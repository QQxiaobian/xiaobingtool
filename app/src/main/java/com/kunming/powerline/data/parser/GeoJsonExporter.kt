package com.kunming.powerline.data.parser

import com.kunming.powerline.data.remote.model.DatasetBundleDto
import java.util.Locale

/**
 * 把「全量数据集」导出成通用 GeoJSON FeatureCollection。
 *
 * 内容：
 * - 线路：用该线路杆塔坐标连成 LineString（>= 2 个点才输出）
 * - 杆塔：Point，带塔号、塔型、回路数
 * - 专题要素：直接嵌入原始 geometryGeojson，带图层名 / 类型 / 属性
 *
 * 纯 Kotlin 手工拼 JSON，不依赖 org.json，因此可在 JVM 单元测试里直接跑。
 * 注意：GeoJSON 是给 QGIS / Google Earth / 第三方系统的**交换格式**；
 * App 之间无损迁移请用「全量备份 JSON」(powerline-dataset/1)。
 */
object GeoJsonExporter {

    fun featureCount(b: DatasetBundleDto): Int {
        val lineStrings = b.ledger.lines.count { line ->
            b.ledger.towers.count { t ->
                (t.lineName ?: "") == line.lineName && t.longitude != null && t.latitude != null
            } >= 2
        }
        val towers = b.ledger.towers.count { it.longitude != null && it.latitude != null }
        val features = b.layers.sumOf { l ->
            l.features.count { it.geometryGeojson.trim().let { g -> g.startsWith("{") && g.endsWith("}") } }
        }
        return lineStrings + towers + features
    }

    fun export(b: DatasetBundleDto): String {
        val sb = StringBuilder(4096)
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        var first = true
        fun sep() {
            if (first) first = false else sb.append(',')
        }
        fun props(vararg pairs: Pair<String, Any?>) {
            sb.append('{')
            pairs.forEachIndexed { i, (k, v) ->
                if (i > 0) sb.append(',')
                sb.append(q(k)).append(':').append(q(v?.toString()))
            }
            sb.append('}')
        }

        // 1) 线路 LineString
        b.ledger.lines.forEach { line ->
            val pts = b.ledger.towers
                .filter { t -> (t.lineName ?: "") == line.lineName && t.longitude != null && t.latitude != null }
                .map { t -> t.longitude!! to t.latitude!! }
            if (pts.size < 2) return@forEach
            sep()
            sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
            sb.append(pts.joinToString(",") { (lo, la) -> "[${n(lo)},${n(la)}]" })
            sb.append("]},\"properties\":")
            props(
                "type" to "line",
                "name" to line.lineName,
                "voltage" to line.voltageLevel,
                "start_station" to line.startStation,
                "end_station" to line.endStation,
                "in_service_date" to line.inServiceDate,
                "remark" to line.remark
            )
            sb.append('}')
        }

        // 2) 杆塔 Point
        b.ledger.towers.forEach { t ->
            val lo = t.longitude ?: return@forEach
            val la = t.latitude ?: return@forEach
            sep()
            sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
            sb.append(n(lo)).append(',').append(n(la))
            if (t.elevation != null) sb.append(',').append(n(t.elevation))
            sb.append("]},\"properties\":")
            props(
                "type" to "tower",
                "name" to t.lineName,
                "tower_number" to t.towerNumber,
                "tower_type" to t.towerType,
                "circuit_no" to t.circuitNo,
                "circuit_count" to t.circuitCount
            )
            sb.append('}')
        }

        // 3) 专题图层要素（geometry 原文直接嵌入）
        b.layers.forEach { layer ->
            layer.features.forEach { f ->
                val g = f.geometryGeojson.trim()
                if (!g.startsWith("{") || !g.endsWith("}")) return@forEach
                sep()
                sb.append("{\"type\":\"Feature\",\"geometry\":").append(g).append(",\"properties\":")
                sb.append('{')
                sb.append(q("type")).append(':').append(q("thematic")).append(',')
                sb.append(q("name")).append(':').append(q(f.name)).append(',')
                sb.append(q("layer")).append(':').append(q(layer.name)).append(',')
                sb.append(q("kind")).append(':').append(q(f.kind)).append(',')
                sb.append(q("attrs")).append(':').append(q(f.propertiesJson))
                sb.append("}}")
            }
        }

        sb.append("]}")
        return sb.toString()
    }

    private fun n(d: Double): String =
        if (d.isNaN() || d.isInfinite()) "null" else d.toString()

    /** JSON 字符串转义；null 输出 JSON null */
    private fun q(s: String?): String {
        if (s == null) return "null"
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        s.forEach { c ->
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append(String.format(Locale.US, "\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
