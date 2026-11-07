package com.kunming.powerline.data.parser

import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream

/** 单个专题要素（点 / 线 / 面） */
data class ImportFeature(
    val name: String?,
    /** point / line / polygon */
    val kind: String,
    /** GeoJSON geometry 原文 */
    val geometryJson: String,
    val lon: Double? = null,
    val lat: Double? = null,
    val properties: Map<String, String> = emptyMap()
)

/** 一个专题图层（对应文件中的一个 Folder，或整个文件） */
data class ImportLayer(
    val name: String,
    val geometryType: String,
    val features: List<ImportFeature>,
    val remark: String? = null
)

data class ThematicParseResult(
    val layers: List<ImportLayer> = emptyList(),
    val errors: List<ImportError> = emptyList(),
    val warnings: List<String> = emptyList(),
    val format: String = ""
) {
    val featureCount: Int get() = layers.sumOf { it.features.size }
    val isEmpty: Boolean get() = layers.all { it.features.isEmpty() }
}

/**
 * 通用专题图层解析器：把 KML / GeoJSON / GPX 里的点、线、面原样转成要素，
 * 不再强制解释成"线路 + 杆塔"。
 *
 * 命名规则与 KmlParser 一致：Folder → Document → 文件名，**不改名**。
 */
object ThematicParser {

    private data class RawFeature(
        val name: String,
        val desc: String,
        val kind: String,
        val coords: List<Pair<Double, Double>>,
        val folder: String,
        val doc: String,
        /** ExtendedData / SchemaData 里的属性（行政区划 KML 的 name、adcode 常在这里） */
        val attrs: Map<String, String> = emptyMap()
    )

    /** 扩展属性里可能承载名称的键（不区分大小写，按优先级） */
    private val NAME_ATTR_KEYS = listOf(
        "name", "名称", "NAME_CHN", "name_zh", "title", "标题", "区域名称", "区县", "乡镇"
    )

    /** 从扩展属性里挑名称，全空则返回空串 */
    internal fun nameFromAttrs(attrs: Map<String, String>): String {
        NAME_ATTR_KEYS.forEach { key ->
            attrs.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                ?.value?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return attrs.entries.firstOrNull { (k, v) ->
            v.isNotBlank() && (k.contains("name", true) || k.contains("名称"))
        }?.value?.trim().orEmpty()
    }

    /**
     * 名称缺失时从备注里取首段。
     *
     * 行政区划 / 规划图转换出来的 KML 常出现 `<name>` 空、只有 `<description>` 的情况，
     * 这类数据入库后整层没有名称，地图上就是一片没有标注的色块。这里把备注的第一个
     * 短句（逗号、顿号、句号等分隔）当作名称，超过 24 字、纯数字、URL 则放弃，
     * 避免把一整段介绍塞进名称字段。
     */
    internal fun nameFromDesc(desc: String): String {
        val raw = desc.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ")
        // 多行备注取第一行，再压平空白
        val text = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isEmpty()) return ""
        if (text.startsWith("http", ignoreCase = true)) return ""
        // 整段都是介绍性文字时不当名称（地图上仍会显示前 12 字，但名称字段保持干净）
        if (text.length > 40) return ""
        val seg = text
            .split('，', ',', '；', ';', '|', '、', '：', ':', '。')
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.trim('.', '·', '-')
            .orEmpty()
        val cand = seg.ifBlank { text }
        if (cand.isEmpty() || cand.length > 24) return ""
        if (cand.all { it.isDigit() || it == '.' || it == '-' || it == ' ' }) return ""
        return cand
    }

