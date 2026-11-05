package com.kunming.powerline.ui.map

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kunming.powerline.ui.layer.LayerIds
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * 地图控制器：封装全部图层 / 相机 / 截图操作，Compose 侧只与它交互
 */
class MapController {

    var map: MapLibreMap? = null
        private set
    var style: Style? = null
        private set

    var ready by mutableStateOf(false)
        private set

    var currentBasemap by mutableStateOf(BasemapType.YN_SATELLITE)
        private set

    var currentBearing by mutableStateOf(0.0)
        private set
    var currentZoom by mutableStateOf(12.0)
        private set
    var centerLatLng by mutableStateOf<LatLng?>(null)
        private set

    /** 底图切换完回调（用于后续重绘图层） */
    var onStyleReady: ((Style) -> Unit)? = null
    var onCameraChanged: (() -> Unit)? = null

    fun attach(map: MapLibreMap) {
        this.map = map
        map.addOnCameraIdleListener {
            map.cameraPosition?.let { cp ->
                currentBearing = cp.bearing.toDouble()
                currentZoom = cp.zoom
                centerLatLng = cp.target
                onCameraChanged?.invoke()
            }
        }
        map.addOnCameraMoveListener {
            map.cameraPosition?.let { cp ->
                currentBearing = cp.bearing.toDouble()
                currentZoom = cp.zoom
                centerLatLng = cp.target
            }
        }
    }

    // ------------------------------------------------------------------
    // Style
    // ------------------------------------------------------------------
    fun loadBasemap(type: BasemapType, keepCamera: Boolean = true) {
        val m = map ?: return
        val cam = if (keepCamera) m.cameraPosition else null
        currentBasemap = type
        ready = false
        val json = TileSourceFactory.buildStyleJson(type)
        m.setStyle(Style.Builder().fromJson(json)) { st ->
            style = st
            registerIcons(st)
            ensureLayerSkeleton(st)
            if (cam != null) {
                m.cameraPosition = CameraPosition.Builder()
                    .target(cam.target)
                    .zoom(cam.zoom)
                    .bearing(cam.bearing)
                    .tilt(cam.tilt)
                    .build()
            }
            ready = true
            onStyleReady?.invoke(st)
        }
    }

    /** 注册所有矢量图标 */
    private fun registerIcons(st: Style) {
        st.addImage(LayerIds.ICON_TOWER, MapIcons.towerIcon(0xFFFFFFFF.toInt()))
        st.addImage(LayerIds.ICON_TOWER_SELECTED, MapIcons.towerIcon(0xFFB71C1C.toInt(), selected = true))
        st.addImage(LayerIds.ICON_FAVORITE, MapIcons.starIcon(0xFFFFB300.toInt()))
        st.addImage(LayerIds.ICON_TAKEOFF, MapIcons.droneIcon(0xFF00ACC1.toInt()))
        st.addImage(LayerIds.ICON_PHOTO, MapIcons.cameraIcon(0xFF8E24AA.toInt()))
        st.addImage(LayerIds.ICON_CROSSING, MapIcons.crossIcon(0xFFFB8C00.toInt()))
        st.addImage(LayerIds.ICON_CAMERA, MapIcons.cameraIcon(0xFF00897B.toInt()))
        st.addImage(LayerIds.ICON_GUARDIAN, MapIcons.personIcon(0xFF2E7D32.toInt()))
        st.addImage(LayerIds.ICON_DANGER, MapIcons.triangleIcon(0xFFE53935.toInt()))
        st.addImage(LayerIds.ICON_SEARCH, MapIcons.ringIcon(0xFFFFD600.toInt()))
    }

