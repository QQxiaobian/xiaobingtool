package com.kunming.powerline.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunming.powerline.data.local.entity.CrossingEntity
import com.kunming.powerline.data.local.entity.CustomOverlayEntity
import com.kunming.powerline.data.local.entity.DangerPointEntity
import com.kunming.powerline.data.local.entity.FavoriteEntity
import com.kunming.powerline.data.local.entity.GuardianEntity
import com.kunming.powerline.data.local.entity.PhotoEntity
import com.kunming.powerline.data.local.entity.TakeoffPointEntity
import com.kunming.powerline.data.local.entity.ThematicFeatureEntity
import com.kunming.powerline.data.local.entity.ThematicLayerEntity
import com.kunming.powerline.data.local.entity.TowerEntity
import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.data.local.entity.CableEntity
import com.kunming.powerline.data.local.entity.CameraEntity
import com.kunming.powerline.ui.layer.LayerIds
import com.kunming.powerline.ui.layer.MapLayer
import com.kunming.powerline.util.VoltagePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

/**
 * 图层渲染协调器：把 Room 数据投递到 MapLibre 图层
 */
object LayerRenderer {

    /** 超过该要素数的专题图层，名称启用碰撞避让（防止几千条文字同时排版） */
    const val DENSE_LABEL_THRESHOLD = 120

    /** 面要素超过该数量时不再逐面标注（行政区划一般几十到几百个面，远低于此值） */
    const val POLYGON_LABEL_MAX = 800