    // ------------------------------------------------------------------
    // KML
    // ------------------------------------------------------------------
    fun parseKml(xml: String, fileName: String = ""): ThematicParseResult {
        val errors = mutableListOf<ImportError>()
        val warnings = mutableListOf<String>()
        val raws = mutableListOf<RawFeature>()
        var docName = ""

        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")

            var pmName = ""
            var pmDesc = ""
            // ExtendedData：<Data name="X"><value>Y</value></Data> 或 <SimpleData name="X">Y</SimpleData>
            val pmAttrs = linkedMapOf<String, String>()
            var curDataKey = ""
            var inPlacemark = false
            var kind = ""
            val ctx = mutableListOf<String>()
            val folderStack = ArrayDeque<String>()
            var curCoords = mutableListOf<Pair<Double, Double>>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name?.lowercase() ?: "") {
                        "document" -> ctx.add("doc")
                        "folder" -> {
                            ctx.add("folder")
                            folderStack.addLast("")
                        }
                        "placemark" -> {
                            ctx.add("pm")
                            inPlacemark = true
                            pmName = ""
                            pmDesc = ""
                            pmAttrs.clear()
                            curDataKey = ""
                            kind = ""
                            curCoords = mutableListOf()
                        }
                        "name" -> {
                            val n = safeText(parser).trim()
                            when (ctx.lastOrNull()) {
                                "pm" -> if (inPlacemark) pmName = n
                                "folder" -> if (n.isNotBlank() && folderStack.isNotEmpty()) {
                                    folderStack[folderStack.size - 1] = n
                                }
                                "doc" -> if (docName.isBlank()) docName = n
                            }
                        }
                        "description" -> if (inPlacemark) pmDesc = safeText(parser)
                        "data" -> if (inPlacemark) {
                            curDataKey = parser.getAttributeValue(null, "name")?.trim().orEmpty()
                        }
                        "value" -> if (inPlacemark && curDataKey.isNotBlank()) {
                            val v = safeText(parser).trim()
                            if (v.isNotEmpty()) pmAttrs[curDataKey] = v
                            curDataKey = ""
                        }
                        "simpledata" -> if (inPlacemark) {
                            val k = parser.getAttributeValue(null, "name")?.trim().orEmpty()
                            val v = safeText(parser).trim()
                            if (k.isNotEmpty() && v.isNotEmpty()) pmAttrs[k] = v
                        }
                        "point" -> if (inPlacemark) kind = "point"
                        "linestring" -> if (inPlacemark) kind = "line"
                        "polygon" -> if (inPlacemark) kind = "polygon"
                        "coordinates" -> {
                            val c = safeText(parser).trim()
                            if (c.isNotEmpty()) {
                                // MultiGeometry 里可能连续出现多个 coordinates
                                raws.add(
                                    RawFeature(
                                        // <name> 为空时退回扩展属性里的名称（很多转换工具把名字放在这里）
                                        name = pmName.ifBlank { nameFromAttrs(pmAttrs) },
                                        desc = pmDesc,
                                        kind = kind.ifBlank { "line" },
                                        coords = parseCoords(c),
                                        folder = folderStack.lastOrNull()?.trim().orEmpty(),
                                        doc = docName,
                                        attrs = pmAttrs.toMap()
                                    )
                                )
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (parser.name?.lowercase() ?: "") {
                        "placemark" -> {
                            inPlacemark = false
                            if (ctx.lastOrNull() == "pm") ctx.removeAt(ctx.size - 1)
                        }
                        "folder" -> {
                            if (ctx.lastOrNull() == "folder") ctx.removeAt(ctx.size - 1)
                            if (folderStack.isNotEmpty()) folderStack.removeLast()
                        }
                        "document" -> if (ctx.lastOrNull() == "doc") ctx.removeAt(ctx.size - 1)
                    }
                }
                event = parser.next()
            }
        } catch (t: Throwable) {
            errors.add(ImportError(-1, "KML 解析异常：${t.message}"))
        }

        val valid = raws.filter { it.coords.isNotEmpty() }
        if (valid.size < raws.size) warnings.add("有 ${raws.size - valid.size} 个要素坐标为空，已跳过")

        // 分组：Folder → Document → 文件名
        val hasFolder = valid.any { it.folder.isNotBlank() }
        val groups: List<Pair<String, List<RawFeature>>> = if (hasFolder) {
            valid.groupBy { it.folder.ifBlank { it.doc.ifBlank { fileName } } }.toList()
        } else {
            val name = when {
                docName.isNotBlank() && !KmlParser.isPlaceholderName(docName) -> docName
                fileName.isNotBlank() -> fileName
                docName.isNotBlank() -> docName
                valid.size == 1 -> valid.first().name.ifBlank { "未命名图层" }
                else -> "未命名图层"
            }
            listOf(name to valid)
        }

