package com.kunming.powerline.data.repository

import com.kunming.powerline.data.local.AppDatabase
import com.kunming.powerline.data.local.entity.CableEntity
import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.data.local.entity.TowerEntity
import com.kunming.powerline.util.GeoUtils
import com.kunming.powerline.util.VoltagePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 线路 / 杆塔 / 电缆 仓库，负责台账读写与地图 GeoJSON 生成
 */
@Singleton
class LineRepository @Inject constructor(
    private val db: AppDatabase
) {
    private val lineDao = db.lineDao()
    private val towerDao = db.towerDao()
    private val cableDao = db.cableDao()

    val lines: Flow<List<LineEntity>> = lineDao.observeAll()
    val towers: Flow<List<TowerEntity>> = towerDao.observeAll()
    val cables: Flow<List<CableEntity>> = cableDao.observeAll()

    /** 左下角实时统计：N 条线路 / M 条电缆 */
    val stat: Flow<Pair<Int, Int>> = combine(lineDao.observeCount(), cableDao.observeCount()) { l, c -> l to c }

    suspend fun lineList(): List<LineEntity> = lineDao.getAll()
    suspend fun towerList(): List<TowerEntity> = towerDao.getAll()
    suspend fun towerOfLine(lineId: String): List<TowerEntity> = towerDao.getByLine(lineId)
    suspend fun lineById(id: String): LineEntity? = lineDao.getById(id)
    suspend fun towerById(id: String): TowerEntity? = towerDao.getById(id)

    fun towersOfLineFlow(lineId: String): Flow<List<TowerEntity>> = towerDao.observeByLine(lineId)

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------
    suspend fun createLine(line: LineEntity): LineEntity {
        lineDao.upsert(line)
        return line
    }

    suspend fun updateLine(line: LineEntity) = lineDao.upsert(line)

    suspend fun deleteLine(id: String) {
        towerDao.deleteByLine(id)
        lineDao.deleteById(id)
    }

    suspend fun deleteAllLines() {
        towerDao.deleteAll()
        lineDao.deleteAll()
        cableDao.deleteAll()
    }

    suspend fun addTower(tower: TowerEntity) {
        towerDao.upsert(tower)
        rebuildLineGeometry(tower.lineId)
    }

    suspend fun addTowers(towers: List<TowerEntity>) {
        if (towers.isEmpty()) return
        towerDao.upsertAll(towers)
        towers.map { it.lineId }.distinct().forEach { rebuildLineGeometry(it) }
    }

    suspend fun updateTower(tower: TowerEntity) {
        towerDao.update(tower)
        rebuildLineGeometry(tower.lineId)
    }

    suspend fun updateVisualOffset(towerId: String, dLon: Double, dLat: Double) {
        towerDao.updateVisualOffset(towerId, dLon, dLat)
    }

    suspend fun deleteTower(id: String) {
        val t = towerDao.getById(id) ?: return
        towerDao.deleteById(id)
        rebuildLineGeometry(t.lineId)
    }

    suspend fun upsertCable(cable: CableEntity) = cableDao.upsert(cable)
    suspend fun deleteCable(id: String) = cableDao.deleteById(id)

    /** 重新按杆塔顺序生成线路 LineString 几何 */
    suspend fun rebuildLineGeometry(lineId: String) {
        val towers = towerDao.getByLine(lineId).sortedBy { it.seq }
        val line = lineDao.getById(lineId) ?: return
        if (towers.isEmpty()) {
            lineDao.updateGeometry(lineId, null)
            return
        }
        val pts = towers.map { Point.fromLngLat(it.lon, it.lat) }
        val geo = org.maplibre.geojson.LineString.fromLngLats(pts)
        lineDao.updateGeometry(lineId, geo.toJson())
    }

    // ------------------------------------------------------------------
    // GeoJSON 生成（供地图图层用）
    // ------------------------------------------------------------------
    /** 线路图层要素：按电压等级着色由客户端表达式完成，这里只带属性 */
    suspend fun buildLineFeatures(onlyLineIds: Set<String>? = null): List<Feature> {
        val lines = lineDao.getAll()
        val towersByLine = towerDao.getAll().groupBy { it.lineId }
        return lines.filter { onlyLineIds == null || it.id in onlyLineIds }.mapNotNull { l ->
            val towers = towersByLine[l.id].orEmpty().sortedBy { it.seq }
            if (towers.size < 2) return@mapNotNull null
            val coords = towers.map { Point.fromLngLat(it.lon, it.lat) }
            val geo = org.maplibre.geojson.LineString.fromLngLats(coords)
            Feature.fromGeometry(geo).apply {
                addStringProperty("fid", l.id)
                addStringProperty("name", l.name)
                addNumberProperty("voltage", l.voltageLevel)
                addStringProperty("color", VoltagePalette.hexColorOf(l.voltageLevel))
                addNumberProperty("width", VoltagePalette.widthOf(l.voltageLevel))
                addStringProperty("kind", "line")
            }
        }
    }

    /**
     * 杆塔要素：
     * - 同塔多回（多回线路共址）自动做视觉偏移（2~5m），不改变真实坐标
     * - 属性中带 circuitLabel（Ⅰ/Ⅱ/Ⅲ回）与 towerLabel（杆塔号）
     */
    suspend fun buildTowerFeatures(
        textSize: Float = 11f,
        static: Boolean = true,
        onlyLineIds: Set<String>? = null,
        highlightTowerId: String? = null
    ): List<Feature> {
        val towers = towerDao.getAll()
        val lines = lineDao.getAll().associateBy { it.id }
        // 同塔识别：不同线路、坐标差 < 0.0001° 视为同址
        val groups = mutableListOf<MutableList<TowerEntity>>()
        towers.forEach { t ->
            val hit = groups.firstOrNull { g ->
                val rep = g.first()
                rep.lineId != t.lineId && GeoUtils.nearlySameLocation(rep.lon, rep.lat, t.lon, t.lat, 0.0001)
            }
            if (hit != null) hit.add(t) else groups.add(mutableListOf(t))
        }
        val circuitIndex = HashMap<String, Int>()
        groups.forEach { g ->
            if (g.size > 1) {
                g.sortedBy { it.lineId }.forEachIndexed { idx, t ->
                    circuitIndex[t.id] = idx
                }
            }
        }

        val roman = listOf("Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ")
        return towers.filter { onlyLineIds == null || it.lineId in onlyLineIds }.map { t ->
            var lon = t.lon
            var lat = t.lat
            val idx = circuitIndex[t.id]
            if (idx != null && t.visualOffsetLon == 0.0 && t.visualOffsetLat == 0.0) {
                val offsetM = 2.0 + idx * 1.0 // 2m, 3m, 4m ...
                val (dLon, dLat) = GeoUtils.offsetMetersToDegrees(lon, lat, 0.0, offsetM.coerceAtMost(5.0))
                lon += dLon
                lat += dLat
            } else {
                val (dLon, dLat) = GeoUtils.offsetMetersToDegrees(lon, lat, t.visualOffsetLon, t.visualOffsetLat)
                lon += dLon
                lat += dLat
            }
            val line = lines[t.lineId]
            val circuitLabel = if (circuitIndex.containsKey(t.id)) {
                roman.getOrElse(idx ?: 0) { "${(idx ?: 0) + 1}" } + "回"
            } else if (t.circuitCount > 1) {
                roman.getOrElse(t.circuitNo - 1) { "${t.circuitNo}" } + "回"
            } else ""

            Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                addStringProperty("fid", t.id)
                addStringProperty("towerId", t.id)
                addStringProperty("lineId", t.lineId)
                addStringProperty("lineName", line?.name ?: "")
                addNumberProperty("voltage", line?.voltageLevel ?: 110)
                addStringProperty("towerLabel", t.towerNumber)
                addStringProperty("circuitLabel", circuitLabel)
                addNumberProperty("textSize", textSize)
                addStringProperty("kind", "tower")
                addBooleanProperty("highlight", highlightTowerId == t.id)
                if (static) addBooleanProperty("static", true)
            }
        }
    }

    suspend fun buildCableFeatures(): List<Feature> {
        val cables = cableDao.getAllOnce()
        return cables.mapNotNull { c ->
            try {
                val f = Feature.fromJson("""{"type":"Feature","properties":{},"geometry":${c.geometryGeojson}}""") ?: return@mapNotNull null
                Feature.fromGeometry(f.geometry()).apply {
                    addStringProperty("fid", c.id)
                    addStringProperty("name", c.name)
                    addNumberProperty("voltage", c.voltageLevel)
                    addStringProperty("color", VoltagePalette.hexColorOf(c.voltageLevel))
                    addStringProperty("kind", "cable")
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // ------------------------------------------------------------------
    // 业务校验
    // ------------------------------------------------------------------
    /** 台账校验：坐标范围、同线路杆塔号重复、相邻杆塔间距异常 */
    suspend fun validate(): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val lines = lineDao.getAll()
        val towersByLine = towerDao.getAll().groupBy { it.lineId }
        lines.forEach { l ->
            val ts = towersByLine[l.id].orEmpty().sortedBy { it.seq }
            ts.forEach { t ->
                if (!GeoUtils.isValidChinaCoord(t.lon, t.lat)) {
                    issues.add(ValidationIssue("坐标越界", "${l.name} ${t.towerNumber}", "经纬度 (${t.lon}, ${t.lat}) 不在中国境内范围"))
                }
                if (t.towerNumber.isBlank()) {
                    issues.add(ValidationIssue("杆塔号缺失", l.name, "存在空杆塔号"))
                }
            }
            ts.groupBy { it.towerNumber }.filter { it.value.size > 1 }.forEach { (num, list) ->
                issues.add(ValidationIssue("杆塔号重复", "${l.name} $num", "重复 ${list.size} 次"))
            }
            for (i in 1 until ts.size) {
                val d = GeoUtils.distance(ts[i - 1].lon, ts[i - 1].lat, ts[i].lon, ts[i].lat)
                if (d > 1500) {
                    issues.add(
                        ValidationIssue(
                            "档距异常", "${l.name} ${ts[i - 1].towerNumber}-${ts[i].towerNumber}",
                            "档距 ${GeoUtils.formatDistance(d)}，超过 1500m 阈值"
                        )
                    )
                }
                if (d < 5.0 && d > 0.0) {
                    issues.add(
                        ValidationIssue(
                            "档距过近", "${l.name} ${ts[i - 1].towerNumber}-${ts[i].towerNumber}",
                            "档距仅 ${GeoUtils.formatDistance(d)}"
                        )
                    )
                }
            }
        }
        return issues
    }

    /** 杆塔长度计算：指定线路、杆塔区段，输出逐档距离与总长 */
    suspend fun calcTowerSectionLength(lineName: String, from: String?, to: String?): SectionLengthResult {
        val line = lineDao.findByName(lineName)
            ?: return SectionLengthResult(false, "未找到线路：$lineName", emptyList(), 0.0)
        val ts = towerDao.getByLine(line.id).sortedBy { it.seq }
        if (ts.size < 2) return SectionLengthResult(false, "该线路杆塔不足 2 基", emptyList(), 0.0)
        val startIdx = from?.let { f -> ts.indexOfFirst { it.towerNumber == f } }?.takeIf { it >= 0 } ?: 0
        val endIdx = to?.let { t -> ts.indexOfFirst { it.towerNumber == t } }?.takeIf { it >= 0 } ?: (ts.size - 1)
        if (startIdx >= endIdx) return SectionLengthResult(false, "起始杆塔必须在终止杆塔之前", emptyList(), 0.0)
        val spans = mutableListOf<SpanRow>()
        var total = 0.0
        for (i in startIdx + 1..endIdx) {
            val d = GeoUtils.distance(ts[i - 1].lon, ts[i - 1].lat, ts[i].lon, ts[i].lat)
            total += d
            spans.add(SpanRow(ts[i - 1].towerNumber, ts[i].towerNumber, d))
        }
        return SectionLengthResult(true, "共 ${spans.size} 档，总长 ${GeoUtils.formatDistance(total)}", spans, total)
    }

    companion object {
        fun newId(prefix: String = ""): String =
            (if (prefix.isBlank()) "" else "${prefix}_") + UUID.randomUUID().toString().replace("-", "").take(20)
    }
}

data class ValidationIssue(val type: String, val target: String, val detail: String)
data class SpanRow(val from: String, val to: String, val distance: Double)
data class SectionLengthResult(
    val success: Boolean,
    val message: String,
    val spans: List<SpanRow>,
    val total: Double
)
