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
        layerOn: (MapLayer) -> Boolean,
        voltageFilter: Set<Int>,
        textSize: Float,
        selectedTowerId: String?
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
            ctrl.setFeatures(LayerIds.SRC_LINE, lineFeatures)
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

            ctrl.setLayerVisible(LayerIds.LYR_LINE, layerOn(MapLayer.OVERHEAD_LINE))
            ctrl.setLayerVisible(LayerIds.LYR_LINE_CASING, layerOn(MapLayer.OVERHEAD_LINE))
            ctrl.setLayerVisible(LayerIds.LYR_TOWER, layerOn(MapLayer.OVERHEAD_LINE))
            ctrl.setLayerVisible(LayerIds.LYR_CABLE, layerOn(MapLayer.CABLE))
            ctrl.setLayerVisible(LayerIds.LYR_FAVORITE, layerOn(MapLayer.FAVORITE))
            ctrl.setLayerVisible(LayerIds.LYR_TAKEOFF, layerOn(MapLayer.TAKEOFF))
            ctrl.setLayerVisible(LayerIds.LYR_PHOTO, layerOn(MapLayer.PHOTO))
            ctrl.setLayerVisible(LayerIds.LYR_CROSSING, layerOn(MapLayer.CROSSING))
            ctrl.setLayerVisible(LayerIds.LYR_CAMERA, layerOn(MapLayer.CAMERA))
            ctrl.setLayerVisible(LayerIds.LYR_GUARDIAN, layerOn(MapLayer.GUARDIAN))
            ctrl.setLayerVisible(LayerIds.LYR_DANGER, layerOn(MapLayer.DANGER))
            ctrl.setLayerVisible(LayerIds.LYR_CUSTOM_FILL, layerOn(MapLayer.CUSTOM))
            ctrl.setLayerVisible(LayerIds.LYR_CUSTOM_LINE, layerOn(MapLayer.CUSTOM))
            ctrl.setLayerVisible(LayerIds.LYR_CUSTOM_TEXT, layerOn(MapLayer.CUSTOM))
        }
    }

    /**
     * 杆塔要素生成：
     * - 同址（坐标差 < 0.0001°）的多回杆塔按回路序做 2~5m 纵向偏移
     * - 识别"多回"：跨线路同址 或 单线路内同址
     */
    fun buildTowerFeatures(
        towers: List<TowerEntity>,
        lines: List<LineEntity>,
        textSize: Float,
        selectedTowerId: String?
    ): List<Feature> {
        val lineMap = lines.associateBy { it.id }
        // 同址分组（仅同线路内 + 跨线路，都视为同塔）
        val groups = mutableListOf<MutableList<TowerEntity>>()
        towers.forEach { t ->
            val g = groups.firstOrNull { grp ->
                val rep = grp.first()
                (rep.lineId == t.lineId || rep.lon != t.lon) &&
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
