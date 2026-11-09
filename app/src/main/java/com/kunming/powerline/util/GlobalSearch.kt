package com.kunming.powerline.util

import com.kunming.powerline.ui.SearchHit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 全局搜索的归一化记录（1.6.3）。
 *
 * 所有图层（线路、杆塔、电缆、点位、专题要素、照片、自定义标注、导入批次）
 * 在检索前统一转换成 [SearchRecord]，匹配逻辑集中在 [GlobalSearch.match]，
 * 不依赖 Android / Room，便于纯 JVM 单元测试。
 */
data class SearchRecord(
    val title: String,
    val subtitle: String,
    val lon: Double? = null,
    val lat: Double? = null,
    val kind: String,
    val refId: String? = null,
    val zoom: Double = 16.0,
    /** 参与匹配但不展示的附加字段（备注、属性、文件名等） */
    val keywords: List<String?> = emptyList(),
    /** 排序权重，越小越靠前 */
    val priority: Int = 50
)

/**
 * 纯 JVM 的全局搜索内核。
 */
object GlobalSearch {

    /** 结果上限：避免导入成千上万个专题要素时把结果面板撑爆 */
    const val MAX_RESULTS = 200

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 排序中间量：名称匹配度 > 有无坐标 > 类型权重 > 标题 */
    private data class Ranked(
        val nameScore: Int,
        val noCoord: Int,
        val priority: Int,
        val hit: SearchHit
    )

    /**
     * 在全部记录中检索。
     *
     * - 按空白 / 中英文逗号分号顿号分词，所有词都要命中（AND）
     * - 大小写不敏感
     * - 名称精确 / 前缀 / 包含优先于仅备注命中
     * - 有坐标的结果优先（可定位）
     * - 按 kind + refId + 标题去重
     */
    fun match(query: String, records: List<SearchRecord>): List<SearchHit> {
        val q = query.trim().lowercase()
        val terms = q.split(Regex("[\\s,，;；、]+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()

        return records.mapNotNull { r ->
            val title = r.title.trim()
            val extra = r.keywords.filterNotNull().joinToString(" ")
            val hay = "$title ${r.subtitle} $extra".lowercase()
            if (!terms.all { hay.contains(it) }) return@mapNotNull null

            val lt = title.lowercase()
            val nameScore = when {
                lt == q -> 0
                lt.startsWith(q) -> 1
                lt.contains(q) -> 2
                else -> 3
            }
            val noCoord = if (r.lon == null || r.lat == null ||
                (r.lon == 0.0 && r.lat == 0.0)) 1 else 0

            Ranked(
                nameScore, noCoord, r.priority,
                SearchHit(
                    title = title.ifBlank { "未命名" },
                    subtitle = r.subtitle,
                    lon = r.lon ?: 0.0,
                    lat = r.lat ?: 0.0,
                    kind = r.kind,
                    refId = r.refId,
                    zoom = r.zoom
                )
            )
        }
            .sortedWith(
                compareBy<Ranked> { it.nameScore }
                    .thenBy { it.noCoord }
                    .thenBy { it.priority }
                    .thenBy { it.hit.title }
            )
            .map { it.hit }
            .distinctBy { "${it.kind}|${it.refId}|${it.title}" }
            .take(MAX_RESULTS)
    }

    /**
     * 从 GeoJSON geometry 原文里取第一个坐标，用于给「没有显式 lon/lat 字段」
     * 的电缆 / 自定义标注 / 专题要素提供定位点。
     *
     * 支持 Point / LineString / Polygon / Multi* 的任意嵌套。
     * 用 kotlinx.serialization 而非 org.json，保证纯 JVM 单测可运行。
     */
    fun firstCoordinate(geometryGeojson: String?): Pair<Double, Double>? {
        if (geometryGeojson.isNullOrBlank()) return null
        return try {
            val root = json.parseToJsonElement(geometryGeojson).jsonObject
            walkCoordinates(root["coordinates"])
        } catch (_: Exception) {
            null
        }
    }

    private fun walkCoordinates(e: JsonElement?): Pair<Double, Double>? {
        if (e !is JsonArray) return null
        if (e.size >= 2) {
            val a = e[0] as? JsonPrimitive
            val b = e[1] as? JsonPrimitive
            if (a != null && b != null) {
                val lon = a.content.toDoubleOrNull()
                val lat = b.content.toDoubleOrNull()
                if (lon != null && lat != null) return lon to lat
            }
        }
        return e.firstNotNullOfOrNull { walkCoordinates(it) }
    }

    /** 几何类型中文标签 */
    fun geometryLabel(kind: String?): String = when (kind) {
        "point" -> "点"
        "line" -> "线"
        "polygon" -> "面"
        "mixed" -> "混合"
        else -> kind ?: "要素"
    }
}
