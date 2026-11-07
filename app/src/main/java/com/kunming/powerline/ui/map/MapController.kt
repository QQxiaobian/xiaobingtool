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
import org.maplibre.android.style.layers.PropertyValue
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
    /** 用户手势拖动/缩放地图（用于退出跟随模式） */
    var onUserGesture: (() -> Unit)? = null

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
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                onUserGesture?.invoke()
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
        // 方向箭头：预渲染 36 个角度，按 bearing 就近取用
        MapIcons.headingIcons().forEach { (name, bmp) -> st.addImage(name, bmp) }
    }

    /** 建立空的 source + layer 骨架，后续只更新数据（保证渲染顺序稳定） */
    private fun ensureLayerSkeleton(st: Style) {
        val empty = FeatureCollection.fromFeatures(emptyList())
        // ---- 专题图层（导入的点/线/面）----
        // 放在最底层，线路与杆塔始终压在其上，避免巡视点盖住杆塔编号
        st.addSource(geoSource(LayerIds.SRC_THEMATIC, empty))
        st.addLayer(
            FillLayer(LayerIds.LYR_THEMATIC_FILL, LayerIds.SRC_THEMATIC).withFilter(
                Expression.eq(Expression.get("kind"), Expression.literal("polygon"))
            ).withProperties(
                PropertyFactory.fillColor(Expression.get("color")),
                // 透明度由图层自身设置（fillOpacity 属性），支持单层/批量调节
                PropertyFactory.fillOpacity(Expression.get("fillOpacity")),
                PropertyFactory.fillOutlineColor(Expression.get("color"))
            )
        )
        st.addLayer(
            LineLayer(LayerIds.LYR_THEMATIC_LINE, LayerIds.SRC_THEMATIC).withFilter(
                Expression.any(
                    Expression.eq(Expression.get("kind"), Expression.literal("line")),
                    Expression.eq(Expression.get("kind"), Expression.literal("polygon"))
                )
            ).withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(Expression.get("lineWidth")),
                PropertyFactory.lineOpacity(0.92f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        st.addLayer(
            CircleLayer(LayerIds.LYR_THEMATIC_POINT, LayerIds.SRC_THEMATIC).withFilter(
                Expression.eq(Expression.get("kind"), Expression.literal("point"))
            ).withProperties(
                PropertyFactory.circleRadius(5.5f),
                PropertyFactory.circleColor(Expression.get("color")),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(1.8f),
                PropertyFactory.circleOpacity(0.95f)
            )
        )
        // 区域 / 点位名称：只在放大到一定级别后显示；开启碰撞检测，
        // 密集区域自动取舍，避免几千条文字同时绘制导致卡顿
        // 少量要素的图层：不做碰撞剔除，保证一定画出来（区域名称"看不到"的根因之一
        // 就是这里做剔除后连一个都没留下）
        val thematicLabelLayer = SymbolLayer(LayerIds.LYR_THEMATIC_LABEL, LayerIds.SRC_THEMATIC)
        thematicLabelLayer.withFilter(
            Expression.all(
                Expression.any(
                    Expression.eq(Expression.get("labelDense"), Expression.literal(0)),
                    Expression.not(Expression.has("labelDense"))
                ),
                labelGeometryFilter()
            )
        )
        thematicLabelLayer.withProperties(
            textFieldOfLabel(),
            PropertyFactory.textSize(12f),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textColor(Expression.get("color")),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2.2f),
            PropertyFactory.textMaxWidth(9f),
            PropertyFactory.textLineHeight(1.05f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_CENTER),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textOptional(true)
        )
        thematicLabelLayer.setMinZoom(LABEL_MIN_ZOOM)
        st.addLayer(thematicLabelLayer)

        // 密集图层（>120 个要素）：开启碰撞避让，避免几千条文字同时排版
        val thematicDenseLabelLayer = SymbolLayer(LayerIds.LYR_THEMATIC_LABEL_DENSE, LayerIds.SRC_THEMATIC)
        thematicDenseLabelLayer.withFilter(
            Expression.all(
                Expression.eq(Expression.get("labelDense"), Expression.literal(1)),
                labelGeometryFilter()
            )
        )
        thematicDenseLabelLayer.withProperties(
            textFieldOfLabel(),
            PropertyFactory.textSize(11.5f),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textColor(Expression.get("color")),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2.0f),
            PropertyFactory.textMaxWidth(9f),
            PropertyFactory.textLineHeight(1.05f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_CENTER),
            PropertyFactory.textPadding(2f),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textIgnorePlacement(false),
            PropertyFactory.textOptional(true)
        )
        thematicDenseLabelLayer.setMinZoom(LABEL_MIN_ZOOM)
        st.addLayer(thematicDenseLabelLayer)

        // 区域名称（行政区划 / 巡视区域 / 边界线）：独立点图层，低缩放直接可见，
        // 不做碰撞剔除，保证每个区县的名字一打开地图就能看到（不再需要放大到 10 级）。
        // 要素数超过阈值时改走 DENSE 层做碰撞避让，避免几千条文字在低缩放糊满屏。
        st.addSource(geoSource(LayerIds.SRC_AREA_LABEL, empty))
        val areaLabelLayer = SymbolLayer(LayerIds.LYR_AREA_LABEL, LayerIds.SRC_AREA_LABEL)
        areaLabelLayer.withFilter(
            Expression.eq(Expression.get("labelDense"), Expression.literal(0))
        )
        areaLabelLayer.withProperties(
            textFieldOfLabel(),
            PropertyFactory.textSize(12f),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textColor(Expression.get("color")),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2.6f),
            PropertyFactory.textMaxWidth(8.5f),
            PropertyFactory.textLineHeight(1.05f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_CENTER),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textOptional(true)
        )
        areaLabelLayer.setMinZoom(AREA_LABEL_MIN_ZOOM)
        st.addLayer(areaLabelLayer)
        val areaDenseLabelLayer = SymbolLayer(LayerIds.LYR_AREA_LABEL_DENSE, LayerIds.SRC_AREA_LABEL)
        areaDenseLabelLayer.withFilter(
            Expression.eq(Expression.get("labelDense"), Expression.literal(1))
        )
        areaDenseLabelLayer.withProperties(
            textFieldOfLabel(),
            PropertyFactory.textSize(11.5f),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textColor(Expression.get("color")),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2.2f),
            PropertyFactory.textMaxWidth(8.5f),
            PropertyFactory.textLineHeight(1.05f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_CENTER),
            PropertyFactory.textPadding(2f),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textIgnorePlacement(false),
            PropertyFactory.textOptional(true)
        )
        areaDenseLabelLayer.setMinZoom(AREA_LABEL_MIN_ZOOM)
        st.addLayer(areaDenseLabelLayer)
        // 业务点位名称（图标常显，文字只在放大后出现，低缩放完全不排版）
        st.addSource(geoSource(LayerIds.SRC_POINT_LABEL, empty))
        val pointLabelLayer = SymbolLayer(LayerIds.LYR_POINT_LABEL, LayerIds.SRC_POINT_LABEL)
        pointLabelLayer.withProperties(
            PropertyFactory.textField(Expression.coalesce(Expression.get("label"), Expression.literal(""))),
            PropertyFactory.textSize(11f),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textColor(Expression.get("color")),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.8f),
            PropertyFactory.textMaxWidth(8f),
            PropertyFactory.textOffset(arrayOf(0f, 1.15f)),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textIgnorePlacement(false),
            PropertyFactory.textPadding(3f),
            PropertyFactory.textOptional(true)
        )
        pointLabelLayer.setMinZoom(POINT_LABEL_MIN_ZOOM)
        st.addLayer(pointLabelLayer)
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
        // 线路名称（一）：线路中点固定标注 —— 短线也能显示，不依赖线长
        st.addSource(geoSource(LayerIds.SRC_LINE_LABEL_POINT, empty))
        val lineLabelPointLayer = SymbolLayer(
            LayerIds.LYR_LINE_LABEL_POINT, LayerIds.SRC_LINE_LABEL_POINT
        )
        lineLabelPointLayer.withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textSize(12.5f),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textColor(Expression.get("color")),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2.6f),
            PropertyFactory.textMaxWidth(9f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_CENTER),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textIgnorePlacement(false),
            PropertyFactory.textOptional(true),
            PropertyFactory.textPadding(4f)
        )
        lineLabelPointLayer.setMinZoom(LABEL_MIN_ZOOM)
        st.addLayer(lineLabelPointLayer)

        // 线路名称（二）：沿线重复排布，放大后给长线路做视觉引导
        st.addSource(geoSource(LayerIds.SRC_LINE_LABEL, empty))
        val lineLabelAlongLayer = SymbolLayer(LayerIds.LYR_LINE_LABEL, LayerIds.SRC_LINE_LABEL)
        lineLabelAlongLayer.withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textColor(Expression.get("color")),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2.4f),
            PropertyFactory.textMaxWidth(10f),
            PropertyFactory.textLetterSpacing(0.06f),
            PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
            PropertyFactory.symbolSpacing(360f),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textIgnorePlacement(false),
            PropertyFactory.textOptional(true),
            PropertyFactory.textPadding(6f)
        )
        lineLabelAlongLayer.setMinZoom(LABEL_MIN_ZOOM + 2f)
        st.addLayer(lineLabelAlongLayer)
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
        val towerIconLayer = SymbolLayer(LayerIds.LYR_TOWER, LayerIds.SRC_TOWER).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconSize(0.32f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
        // 全省视野（<5 级）几千个杆塔图标没有意义，跳过绘制可显著降低渲染压力
        towerIconLayer.setMinZoom(TOWER_ICON_MIN_ZOOM)
        st.addLayer(towerIconLayer)
        // 杆塔编号单独一层：数据量大，放大到近景才绘制，避免整条线路几千个编号同时排版
        val towerLabelLayer = SymbolLayer(LayerIds.LYR_TOWER_LABEL, LayerIds.SRC_TOWER)
        towerLabelLayer.withProperties(
            PropertyFactory.textField(Expression.get("towerLabel")),
            PropertyFactory.textSize(Expression.get("textSize")),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#000000"),
            PropertyFactory.textHaloWidth(1.6f),
            PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textIgnorePlacement(false),
            PropertyFactory.textPadding(2f)
        )
        towerLabelLayer.setMinZoom(TOWER_LABEL_MIN_ZOOM)
        st.addLayer(towerLabelLayer)
        // 收藏点
        st.addSource(geoSource(LayerIds.SRC_FAVORITE, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_FAVORITE, LayerIds.SRC_FAVORITE).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_FAVORITE),
                PropertyFactory.iconSize(0.42f),
                PropertyFactory.iconAllowOverlap(true),
            )
        )
        // 起飞点
        st.addSource(geoSource(LayerIds.SRC_TAKEOFF, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_TAKEOFF, LayerIds.SRC_TAKEOFF).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_TAKEOFF),
                PropertyFactory.iconSize(0.46f),
                PropertyFactory.iconAllowOverlap(true),
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
            )
        )
        // 视频监控
        st.addSource(geoSource(LayerIds.SRC_CAMERA, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_CAMERA, LayerIds.SRC_CAMERA).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_CAMERA),
                PropertyFactory.iconSize(0.42f),
                PropertyFactory.iconAllowOverlap(true),
            )
        )
        // 护线员
        st.addSource(geoSource(LayerIds.SRC_GUARDIAN, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_GUARDIAN, LayerIds.SRC_GUARDIAN).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_GUARDIAN),
                PropertyFactory.iconSize(0.44f),
                PropertyFactory.iconAllowOverlap(true),
            )
        )
        // 危险点
        st.addSource(geoSource(LayerIds.SRC_DANGER, empty))
        st.addLayer(
            SymbolLayer(LayerIds.LYR_DANGER, LayerIds.SRC_DANGER).withProperties(
                PropertyFactory.iconImage(LayerIds.ICON_DANGER),
                PropertyFactory.iconSize(0.46f),
                PropertyFactory.iconAllowOverlap(true),
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
        // ---- 实时定位（放在最后，保证箭头永远盖在线路/杆塔之上）----
        st.addSource(geoSource(LayerIds.SRC_MY_LOCATION, empty))
        st.addLayer(
            CircleLayer(LayerIds.LYR_MY_LOCATION_ACCURACY, LayerIds.SRC_MY_LOCATION).withProperties(
                PropertyFactory.circleRadius(Expression.get("accuracyRadius")),
                PropertyFactory.circleColor("#1E88E5"),
                PropertyFactory.circleOpacity(0.16f),
                PropertyFactory.circleStrokeColor("#1E88E5"),
                PropertyFactory.circleStrokeOpacity(0.5f),
                PropertyFactory.circleStrokeWidth(1.2f)
            )
        )
        st.addLayer(
            SymbolLayer(LayerIds.LYR_MY_LOCATION_HEADING, LayerIds.SRC_MY_LOCATION).withProperties(
                // 图标本身已按角度预渲染，这里只按要素的 icon 属性取图
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconSize(0.85f),
                // map 对齐：箭头随地图一起旋转，始终指向真实地理方向
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )
    }

    private fun geoSource(id: String, fc: FeatureCollection): GeoJsonSource =
        GeoJsonSource(id, fc)

    /** 名称字段：缺失时用空串兜底，避免 text-field 为 null 时整块文字不渲染 */
    /** 只有点 / 线要素和「面名称承载点」参与名称渲染（面本身不排版文字） */
    private fun labelGeometryFilter(): Expression = Expression.any(
        Expression.eq(Expression.get("kind"), Expression.literal("point")),
        Expression.eq(Expression.get("kind"), Expression.literal("line")),
        Expression.eq(Expression.get("kind"), Expression.literal("label"))
    )

    private fun textFieldOfLabel(): PropertyValue<*> =
        PropertyFactory.textField(
            Expression.coalesce(Expression.get("label"), Expression.literal(""))
        )

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
        (st.getLayer(LayerIds.LYR_TOWER_LABEL) as? SymbolLayer)?.setProperties(
            PropertyFactory.textSize(sp)
        )
    }

    /**
     * 名称显示阈值：低于该缩放级别只画几何，不排版文字。
     *
     * 分级策略（避免一次性排版几千条文字造成卡顿）：
     * - 区域名称（LYR_AREA_LABEL*）固定 [AREA_LABEL_MIN_ZOOM]，**不受本设置约束**，
     *   行政区划一打开地图就能看到名字；
     * - 业务点位名称（LYR_POINT_LABEL）固定 [POINT_LABEL_MIN_ZOOM]，低缩放只画图标；
     * - 线路名 / 专题点名 / 杆塔号仍跟随用户设定阈值，杆塔号再抬高
     *   [TOWER_LABEL_ZOOM_OFFSET] 级。
     */
    fun applyLabelZoom(baseZoom: Float) {
        val st = style ?: return
        val z = baseZoom.coerceIn(MIN_LABEL_ZOOM, MAX_LABEL_ZOOM)
        st.getLayer(LayerIds.LYR_LINE_LABEL_POINT)?.setMinZoom(z)
        st.getLayer(LayerIds.LYR_LINE_LABEL)?.setMinZoom((z + 2f).coerceAtMost(MAX_LABEL_ZOOM))
        val areaZ = (z - 1f).coerceAtLeast(MIN_LABEL_ZOOM)
        st.getLayer(LayerIds.LYR_THEMATIC_LABEL)?.setMinZoom(areaZ)
        st.getLayer(LayerIds.LYR_THEMATIC_LABEL_DENSE)?.setMinZoom(areaZ)
        st.getLayer(LayerIds.LYR_TOWER_LABEL)?.setMinZoom(
            (z + TOWER_LABEL_ZOOM_OFFSET).coerceAtMost(MAX_LABEL_ZOOM)
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

    companion object {
        /** 名称默认显示的缩放级别（与 SettingsStore 默认值保持一致） */
        const val LABEL_MIN_ZOOM = 11f
        /** 杆塔编号相对名称阈值再抬高几级 */
        const val TOWER_LABEL_MIN_ZOOM = 13.5f
        const val TOWER_LABEL_ZOOM_OFFSET = 2.5f
        const val MIN_LABEL_ZOOM = 6f
        const val MAX_LABEL_ZOOM = 17f
        /** 区域名称：低缩放直接显示，不受用户设置的名称阈值约束 */
        const val AREA_LABEL_MIN_ZOOM = 3f
        /** 业务点位名称（收藏点等）：放大到近景才显示，避免低缩放全屏文字卡顿 */
        const val POINT_LABEL_MIN_ZOOM = 12f
        /** 杆塔图标在极低缩放不绘制，全省视野几千个图标会拖慢渲染 */
        const val TOWER_ICON_MIN_ZOOM = 5f
    }
}