    /**
     * 实时定位渲染：精度圈 + 方向箭头 + 蓝点
     *
     * @param bearing 设备朝向（度，0=正北顺时针）。静止时 GPS bearing 为 0，
     *                由调用方传入罗盘方位角，保证停下时箭头仍指向手机朝向。
     */
    fun renderMyLocation(
        ctrl: MapController,
        lon: Double,
        lat: Double,
        accuracy: Float,
        bearing: Float,
        showHeading: Boolean = true
    ) {
        val zoom = ctrl.currentZoom
        val metersPerPx = 156543.03392 * kotlin.math.cos(Math.toRadians(lat)) / Math.pow(2.0, zoom)
        val radiusPx = (accuracy / metersPerPx).coerceIn(8.0, 320.0)
        val f = Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
            addNumberProperty("bearing", bearing.toDouble())
            addNumberProperty("accuracyRadius", radiusPx)
            // 预渲染箭头图标名（按角度就近取整），由符号图层直接用 icon 属性选图
            addStringProperty("icon", MapIcons.headingIconName(bearing))
        }
        ctrl.setFeatures(LayerIds.SRC_MY_LOCATION, listOf(f))
        ctrl.setLayerVisible(LayerIds.LYR_MY_LOCATION_ACCURACY, true)
        ctrl.setLayerVisible(LayerIds.LYR_MY_LOCATION_HEADING, showHeading)
    }

    /** 停止定位后隐藏位置图层 */
    fun clearMyLocation(ctrl: MapController) {
        ctrl.setFeatures(LayerIds.SRC_MY_LOCATION, emptyList())
    }

    suspend fun renderAll(
        ctrl: MapController,
        lines: List<LineEntity>,
        towers: List<TowerEntity>,
        cables: List<CableEntity>,
        favorites: List<FavoriteEntity>,
        takeoffs: List<TakeoffPointEntity>,
        photos: List<PhotoEntity>,
        cameras: List<CameraEntity>,
        guardians: List<GuardianEntity>,
        dangers: List<DangerPointEntity>,
        crossings: List<CrossingEntity>,
        overlays: List<CustomOverlayEntity>,
        thematicLayers: List<ThematicLayerEntity> = emptyList(),
        thematicFeatures: List<ThematicFeatureEntity> = emptyList(),
        layerOn: (MapLayer) -> Boolean,
        voltageFilter: Set<Int>,
        textSize: Float,
        selectedTowerId: String?,
        labelZoom: Float = MapController.LABEL_MIN_ZOOM
    ) = withContext(Dispatchers.Default) {
        // ---- 线路 ----
        val towersByLine = towers.groupBy { it.lineId }
        val lineFeatures = lines.filter { it.voltageLevel in voltageFilter }.mapNotNull { l ->
            val ts = towersByLine[l.id].orEmpty().sortedBy { it.seq }
            if (ts.size < 2) return@mapNotNull null
            val coords = ts.map { Point.fromLngLat(it.lon, it.lat) }
            Feature.fromGeometry(org.maplibre.geojson.LineString.fromLngLats(coords)).apply {
                addStringProperty("fid", l.id)
                addStringProperty("name", l.name)
                addNumberProperty("voltage", l.voltageLevel.toDouble())
                addStringProperty("color", VoltagePalette.hexColorOf(l.voltageLevel))
                addNumberProperty("width", VoltagePalette.widthOf(l.voltageLevel).toDouble())
            }
        }
        // ---- 线路名称标注 ----
        // 沿线排布（长线路放大后重复出现）+ 中点固定（短线也一定能看到名称）
        val visibleLines = lines.filter { it.voltageLevel in voltageFilter }
        val lineLabelFeatures = visibleLines.mapNotNull { l ->
            val ts = towersByLine[l.id].orEmpty().sortedBy { it.seq }
            if (ts.size < 2) return@mapNotNull null
            val coords = ts.map { Point.fromLngLat(it.lon, it.lat) }
            Feature.fromGeometry(org.maplibre.geojson.LineString.fromLngLats(coords)).apply {
                addStringProperty("fid", l.id)
                addStringProperty("label", l.name)
                addStringProperty("color", VoltagePalette.hexColorOf(l.voltageLevel))
            }
        }
        val lineLabelPointFeatures = visibleLines.mapNotNull { l ->
            val ts = towersByLine[l.id].orEmpty().sortedBy { it.seq }
            if (ts.size < 2) return@mapNotNull null
            // 取线路几何的中间节点作为锚点，避免按首尾直线求中点落到线外
            val mid = ts[ts.size / 2]
            Feature.fromGeometry(Point.fromLngLat(mid.lon, mid.lat)).apply {
                addStringProperty("fid", l.id)
                addStringProperty("label", l.name)
                addStringProperty("color", VoltagePalette.hexColorOf(l.voltageLevel))
            }
        }
        // ---- 电缆 ----
        val cableFeatures = cables.filter { it.voltageLevel in voltageFilter }.mapNotNull { c ->
            try {
                val f = Feature.fromJson("""{"type":"Feature","properties":{},"geometry":${c.geometryGeojson}}""")
                Feature.fromGeometry(f.geometry()).apply {
                    addStringProperty("fid", c.id)
                    addStringProperty("name", c.name)
                    addStringProperty("color", VoltagePalette.hexColorOf(c.voltageLevel))
                }
            } catch (e: Exception) {
                null
            }
        }
        // ---- 杆塔（含同塔多回视觉偏移）----
        val towerFeatures = buildTowerFeatures(towers, lines, textSize, selectedTowerId)

        // ---- 收藏点 ----
        val favFeatures = favorites.map { f ->
            Feature.fromGeometry(Point.fromLngLat(f.lon, f.lat)).apply {
                addStringProperty("fid", f.id)
                addStringProperty("name", f.name)
                addStringProperty("category", f.category)
            }
        }
        // ---- 起飞点 ----
        val takeoffFeatures = takeoffs.map { t ->
            Feature.fromGeometry(Point.fromLngLat(t.lon, t.lat)).apply {
                addStringProperty("fid", t.id)
                addStringProperty("name", t.name)
            }
        }
        // ---- 影像 ----
        val photoFeatures = photos.filter { it.lon != null && it.lat != null }.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.lon!!, p.lat!!)).apply {
                addStringProperty("fid", p.id)
                addStringProperty("towerId", p.towerId ?: "")
            }
        }
        // ---- 视频 ----
        val cameraFeatures = cameras.map { c ->
            Feature.fromGeometry(Point.fromLngLat(c.lon, c.lat)).apply {
                addStringProperty("fid", c.id)
                addStringProperty("name", c.name)
                addStringProperty("rtsp", c.rtspUrl)
            }
        }
        // ---- 护线员 ----
        val guardianFeatures = guardians.filter { it.lastLon != null }.map { g ->
            Feature.fromGeometry(Point.fromLngLat(g.lastLon!!, g.lastLat!!)).apply {
                addStringProperty("fid", g.id)
                addStringProperty("name", g.name)
            }
        }
        // ---- 危险点 ----
        val dangerFeatures = dangers.map { d ->
            Feature.fromGeometry(Point.fromLngLat(d.lon, d.lat)).apply {
                addStringProperty("fid", d.id)
                addStringProperty("type", d.type)
                addStringProperty("level", d.level)
            }
        }
        // ---- 交叉跨越 ----
        val crossingFeatures = crossings.filter { it.lon != null && it.lat != null }.map { x ->
            Feature.fromGeometry(Point.fromLngLat(x.lon!!, x.lat!!)).apply {
                addStringProperty("fid", x.id)
                addStringProperty("crossObject", x.crossObject ?: x.crossType)
            }
        }
        // ---- 专题图层（点 / 线 / 面）----
        val visibleLayers = thematicLayers.filter { it.visible }.associateBy { it.id }
        // 每个图层实际的要素数（layer.featureCount 是导入时写的，可能因移动要素而不准）
        val featureCountByLayer = thematicFeatures.groupingBy { it.layerId }.eachCount()
        val thematicFeatureList = thematicFeatures.mapNotNull { f ->
            val layer = visibleLayers[f.layerId] ?: return@mapNotNull null
            try {
                val src = Feature.fromJson(
                    """{"type":"Feature","properties":{},"geometry":${f.geometryGeojson}}"""
                )
                // 名称：要素名优先，面要素无名字时退回图层名；备注取属性里的 description / 备注
                // 注意：面要素的名称**不**在本要素上渲染（MapLibre 在 Polygon 上放置文字的
                // 行为不稳定，很多机型直接不画），改由下面的 polygonLabelFeatures 用
                // 独立点要素承载，保证行政区划每个区县的名字都能显示。
                val label = if (layer.labelVisible && f.kind != "polygon") {
                    ThematicLabels.compose(
                        name = f.name,
                        propertiesJson = f.propertiesJson,
                        layerName = layer.name,
                        layerRemark = layer.remark,
                        kind = f.kind,
                        featureCount = layer.featureCount
                    )
                } else ""
                Feature.fromGeometry(src.geometry()).apply {
                    addStringProperty("fid", f.id)
                    addStringProperty("layerId", f.layerId)
                    addStringProperty("kind", f.kind)
                    addStringProperty("label", label)
                    addStringProperty("layerName", layer.name)
                    addStringProperty("remark", layer.remark ?: "")
                    addStringProperty("color", "#" + Integer.toHexString(layer.color).takeLast(6))
                    addNumberProperty("fillOpacity", layer.fillOpacity.toDouble())
                    addNumberProperty("lineWidth", layer.lineWidth.toDouble())
                    // 1 = 密集图层（名称开启碰撞避让），0 = 稀疏图层（名称必显）
                    addNumberProperty(
                        "labelDense",
                        if (layer.featureCount > DENSE_LABEL_THRESHOLD) 1.0 else 0.0
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
        // ---- 面要素名称：用独立点要素承载 ----
        // 行政区划、巡视区域这类数据每个面都有自己的名字，必须逐面显示。
        // 面本身的 symbol 在部分机型上不渲染，这里在其质心处补一个纯文字点。
        val polygonLabelFeatures = thematicFeatures.mapNotNull { f ->
            val layer = visibleLayers[f.layerId] ?: return@mapNotNull null
            if (!layer.labelVisible || f.kind != "polygon") return@mapNotNull null
            if ((featureCountByLayer[f.layerId] ?: 0) > POLYGON_LABEL_MAX) return@mapNotNull null
            val lon = f.lon ?: return@mapNotNull null
            val lat = f.lat ?: return@mapNotNull null
            val text = ThematicLabels.compose(
                name = f.name,
                propertiesJson = f.propertiesJson,
                layerName = layer.name,
                layerRemark = layer.remark,
                kind = f.kind,
                featureCount = featureCountByLayer[f.layerId] ?: 0
            )
            if (text.isBlank()) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                addStringProperty("fid", f.id)
                addStringProperty("layerId", f.layerId)
                addStringProperty("kind", "label")
                addStringProperty("label", text)
                addStringProperty("layerName", layer.name)
                addStringProperty("remark", layer.remark ?: "")
                addStringProperty("color", "#" + Integer.toHexString(layer.color).takeLast(6))
                addNumberProperty("fillOpacity", layer.fillOpacity.toDouble())
                addNumberProperty("lineWidth", layer.lineWidth.toDouble())
                addNumberProperty("labelDense", 0.0)
            }
        }

        // ---- 图层级区域标注 ----
        // 面图层里的要素常常整层都没有名字（CAD 转出来的区域尤其如此），
        // 这时不逐个面去贴图层名（会糊满屏），而是给整个图层补一条"图层名标注"，
        // 位置取第一个有坐标的面要素质心。
        val layerLabelFeatures = visibleLayers.values.mapNotNull { layer ->
            if (!layer.labelVisible || !layer.hasPolygon) return@mapNotNull null
            val own = thematicFeatures.filter { it.layerId == layer.id }
            if (own.isEmpty()) return@mapNotNull null
            if (own.size > POLYGON_LABEL_MAX) return@mapNotNull null
            // 只要有一个面能显示自己的名字（或退回图层名），就不再补整层标注
            if (polygonLabelFeatures.any { it.getStringProperty("layerId") == layer.id }) {
                return@mapNotNull null
            }
            val anchor = own.firstOrNull { it.lon != null && it.lat != null } ?: return@mapNotNull null
            val text = listOfNotNull(
                layer.name.takeIf { it.isNotBlank() },
                layer.remark?.takeIf { it.isNotBlank() }
            ).joinToString("\n")
            Feature.fromGeometry(Point.fromLngLat(anchor.lon!!, anchor.lat!!)).apply {
                addStringProperty("fid", "layer-label-" + layer.id)
                addStringProperty("layerId", layer.id)
                addStringProperty("kind", "label")
                addStringProperty("label", text)
                addStringProperty("color", "#" + Integer.toHexString(layer.color).takeLast(6))
                addNumberProperty("fillOpacity", layer.fillOpacity.toDouble())
                addNumberProperty("lineWidth", layer.lineWidth.toDouble())
                addNumberProperty("labelDense", 0.0)
            }
        }

        // ---- 自定义（绘制结果）----
        val overlayFeatures = overlays.mapNotNull { o ->
            try {
                val f = Feature.fromJson("""{"type":"Feature","properties":{},"geometry":${o.geometryGeojson}}""")
                val feat = Feature.fromGeometry(f.geometry()).apply {
                    addStringProperty("fid", o.id)
                    addStringProperty("name", o.name)
                    val isFill = o.geometryGeojson.contains("Polygon")
                    addStringProperty("kind", if (o.type == "text") "text" else if (isFill) "fill" else "line")
                    addStringProperty("color", "#" + Integer.toHexString(o.color).takeLast(6))
                    addNumberProperty("textSize", o.textSize.toDouble())
                }
                feat
            } catch (e: Exception) {
                null
            }
        }

        withContext(Dispatchers.Main) {
            // 名称显示阈值（低于该级别不排版文字，减少密集数据卡顿）
            ctrl.applyLabelZoom(labelZoom)

            ctrl.setFeatures(LayerIds.SRC_LINE, lineFeatures)
            ctrl.setFeatures(LayerIds.SRC_LINE_LABEL, lineLabelFeatures)
            ctrl.setFeatures(LayerIds.SRC_LINE_LABEL_POINT, lineLabelPointFeatures)
            ctrl.setFeatures(LayerIds.SRC_CABLE, cableFeatures)
            ctrl.setFeatures(LayerIds.SRC_TOWER, towerFeatures)
            ctrl.setFeatures(LayerIds.SRC_FAVORITE, favFeatures)
            ctrl.setFeatures(LayerIds.SRC_TAKEOFF, takeoffFeatures)
            ctrl.setFeatures(LayerIds.SRC_PHOTO, photoFeatures)
            ctrl.setFeatures(LayerIds.SRC_CAMERA, cameraFeatures)
            ctrl.setFeatures(LayerIds.SRC_GUARDIAN, guardianFeatures)
            ctrl.setFeatures(LayerIds.SRC_DANGER, dangerFeatures)
            ctrl.setFeatures(LayerIds.SRC_CROSSING, crossingFeatures)
            ctrl.setFeatures(LayerIds.SRC_CUSTOM, overlayFeatures)
            ctrl.setFeatures(LayerIds.SRC_THEMATIC, thematicFeatureList + layerLabelFeatures)
            ctrl.setFeatures(LayerIds.SRC_AREA_LABEL, polygonLabelFeatures)

            ctrl.setLayerVisible(LayerIds.LYR_LINE, layerOn(MapLayer.OVERHEAD_LINE))
            ctrl.setLayerVisible(LayerIds.LYR_LINE_LABEL, layerOn(MapLayer.OVERHEAD_LINE))
            ctrl.setLayerVisible(LayerIds.LYR_LINE_LABEL_POINT, layerOn(MapLayer.OVERHEAD_LINE))
            ctrl.setLayerVisible(LayerIds.LYR_LINE_CASING, layerOn(MapLayer.OVERHEAD_LINE))
            ctrl.setLayerVisible(LayerIds.LYR_TOWER, layerOn(MapLayer.OVERHEAD_LINE))
            ctrl.setLayerVisible(LayerIds.LYR_TOWER_LABEL, layerOn(MapLayer.OVERHEAD_LINE))
            ctrl.setLayerVisible(LayerIds.LYR_CABLE, layerOn(MapLayer.CABLE))
            ctrl.setLayerVisible(LayerIds.LYR_FAVORITE, layerOn(MapLayer.FAVORITE))
            ctrl.setLayerVisible(LayerIds.LYR_TAKEOFF, layerOn(MapLayer.TAKEOFF))
            ctrl.setLayerVisible(LayerIds.LYR_PHOTO, layerOn(MapLayer.PHOTO))
            ctrl.setLayerVisible(LayerIds.LYR_CROSSING, layerOn(MapLayer.CROSSING))
            ctrl.setLayerVisible(LayerIds.LYR_CAMERA, layerOn(MapLayer.CAMERA))
            ctrl.setLayerVisible(LayerIds.LYR_GUARDIAN, layerOn(MapLayer.GUARDIAN))
            ctrl.setLayerVisible(LayerIds.LYR_DANGER, layerOn(MapLayer.DANGER))
            ctrl.setLayerVisible(LayerIds.LYR_THEMATIC_FILL, layerOn(MapLayer.THEMATIC))
            ctrl.setLayerVisible(LayerIds.LYR_THEMATIC_LINE, layerOn(MapLayer.THEMATIC))
            ctrl.setLayerVisible(LayerIds.LYR_THEMATIC_POINT, layerOn(MapLayer.THEMATIC))
            ctrl.setLayerVisible(LayerIds.LYR_THEMATIC_LABEL, layerOn(MapLayer.THEMATIC))
            ctrl.setLayerVisible(LayerIds.LYR_THEMATIC_LABEL_DENSE, layerOn(MapLayer.THEMATIC))
            ctrl.setLayerVisible(LayerIds.LYR_AREA_LABEL, layerOn(MapLayer.THEMATIC))
            ctrl.setLayerVisible(LayerIds.LYR_CUSTOM_FILL, layerOn(MapLayer.CUSTOM))
            ctrl.setLayerVisible(LayerIds.LYR_CUSTOM_LINE, layerOn(MapLayer.CUSTOM))
            ctrl.setLayerVisible(LayerIds.LYR_CUSTOM_TEXT, layerOn(MapLayer.CUSTOM))
        }
    }

    /**
     * 杆塔要素生成：
     * - 同塔多回：**仅跨线路**同址才算同塔（同一线路内的杆塔即使是同址也不能合并，
     *   否则重复坐标的杆塔会被误判成多回偏移、甚至叠在一起看起来"只有一个点"）
     * - 同址（坐标差 < 0.0001°）的多回杆塔按回路序做 2~5m 纵向偏移
     */
    fun buildTowerFeatures(
        towers: List<TowerEntity>,
        lines: List<LineEntity>,
        textSize: Float,
        selectedTowerId: String?
    ): List<Feature> {
        val lineMap = lines.associateBy { it.id }
        // 同址分组：只在不同线路之间识别（跨线路同塔多回）
        val groups = mutableListOf<MutableList<TowerEntity>>()
        towers.forEach { t ->
            val g = groups.firstOrNull { grp ->
                val rep = grp.first()
                rep.lineId != t.lineId &&
                    com.kunming.powerline.util.GeoUtils.nearlySameLocation(rep.lon, rep.lat, t.lon, t.lat, 0.0001)
            }
            if (g != null) g.add(t) else groups.add(mutableListOf(t))
        }
        val idxMap = HashMap<String, Int>()
        groups.forEach { g ->
            if (g.size > 1) g.sortedBy { it.lineId + it.towerNumber }.forEachIndexed { i, t -> idxMap[t.id] = i }
        }
        val roman = listOf("Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ")

        return towers.map { t ->
            var dx = 0.0
            var dy = 0.0
            val i = idxMap[t.id]
            if (i != null) {
                // 2m 起，每增加一回 +1m，最大 5m（垂直方向错开）
                dy = (2.0 + i).coerceAtMost(5.0)
            }
            if (t.visualOffsetLon != 0.0 || t.visualOffsetLat != 0.0) {
                dx += t.visualOffsetLon
                dy += t.visualOffsetLat
            }
            val (dLon, dLat) = com.kunming.powerline.util.GeoUtils.offsetMetersToDegrees(t.lon, t.lat, dx, dy)
            val line = lineMap[t.lineId]
            val circuitLabel = when {
                i != null -> roman.getOrElse(i) { "${i + 1}" } + "回"
                t.circuitCount > 1 -> roman.getOrElse(t.circuitNo - 1) { "${t.circuitNo}" } + "回"
                else -> ""
            }
            Feature.fromGeometry(Point.fromLngLat(t.lon + dLon, t.lat + dLat)).apply {
                addStringProperty("fid", t.id)
                addStringProperty("lineId", t.lineId)
                addStringProperty("lineName", line?.name ?: "")
                addNumberProperty("voltage", (line?.voltageLevel ?: 110).toDouble())
                addStringProperty("towerLabel", t.towerNumber)
                addStringProperty("circuitLabel", circuitLabel)
                addNumberProperty("textSize", textSize.toDouble())
                addStringProperty(
                    "icon",
                    if (selectedTowerId == t.id) LayerIds.ICON_TOWER_SELECTED else LayerIds.ICON_TOWER
                )
            }
        }
    }
}
