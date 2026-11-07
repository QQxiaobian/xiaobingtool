package com.kunming.powerline.data.parser

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shapefile → 专题图层（点 / 线 / 面）
 *
 * 与 [ShapefileReader] 的区别：那个是"把 shp 当线路台账"（每个记录 → 一条线路），
 * 这里保留原始几何类型，一个 .shp 文件对应一个专题图层，记录对应要素。
 *
 * 支持 Point / PolyLine / Polygon / MultiPoint（含 Z、M 变体）。
 * 坐标系：Web Mercator 与高斯-克吕格（CGCS2000 / 西安80）按 .prj 做近似反算，
 * 其余按经纬度原样读取。
 */
object ShapefileThematicReader {

    fun read(
        shp: ByteArray,
        dbf: ByteArray?,
        prj: String?,
        fileName: String = "",
        onProgress: (Float) -> Unit = {}
    ): ThematicParseResult {
        val errors = mutableListOf<ImportError>()
        val warnings = mutableListOf<String>()
        val features = mutableListOf<ImportFeature>()
        val attrs = try {
            dbf?.let { DbfReader.read(it) } ?: emptyList()
        } catch (t: Throwable) {
            warnings.add("属性表读取失败：${t.message}")
            emptyList()
        }
        val transform = coordinateTransform(prj, warnings)

        try {
            val din = DataInputStream(ByteArrayInputStream(shp))
            // 头部 100 字节：文件码(4) + 5 个未用 int(20) + 文件长度(4) + 版本(4) + 几何类型(4, 小端)
            din.skipBytes(28)
            val fileType = Integer.reverseBytes(din.readInt())
            din.skipBytes(100 - 32)
            if (fileType == 0) {
                return ThematicParseResult(
                    emptyList(), listOf(ImportError(-1, "Shapefile 中没有几何要素")),
                    warnings, "shp"
                )
            }

            var index = 0
            while (din.available() >= 8) {
                /* 记录号（大端） */ din.readInt()
                // 记录长度同样是**大端**存储，单位为 16 位字
                val contentLen = din.readInt() * 2
                if (contentLen <= 0 || din.available() < contentLen) break
                val content = ByteArray(contentLen)
                din.readFully(content)
                index++
                if (index % 200 == 0) {
                    onProgress((index.toFloat() / 1000f).coerceAtMost(0.99f))
                }

                val a = attrs.getOrNull(index - 1) ?: emptyMap()
                val name = pick(a, "name", "NAME", "名称", "区域名称", "线路名称", "点位名称")
                val desc = pick(
                    a, "description", "desc", "remark", "备注", "说明", "描述", "note", "注释"
                )
                val props = linkedMapOf<String, String>()
                name?.let { props["name"] = it }
                desc?.let { props["description"] = it }
                // 其余属性原样保留，便于后续查看
                a.forEach { (k, v) ->
                    if (!props.containsKey(k) && v.isNotBlank() && k != "_zone") props[k] = v
                }

                features.addAll(
                    readRecord(content, name, props, transform, index)
                )
            }
            onProgress(1f)
        } catch (t: Throwable) {
            errors.add(ImportError(-1, "Shapefile 解析异常：${t.message}"))
        }

        if (features.isEmpty()) {
            errors.add(ImportError(-1, "未解析到有效几何，可能是空图层或不受支持的几何类型"))
        }

        val kind = when {
            features.isEmpty() -> "point"
            features.map { it.kind }.distinct().size == 1 -> features.first().kind
            else -> "mixed"
        }
        val layerName = fileName.trim()
            .removeSuffix(".shp").removeSuffix(".SHP")
            .ifBlank { "SHP 图层" }
        val layers = if (features.isEmpty()) emptyList() else listOf(
            ImportLayer(name = layerName, geometryType = kind, features = features)
        )
        return ThematicParseResult(layers, errors, warnings, "shp")
    }

