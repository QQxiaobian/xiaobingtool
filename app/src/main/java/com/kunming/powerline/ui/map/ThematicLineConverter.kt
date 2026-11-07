package com.kunming.powerline.ui.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * 专题线要素 → 正式线路的几何换算
 *
 * 专题线没有"杆塔"概念，转换时把线要素的每个顶点当作一基"虚拟杆塔"，
 * 编号按 1..N 顺序给出，这样线路图层、长度计算、杆塔列表都能直接复用。
 */
object ThematicLineConverter {

    /** 少于 2 个点无法成线 */
    const val MIN_POINTS = 2

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 从 GeoJSON geometry 里取折线坐标；MultiLineString 取最长的一段 */
    fun lineCoords(geometryGeojson: String?): List<Pair<Double, Double>> {
        if (geometryGeojson.isNullOrBlank()) return emptyList()
        return try {
            val obj = json.parseToJsonElement(geometryGeojson) as? JsonObject ?: return emptyList()
            when ((obj["type"] as? JsonPrimitive)?.content) {
                "LineString" -> readRing(obj["coordinates"])
                "MultiLineString" -> {
                    val arr = obj["coordinates"] as? JsonArray ?: return emptyList()
                    arr.mapNotNull { readRing(it).takeIf { pts -> pts.size >= MIN_POINTS } }
                        .maxByOrNull { it.size }
                        ?: emptyList()
                }
                "Polygon" -> {
                    val arr = obj["coordinates"] as? JsonArray ?: return emptyList()
                    readRing(arr.firstOrNull())
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readRing(el: kotlinx.serialization.json.JsonElement?): List<Pair<Double, Double>> {
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { p ->
            val c = p as? JsonArray ?: return@mapNotNull null
            if (c.size < 2) return@mapNotNull null
            val lon = (c[0] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lat = (c[1] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            if (!lon.isFinite() || !lat.isFinite()) return@mapNotNull null
            if (lon == 0.0 && lat == 0.0) return@mapNotNull null
            lon to lat
        }
    }

    /** 顶点 → 杆塔编号（1..N，原样可读） */
    fun towerNumbers(count: Int): List<String> = (1..count).map { it.toString() }

    /** 线路命名：优先要素名，重名时追加序号 */
    fun lineName(featureName: String?, layerName: String, index: Int, total: Int): String {
        val base = featureName?.trim()?.takeIf { it.isNotBlank() }
            ?: if (total <= 1) layerName else "$layerName ${index + 1}"
        return base
    }

    /** 去闭合尾点（首尾相同会让线路多画一段回程） */
    fun dropClosingPoint(pts: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (pts.size < 3) return pts
        val f = pts.first()
        val l = pts.last()
        return if (kotlin.math.abs(f.first - l.first) < 1e-9 &&
            kotlin.math.abs(f.second - l.second) < 1e-9
        ) pts.dropLast(1) else pts
    }
}