    /** 建立空的 source + layer 骨架，后续只更新数据（保证渲染顺序稳定） */
    private fun ensureLayerSkeleton(st: Style) {
        val empty = FeatureCollection.fromFeatures(emptyList())
        // 线路
        st.addSource(geoSource(LayerIds.SRC_LINE, empty))
        st.addLayer(
            LineLayer(LayerIds.LYR_LINE_CASING, LayerIds.SRC_LINE).withProperties(
                PropertyFactory.lineColor("#000000"),
                PropertyFactory.lineWidth(Expression.product(Expression.get("width"), Expression.literal(1.8f))),
                PropertyFactory.lineOpacity(0.35f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        st.addLayer(
            LineLayer(LayerIds.LYR_LINE, LayerIds.SRC_LINE).withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(Expression.get("width")),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            )
        )
        st.addLayer(
            LineLayer(LayerIds.LYR_LINE_HIGHLIGHT, LayerIds.SRC_LINE).withProperties(
                PropertyFactory.lineColor("#FFEB3B"),
                PropertyFactory.lineWidth(Expression.sum(Expression.get("width"), Expression.literal(4f))),
                PropertyFactory.lineOpacity(0f)
            )
        )
        // 电缆（虚线）
        st.addSource(geoSource(LayerIds.SRC_CABLE, empty))
        st.addLayer(
            LineLayer(LayerIds.LYR_CABLE, LayerIds.SRC_CABLE).withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineDasharray(arrayOf(2.0f, 1.6f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            )
        )
        // 杆塔
        st.addSource(geoSource(LayerIds.SRC_TOWER, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_TOWER, LayerIds.SRC_TOWER).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconSize(0.32f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.textField(Expression.get("towerLabel")),
                PropertyFactory.textSize(Expression.get("textSize")),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(1.6f),
                PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            )
        )
        // 收藏点
        st.addSource(geoSource(LayerIds.SRC_FAVORITE, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_FAVORITE, LayerIds.SRC_FAVORITE).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_FAVORITE),
                PropertyFactory.iconSize(0.42f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textSize(11f),
                PropertyFactory.textColor("#FFE082"),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(1.8f),
                PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
                PropertyFactory.textAllowOverlap(true)
            )
        )
        // 起飞点
        st.addSource(geoSource(LayerIds.SRC_TAKEOFF, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_TAKEOFF, LayerIds.SRC_TAKEOFF).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_TAKEOFF),
                PropertyFactory.iconSize(0.46f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textSize(11f),
                PropertyFactory.textColor("#B2EBF2"),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(1.8f),
                PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
                PropertyFactory.textAllowOverlap(true)
            )
        )
        // 影像
        st.addSource(geoSource(LayerIds.SRC_PHOTO, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_PHOTO, LayerIds.SRC_PHOTO).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_PHOTO),
                PropertyFactory.iconSize(0.40f),
                PropertyFactory.iconAllowOverlap(true)
            )
        )
        // 交叉跨越
        st.addSource(geoSource(LayerIds.SRC_CROSSING, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_CROSSING, LayerIds.SRC_CROSSING).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_CROSSING),
                PropertyFactory.iconSize(0.44f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.textField(Expression.get("crossObject")),
                PropertyFactory.textSize(10f),
                PropertyFactory.textColor("#FFE0B2"),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(1.6f),
                PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
                PropertyFactory.textAllowOverlap(true)
            )
        )
        // 视频监控
        st.addSource(geoSource(LayerIds.SRC_CAMERA, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_CAMERA, LayerIds.SRC_CAMERA).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_CAMERA),
                PropertyFactory.iconSize(0.42f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textSize(10f),
                PropertyFactory.textColor("#80CBC4"),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(1.6f),
                PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
                PropertyFactory.textAllowOverlap(true)
            )
        )
        // 护线员
        st.addSource(geoSource(LayerIds.SRC_GUARDIAN, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_GUARDIAN, LayerIds.SRC_GUARDIAN).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_GUARDIAN),
                PropertyFactory.iconSize(0.44f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textSize(11f),
                PropertyFactory.textColor("#C8E6C9"),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(1.8f),
                PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
                PropertyFactory.textAllowOverlap(true)
            )
        )
        // 危险点
        st.addSource(geoSource(LayerIds.SRC_DANGER, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_DANGER, LayerIds.SRC_DANGER).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_DANGER),
                PropertyFactory.iconSize(0.46f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.textField(Expression.get("type")),
                PropertyFactory.textSize(10f),
                PropertyFactory.textColor("#FFCDD2"),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(1.6f),
                PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
                PropertyFactory.textAllowOverlap(true)
            )
        )
        // 自定义图层
        st.addSource(geoSource(LayerIds.SRC_CUSTOM, empty))
        st.addLayer(
            FillLayer(LayerIds.LYR_CUSTOM_FILL, LayerIds.SRC_CUSTOM).withFilter(
                Expression.eq(Expression.get("kind"), Expression.literal("fill"))
            ).withProperties(
                PropertyFactory.fillColor(Expression.get("color")),
                PropertyFactory.fillOpacity(0.28f),
                PropertyFactory.fillOutlineColor(Expression.get("color"))
            )
        )
        st.addLayer(
            LineLayer(LayerIds.LYR_CUSTOM_LINE, LayerIds.SRC_CUSTOM).withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineDasharray(arrayOf(3f, 2f))
            )
        )
        st.addLayer(
            SymbolLayer(LayerIds.LYR_CUSTOM_TEXT, LayerIds.SRC_CUSTOM).withFilter(
                Expression.eq(Expression.get("kind"), Expression.literal("text"))
            ).withProperties(
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textSize(Expression.get("textSize")),
                PropertyFactory.textColor(Expression.get("color")),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(2f),
                PropertyFactory.textAllowOverlap(true)
            )
        )
        // 预览（导入前半透明渲染）
        st.addSource(geoSource(LayerIds.SRC_PREVIEW, empty))
        st.addLayer(
            FillLayer(LayerIds.LYR_PREVIEW_FILL, LayerIds.SRC_PREVIEW).withProperties(
                PropertyFactory.fillColor("#FFEB3B"),
                PropertyFactory.fillOpacity(0.22f)
            )
        )
        st.addLayer(
            LineLayer(LayerIds.LYR_PREVIEW_LINE, LayerIds.SRC_PREVIEW).withProperties(
                PropertyFactory.lineColor("#FFEB3B"),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineOpacity(0.85f)
            )
        )
        // 导航直线 / 测距
        st.addSource(geoSource(LayerIds.SRC_ROUTE, empty))
        st.addLayer(
            LineLayer(LayerIds.LYR_ROUTE, LayerIds.SRC_ROUTE).withProperties(
                PropertyFactory.lineColor("#00E5FF"),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineDasharray(arrayOf(1.5f, 1.2f))
            )
        )
        // 搜索结果高亮
        st.addSource(geoSource(LayerIds.SRC_SEARCH, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_SEARCH, LayerIds.SRC_SEARCH).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_SEARCH),
                PropertyFactory.iconSize(0.7f),
                PropertyFactory.iconAllowOverlap(true)
            )
        )
        // 绘制
        st.addSource(geoSource(LayerIds.SRC_DRAW, empty))
        st.addLayer(
            FillLayer(LayerIds.LYR_DRAW_FILL, LayerIds.SRC_DRAW).withFilter(
                Expression.eq(Expression.get("kind"), Expression.literal("fill"))
            ).withProperties(
                PropertyFactory.fillColor("#B71C1C"),
                PropertyFactory.fillOpacity(0.25f)
            )
        )
        st.addLayer(
            LineLayer(LayerIds.LYR_DRAW_LINE, LayerIds.SRC_DRAW).withProperties(
                PropertyFactory.lineColor("#FF5252"),
                PropertyFactory.lineWidth(3f)
            )
        )
        st.addLayer(
            CircleLayer(LayerIds.LYR_DRAW_VERTEX, LayerIds.SRC_DRAW).withFilter(
                Expression.eq(Expression.get("kind"), Expression.literal("vertex"))
            ).withProperties(
                PropertyFactory.circleRadius(5.5f),
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleStrokeColor("#B71C1C"),
                PropertyFactory.circleStrokeWidth(2.2f)
            )
        )
    }

    private fun geoSource(id: String, fc: FeatureCollection): GeoJsonSource =
        GeoJsonSource(id, fc)

    // ------------------------------------------------------------------
    // 数据更新
    // ------------------------------------------------------------------
    fun setFeatures(sourceId: String, features: List<Feature>) {
        val st = style ?: return
        (st.getSourceAs<GeoJsonSource>(sourceId))?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setGeoJsonString(sourceId: String, json: String) {
        val st = style ?: return
        val src = st.getSourceAs<GeoJsonSource>(sourceId) ?: return
        try {
            src.setGeoJson(json)
        } catch (e: Exception) {
            // 忽略非法 GeoJSON
        }
    }

    fun setLayerVisible(layerId: String, visible: Boolean) {
        val st = style ?: return
        st.getLayer(layerId)?.setProperties(
            PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
        )
    }

    fun setLayerOpacity(layerId: String, opacity: Float) {
        val st = style ?: return
        when (val l = st.getLayer(layerId)) {
            is LineLayer -> l.setProperties(PropertyFactory.lineOpacity(opacity))
            is FillLayer -> l.setProperties(PropertyFactory.fillOpacity(opacity))
            else -> {}
        }
    }

    fun setTowerTextSize(sp: Float) {
        val st = style ?: return
        (st.getLayer(LayerIds.LYR_TOWER) as? SymbolLayer)?.setProperties(
            PropertyFactory.textSize(sp)
        )
    }

    // ------------------------------------------------------------------
    // 相机
    // ------------------------------------------------------------------
    fun moveTo(lon: Double, lat: Double, zoom: Double? = null, animate: Boolean = true) {
        val m = map ?: return
        val upd = CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom ?: m.cameraPosition?.zoom ?: 14.0)
        if (animate) m.animateCamera(upd, 900) else m.moveCamera(upd)
    }

    fun easeTo(lon: Double, lat: Double, zoom: Double? = null, bearing: Double? = null, durationMs: Int = 900) {
        val m = map ?: return
        val cp = m.cameraPosition ?: return
        val pos = CameraPosition.Builder(cp)
            .target(LatLng(lat, lon))
            .zoom(zoom ?: cp.zoom)
            .bearing(bearing ?: cp.bearing)
            .build()
        m.easeCamera(CameraUpdateFactory.newCameraPosition(pos), durationMs)
    }

    fun zoomBy(delta: Double) {
        val m = map ?: return
        m.animateCamera(
            if (delta >= 0) CameraUpdateFactory.zoomIn() else CameraUpdateFactory.zoomOut()
        )
    }

    fun zoomIn() { map?.animateCamera(CameraUpdateFactory.zoomIn()) }

    fun zoomOut() { map?.animateCamera(CameraUpdateFactory.zoomOut()) }

    fun resetNorth() {
        val m = map ?: return
        val cp = m.cameraPosition ?: return
        m.easeCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(cp).bearing(0.0).tilt(0.0).build()
            ), 400
        )
    }

    fun rotateTo(deg: Double) {
        val m = map ?: return
        val cp = m.cameraPosition ?: return
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(cp).bearing(deg).build()))
    }

    fun fitBounds(points: List<Point>, padding: Int = 120) {
        if (points.isEmpty()) return
        val m = map ?: return
        val lls = points.map { LatLng(it.latitude(), it.longitude()) }
        if (lls.size == 1) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(lls[0], 16.0), 800)
            return
        }
        try {
            val b = LatLngBounds.Builder().includes(lls).build()
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(b, padding), 900)
        } catch (e: Exception) {
            moveTo(points[0].longitude(), points[0].latitude(), 14.0)
        }
    }

    fun snapshot(cb: (Bitmap?) -> Unit) {
        map?.snapshot { bmp -> cb(bmp) }
    }

    /** 屏幕坐标查询要素 */
    fun queryFeatures(px: Float, py: Float, layerIds: Array<String>): List<Feature> {
        val m = map ?: return emptyList()
        return try {
            m.queryRenderedFeatures(android.graphics.PointF(px, py), *layerIds)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun pixelToLatLng(px: Float, py: Float): LatLng? =
        map?.projection?.fromScreenLocation(android.graphics.PointF(px, py))

    fun latLngToPixel(latLng: LatLng): android.graphics.PointF? =
        map?.projection?.toScreenLocation(latLng)

    fun cameraInfo(): String {
        val c = centerLatLng ?: return "—"
        return String.format("经度 %.5f，纬度 %.5f，缩放 %.1f", c.longitude, c.latitude, currentZoom)
    }

    fun destroy() {
        style = null
        map = null
        ready = false
    }
}