    // ------------------------------------------------------------------
    private fun readRecord(
        content: ByteArray,
        name: String?,
        props: Map<String, String>,
        transform: (Double, Double) -> Pair<Double, Double>,
        seq: Int
    ): List<ImportFeature> {
        if (content.size < 4) return emptyList()
        val bb = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN)
        val type = bb.int
        if (type == 0) return emptyList()
        return when (type) {
            // ---- 点 ----
            1, 11, 21 -> {
                if (content.size < 20) return emptyList()
                val (lon, lat) = transform(bb.double, bb.double)
                listOf(
                    ImportFeature(
                        name = name, kind = "point",
                        geometryJson = pointJson(lon, lat), lon = lon, lat = lat, properties = props
                    )
                )
            }
            // ---- 多点 ----
            8, 18, 28 -> {
                if (content.size < 40) return emptyList()
                bb.position(36)
                val n = bb.int
                val out = mutableListOf<ImportFeature>()
                repeat(n.coerceAtMost((content.size - 40) / 16)) {
                    val (lon, lat) = transform(bb.double, bb.double)
                    out.add(
                        ImportFeature(
                            name = name, kind = "point",
                            geometryJson = pointJson(lon, lat),
                            lon = lon, lat = lat, properties = props
                        )
                    )
                }
                out
            }
            // ---- 折线 / 多边形 ----
            3, 13, 23, 5, 15, 25 -> {
                if (content.size < 44) return emptyList()
                bb.position(36)
                val numParts = bb.int
                val numPts = bb.int
                if (numParts <= 0 || numPts <= 0) return emptyList()
                val parts = IntArray(numParts) { bb.int.coerceIn(0, numPts) }
                val pts = ArrayList<Pair<Double, Double>>(numPts)
                repeat(numPts) {
                    if (bb.remaining() < 16) return@repeat
                    pts.add(transform(bb.double, bb.double))
                }
                if (pts.isEmpty()) return emptyList()

                val isPolygon = type == 5 || type == 15 || type == 25
                // 按 part 切环
                val rings = mutableListOf<List<Pair<Double, Double>>>()
                for (i in parts.indices) {
                    val from = parts[i]
                    val to = if (i + 1 < parts.size) parts[i + 1] else pts.size
                    if (to > from) rings.add(pts.subList(from, to))
                }
                if (rings.isEmpty()) rings.add(pts)

                rings.mapNotNull { ring ->
                    if (isPolygon) {
                        if (ring.size < 3) return@mapNotNull null
                        val closed = closeRing(ring)
                        val c = centroid(closed)
                        ImportFeature(
                            name = name, kind = "polygon",
                            geometryJson = polygonJson(closed),
                            lon = c.first, lat = c.second, properties = props
                        )
                    } else {
                        if (ring.size < 2) return@mapNotNull null
                        ImportFeature(
                            name = name, kind = "line",
                            geometryJson = lineJson(ring),
                            lon = ring.first().first, lat = ring.first().second, properties = props
                        )
                    }
                }
            }
            else -> emptyList()
        }
    }

    /**
     * 坐标转换：按 .prj 判断。
     * - 含 Mercator → 墨卡托反算
     * - 含 Gauss_Kruger / Xian_1980 / CGCS2000 且数值很大 → 高斯反算
     * - 其他（含 GCS/WGS84）→ 原样
     */
    private fun coordinateTransform(
        prj: String?,
        warnings: MutableList<String>
    ): (Double, Double) -> Pair<Double, Double> {
        val identity: (Double, Double) -> Pair<Double, Double> = { x, y -> x to y }
        val p = prj ?: return identity
        if (p.contains("Mercator", true)) {
            return { x, y -> ShapefileReader.mercatorToLonLat(x, y) }
        }
        if (p.contains("Gauss_Kruger", true) || p.contains("Gauss Kruger", true) ||
            p.contains("Xian_1980", true) || p.contains("Xi'an", true)
        ) {
            return { x, y ->
                if (kotlin.math.abs(x) > 1000) {
                    // 高斯坐标一般 X 为北向（米），Y 为东向含带号
                    ShapefileReader.gaussKrugerToLonLat(y, x, null)
                } else x to y
            }
        }
        if (p.contains("PROJCS", true)) {
            warnings.add("检测到投影坐标系，已按原值导入，位置可能有偏移（建议先转 WGS84）")
        }
        return identity
    }

    private fun pick(a: Map<String, String>, vararg keys: String): String? {
        keys.forEach { k ->
            a.entries.firstOrNull { it.key.equals(k, true) }?.value
                ?.takeIf { it.isNotBlank() }?.let { return it }
        }
        // 退化匹配：包含关键字的字段名
        val loose = a.entries.firstOrNull { (k, v) ->
            v.isNotBlank() && keys.any { k.contains(it, true) }
        }
        return loose?.value
    }

    private fun pointJson(lon: Double, lat: Double) =
        """{"type":"Point","coordinates":[$lon,$lat]}"""

    private fun lineJson(pts: List<Pair<Double, Double>>) =
        """{"type":"LineString","coordinates":[${pts.joinToString(",") { "[${it.first},${it.second}]" }}]}"""

    private fun polygonJson(ring: List<Pair<Double, Double>>) =
        """{"type":"Polygon","coordinates":[[${ring.joinToString(",") { "[${it.first},${it.second}]" }}]]}"""

    private fun closeRing(pts: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (pts.size < 3) return pts
        val f = pts.first()
        val l = pts.last()
        return if (kotlin.math.abs(f.first - l.first) < 1e-9 &&
            kotlin.math.abs(f.second - l.second) < 1e-9
        ) pts else pts + f
    }

    private fun centroid(pts: List<Pair<Double, Double>>): Pair<Double, Double> {
        val ring = if (pts.size > 1) pts.dropLast(1) else pts
        if (ring.isEmpty()) return 0.0 to 0.0
        return ring.map { it.first }.average() to ring.map { it.second }.average()
    }
}