        val layers = groups.map { (name, list) ->
            val feats = list.mapNotNull { toFeature(it) }
            val kind = when {
                feats.isEmpty() -> "point"
                feats.map { it.kind }.distinct().size == 1 -> feats.first().kind
                else -> "mixed"
            }
            ImportLayer(
                name = name.trim().ifBlank { "未命名图层" },
                geometryType = kind,
                features = feats
            )
        }.filter { it.features.isNotEmpty() }

        return ThematicParseResult(layers, errors, warnings, "kml")
    }

    private fun toFeature(r: RawFeature): ImportFeature? {
        val c = r.coords
        if (c.isEmpty()) return null
        // 名称缺失时从备注首段兜底，保证行政区划这类「有备注、无名称」的数据也能标注
        val displayName = r.name.trim().ifBlank { nameFromDesc(r.desc) }
        return when (r.kind) {
            "point" -> ImportFeature(
                name = displayName.ifBlank { null },
                kind = "point",
                geometryJson = pointJson(c.first()),
                lon = c.first().first, lat = c.first().second,
                properties = props(r)
            )
            "polygon" -> ImportFeature(
                name = displayName.ifBlank { null },
                kind = "polygon",
                geometryJson = polygonJson(closeRing(c)),
                lon = centroid(c).first, lat = centroid(c).second,
                properties = props(r)
            )
            else -> ImportFeature(
                name = displayName.ifBlank { null },
                kind = "line",
                geometryJson = lineJson(c),
                lon = c.first().first, lat = c.first().second,
                properties = props(r)
            )
        }
    }

    private fun props(r: RawFeature): Map<String, String> {
        val m = linkedMapOf<String, String>()
        // 先把扩展属性铺进去（adcode、面积等），点击信息卡可以看到原始字段
        r.attrs.forEach { (k, v) ->
            if (v.isNotBlank()) {
                m[k] = v.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            }
        }
        if (r.name.isNotBlank()) m["name"] = r.name
        if (r.desc.isNotBlank()) {
            m["description"] = r.desc.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        }
        return m
    }

    private fun safeText(parser: XmlPullParser): String =
        try { parser.nextText() ?: "" } catch (_: Throwable) { "" }

    // ------------------------------------------------------------------
    // GeoJSON
    // ------------------------------------------------------------------
    fun parseGeoJson(json: String, fileName: String = ""): ThematicParseResult {
        val errors = mutableListOf<ImportError>()
        val features = mutableListOf<ImportFeature>()
        var layerName = fileName
        try {
            val root = JSONObject(json)
            val arr = when (root.optString("type")) {
                "FeatureCollection" -> root.optJSONArray("features") ?: JSONArray()
                "Feature" -> JSONArray().put(root)
                else -> JSONArray()
            }
            if (layerName.isBlank()) layerName = root.optString("name").ifBlank { "未命名图层" }
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val props = f.optJSONObject("properties") ?: JSONObject()
                // 名称键五花八门：name / NAME / 名称 / NAME_CHN / title ……
                // 都没有时退回 description / 备注的首段，避免整层无标注
                val propsMap = props.keys().asSequence().associateWith { props.optString(it) }
                val descText = listOf("description", "desc", "remark", "备注", "说明", "描述")
                    .firstNotNullOfOrNull { k -> propsMap.entries.firstOrNull { it.key.equals(k, true) }?.value }
                    .orEmpty()
                val name = nameFromAttrs(propsMap)
                    .ifBlank { nameFromDesc(descText) }
                    .ifBlank { null }
                val geom = f.optJSONObject("geometry") ?: continue
                features.addAll(expandGeometry(geom, name, props))
            }
        } catch (t: Throwable) {
            errors.add(ImportError(-1, "GeoJSON 解析异常：${t.message}"))
        }
        val kind = when {
            features.isEmpty() -> "point"
            features.map { it.kind }.distinct().size == 1 -> features.first().kind
            else -> "mixed"
        }
        val layers = if (features.isEmpty()) emptyList() else listOf(
            ImportLayer(layerName.ifBlank { "未命名图层" }, kind, features)
        )
        return ThematicParseResult(layers, errors, emptyList(), "geojson")
    }

    private fun expandGeometry(geom: JSONObject, name: String?, props: JSONObject): List<ImportFeature> {
        val type = geom.optString("type")
        val coords = geom.optJSONArray("coordinates")
        val out = mutableListOf<ImportFeature>()
        val propsMap = props.keys().asSequence().associateWith { props.optString(it) }
        val baseName = name?.ifBlank { null }
        when (type) {
            "Point" -> {
                val c = coords ?: return emptyList()
                val lon = c.optDouble(0, Double.NaN)
                val lat = c.optDouble(1, Double.NaN)
                if (lon.isNaN() || lat.isNaN()) return emptyList()
                out.add(
                    ImportFeature(baseName, "point", pointJson(lon to lat), lon, lat, propsMap)
                )
            }
            "MultiPoint" -> {
                val cs = coords ?: return emptyList()
                for (i in 0 until cs.length()) {
                    val c = cs.optJSONArray(i) ?: continue
                    val lon = c.optDouble(0, Double.NaN)
                    val lat = c.optDouble(1, Double.NaN)
                    if (lon.isNaN() || lat.isNaN()) continue
                    out.add(ImportFeature(baseName, "point", pointJson(lon to lat), lon, lat, propsMap))
                }
            }
            "LineString" -> {
                val pts = toPoints(coords)
                if (pts.size >= 2) {
                    out.add(
                        ImportFeature(baseName, "line", lineJson(pts), pts.first().first, pts.first().second, propsMap)
                    )
                }
            }
            "MultiLineString" -> {
                val arr = coords ?: return emptyList()
                for (i in 0 until arr.length()) {
                    val pts = toPoints(arr.optJSONArray(i))
                    if (pts.size >= 2) {
                        out.add(ImportFeature(baseName, "line", lineJson(pts), pts.first().first, pts.first().second, propsMap))
                    }
                }
            }
            "Polygon" -> {
                val pts = toPoints(coords)
                if (pts.size >= 3) {
                    out.add(
                        ImportFeature(
                            baseName, "polygon", polygonJson(closeRing(pts)),
                            centroid(pts).first, centroid(pts).second, propsMap
                        )
                    )
                }
            }
            "MultiPolygon" -> {
                val arr = coords ?: return emptyList()
                for (i in 0 until arr.length()) {
                    val pts = toPoints(arr.optJSONArray(i))
                    if (pts.size >= 3) {
                        out.add(
                            ImportFeature(
                                baseName, "polygon", polygonJson(closeRing(pts)),
                                centroid(pts).first, centroid(pts).second, propsMap
                            )
                        )
                    }
                }
            }
            "GeometryCollection" -> {
                val gs = geom.optJSONArray("geometries") ?: return emptyList()
                for (i in 0 until gs.length()) {
                    gs.optJSONObject(i)?.let { out.addAll(expandGeometry(it, name, props)) }
                }
            }
        }
        return out
    }

    private fun toPoints(arr: JSONArray?): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val c = arr.optJSONArray(i) ?: continue
            val lon = c.optDouble(0, Double.NaN)
            val lat = c.optDouble(1, Double.NaN)
            if (!lon.isNaN() && !lat.isNaN()) out.add(lon to lat)
        }
        return out
    }

    // ------------------------------------------------------------------
    // GPX
    // ------------------------------------------------------------------
    fun parseGpx(xml: String, fileName: String = ""): ThematicParseResult {
        val errors = mutableListOf<ImportError>()
        val points = mutableListOf<ImportFeature>()
        val lines = mutableListOf<ImportFeature>()
        var curSeg = mutableListOf<Pair<Double, Double>>()
        var curName = ""
        var curDesc = ""
        var trkName = ""
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
            var lat = 0.0
            var lon = 0.0
            var inWpt = false
            var inTrk = false
            var inTrkpt = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name?.lowercase() ?: "") {
                        "wpt" -> {
                            inWpt = true
                            curName = ""
                            curDesc = ""
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        }
                        "trk" -> {
                            inTrk = true
                            trkName = ""
                        }
                        "trkseg" -> curSeg = mutableListOf()
                        "trkpt" -> {
                            inTrkpt = true
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        }
                        "name" -> {
                            val n = safeText(parser).trim()
                            if (inWpt) curName = n else if (inTrk) trkName = n
                        }
                        "desc", "cmt" -> if (inWpt) curDesc = safeText(parser).trim()
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (parser.name?.lowercase() ?: "") {
                        "wpt" -> {
                            inWpt = false
                            if (lon != 0.0 && lat != 0.0) {
                                points.add(
                                    ImportFeature(
                                        name = curName.ifBlank { null },
                                        kind = "point",
                                        geometryJson = pointJson(lon to lat),
                                        lon = lon, lat = lat,
                                        properties = if (curDesc.isBlank()) emptyMap() else mapOf("description" to curDesc)
                                    )
                                )
                            }
                        }
                        "trkpt" -> {
                            inTrkpt = false
                            if (lon != 0.0 && lat != 0.0) curSeg.add(lon to lat)
                        }
                        "trkseg" -> {
                            if (curSeg.size >= 2) {
                                lines.add(
                                    ImportFeature(
                                        name = trkName.ifBlank { null },
                                        kind = "line",
                                        geometryJson = lineJson(curSeg),
                                        lon = curSeg.first().first, lat = curSeg.first().second
                                    )
                                )
                            }
                        }
                        "trk" -> inTrk = false
                    }
                }
                event = parser.next()
            }
        } catch (t: Throwable) {
            errors.add(ImportError(-1, "GPX 解析异常：${t.message}"))
        }
        val feats = points + lines
        val kind = when {
            feats.isEmpty() -> "point"
            feats.map { it.kind }.distinct().size == 1 -> feats.first().kind
            else -> "mixed"
        }
        val layers = if (feats.isEmpty()) emptyList() else listOf(
            ImportLayer(fileName.ifBlank { "GPX 要素" }, kind, feats)
        )
        return ThematicParseResult(layers, errors, emptyList(), "gpx")
    }

    // ------------------------------------------------------------------
    // 几何工具
    // ------------------------------------------------------------------
    private fun pointJson(p: Pair<Double, Double>): String =
        """{"type":"Point","coordinates":[${p.first},${p.second}]}"""

    private fun lineJson(pts: List<Pair<Double, Double>>): String =
        """{"type":"LineString","coordinates":[${pts.joinToString(",") { "[${it.first},${it.second}]" }}]}"""

    private fun polygonJson(ring: List<Pair<Double, Double>>): String =
        """{"type":"Polygon","coordinates":[[${ring.joinToString(",") { "[${it.first},${it.second}]" }}]]}"""

    private fun closeRing(pts: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (pts.size < 3) return pts
        val first = pts.first()
        val last = pts.last()
        return if (kotlin.math.abs(first.first - last.first) < 1e-9 &&
            kotlin.math.abs(first.second - last.second) < 1e-9
        ) pts else pts + first
    }

    private fun centroid(pts: List<Pair<Double, Double>>): Pair<Double, Double> {
        if (pts.isEmpty()) return 0.0 to 0.0
        return pts.map { it.first }.average() to pts.map { it.second }.average()
    }

    fun parseCoords(s: String): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        s.trim().split(Regex("""\s+""")).forEach { t ->
            val parts = t.split(',')
            if (parts.size >= 2) {
                val lon = parts[0].toDoubleOrNull()
                val lat = parts[1].toDoubleOrNull()
                if (lon != null && lat != null) out.add(lon to lat)
            }
        }
        return out
    }
}
