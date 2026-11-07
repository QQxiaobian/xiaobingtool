package com.kunming.powerline.data.parser

import com.kunming.powerline.util.VoltagePalette
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream

/**
 * KML 解析器（纯逻辑，不依赖 Android Context，便于单元测试）。
 *
 * 命名规则（用户要求"按照文件里面的名称导入"）：
 * 1. 有 `<Folder>`：以 Folder 名作为线路名（一个 Folder = 一条线路）；
 * 2. 没有 Folder 但有多个含 `LineString/Polygon` 的 Placemark：每个 Placemark 独立成线，用其 name；
 * 3. 其余情况：单条线路，名称取 Placemark 的 name → Document 的 name → 文件名。
 *
 * 编号规则：**保留文件中的原始杆塔编号**（如 "012"、"N35"、"12号塔" → 12），
 * 只有在文件里完全没有编号时才按沿线顺序生成 1..N。不再事后重排编号。
 *
 * 其他历史修复：
 * - 空标签（<coordinates/>、<name></name>）上 nextText() 返回 null 不再 NPE
 * - 同一 Placemark 内 Point + LineString 不再重复生成杆塔（以线为主干）
 * - 线路首尾闭合坐标自动去掉，保证开放折线
 */
object KmlParser {

    private data class KmlGeometry(val type: String, val coords: String)

    private data class RawPlacemark(
        val name: String,
        val desc: String,
        val voltage: Int?,
        val geometries: List<KmlGeometry>,
        val folder: String,
        val doc: String
    )

    private data class NamedPoint(
        val lon: Double,
        val lat: Double,
        val number: String?,
        val remark: String?
    )

    /**
     * @param fileName 文件名（不含扩展名），仅作为名称/电压的兜底
     */
    suspend fun parse(
        xml: String,
        onProgress: (Float) -> Unit = {},
        fileName: String = ""
    ): ParseResult {
        val errors = mutableListOf<ImportError>()
        val raw = mutableListOf<RawPlacemark>()
        val fileVoltage = VoltagePalette.parseLevel(fileName)
        var docName = ""

        try {
            // 用 XmlPullParserFactory 而非 android.util.Xml.newPullParser()，
            // 这样 JVM 单元测试也能运行（kxml2 实现）。
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")

            var pmName = ""
            var pmDesc = ""
            var pmVoltage: Int? = null
            val geometries = mutableListOf<KmlGeometry>()
            var inPlacemark = false
            var geomType = ""
            // 上下文栈：doc / folder / pm，用于区分 <name> 属于谁
            val ctx = mutableListOf<String>()
            val folderStack = ArrayDeque<String>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val tag = parser.name?.lowercase() ?: ""
                    when (tag) {
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
                            pmVoltage = null
                            geometries.clear()
                            geomType = ""
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
                        "voltage" -> if (inPlacemark) pmVoltage = VoltagePalette.parseLevel(safeText(parser))
                        "lineString" -> if (inPlacemark) geomType = "line"
                        "point" -> if (inPlacemark) geomType = "point"
                        "polygon" -> if (inPlacemark) geomType = "polygon"
                        "coordinates" -> {
                            val c = safeText(parser).trim()
                            if (c.isNotEmpty()) {
                                geometries.add(KmlGeometry(geomType.ifBlank { "line" }, c))
                            }
                        }
                        "data" -> {
                            val k = parser.getAttributeValue(null, "name")
                            if (k != null && (k.contains("电压") || k.equals("voltage", true))) {
                                val v = readDataValue(parser)
                                if (v.isNotBlank()) pmVoltage = VoltagePalette.parseLevel(v)
                            }
                        }
                        "simpledata" -> {
                            val k = parser.getAttributeValue(null, "name")
                            if (k != null && (k.contains("电压") || k.equals("voltage", true))) {
                                pmVoltage = VoltagePalette.parseLevel(safeText(parser))
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    val tag = parser.name?.lowercase() ?: ""
                    when (tag) {
                        "placemark" -> {
                            if (inPlacemark) {
                                raw.add(
                                    RawPlacemark(
                                        name = pmName,
                                        desc = pmDesc,
                                        voltage = pmVoltage,
                                        geometries = geometries.toList(),
                                        folder = folderStack.lastOrNull()?.trim().orEmpty(),
                                        doc = docName
                                    )
                                )
                                inPlacemark = false
                            }
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
            onProgress(1f)
        } catch (t: Throwable) {
            errors.add(ImportError(-1, "KML 解析异常：${t.message}"))
        }

        val warnings = mutableListOf<String>()
        val lines = buildLines(raw, docName, fileVoltage, fileName, warnings)
        val list = lines.map { it.dedupTowers() }
        val (valid, verr) = ImportValidator.validate(list, allowDuplicateNumbers = true)
        errors.addAll(verr)
        return ParseResult(valid, errors, warnings + ImportValidator.detectSameTower(valid), "kml")
    }

    /** 安全读取元素文本，nextText() 返回 null 时回退空串 */
    private fun safeText(parser: XmlPullParser): String =
        try { parser.nextText() ?: "" } catch (_: Throwable) { "" }

    /**
     * 读取 ExtendedData.Data 的 <value> 子元素文本。
     * 调用时 parser 位于 <Data> START_TAG，遍历子元素找 <value>。
     */
    private fun readDataValue(parser: XmlPullParser): String {
        return try {
            val depth = parser.depth
            var ev = parser.next()
            while (!(ev == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (ev == XmlPullParser.START_TAG && parser.name?.lowercase() == "value") {
                    return safeText(parser)
                }
                ev = parser.next()
            }
            ""
        } catch (_: Throwable) { "" }
    }

    // ------------------------------------------------------------------
    // 分组
    // ------------------------------------------------------------------

    private fun buildLines(
        raw: List<RawPlacemark>,
        docName: String,
        fileVoltage: Int?,
        fileName: String,
        warnings: MutableList<String>
    ): List<ImportLine> {
        if (raw.isEmpty()) return emptyList()

        val groups: List<Pair<String, List<RawPlacemark>>> = run {
            val hasFolder = raw.any { it.folder.isNotBlank() }
            if (hasFolder) {
                // 一个 Folder = 一条线路，Folder 名即线路名
                raw.groupBy { p ->
                    p.folder.ifBlank { p.doc.ifBlank { fallbackName(p, fileName) } }
                }.toList()
            } else {
                val linePms = raw.filter { p ->
                    p.geometries.any { it.type == "line" || it.type == "polygon" }
                }
                when {
                    // 文件中多个含线/面的 Placemark：每个独立成线，用其自身名称
                    linePms.size >= 2 -> {
                        val out = mutableListOf<Pair<String, MutableList<RawPlacemark>>>()
                        linePms.forEach { p -> out.add(fallbackName(p, fileName) to mutableListOf(p)) }
                        // 纯点要素按坐标吸附到所属线；吸附不上的忽略并提示
                        val loose = raw.filter { p -> p.geometries.all { it.type == "point" } }
                        var unmatched = 0
                        loose.forEach { pt ->
                            val c = pt.geometries.firstNotNullOfOrNull { g ->
                                parseCoordString(g.coords).firstOrNull()
                            }
                            val hit = if (c == null) null else out.firstOrNull { (_, list) ->
                                list.first().geometries.any { g ->
                                    (g.type == "line" || g.type == "polygon") &&
                                        parseCoordString(g.coords).any { samePoint(it.first, it.second, c.first, c.second) }
                                }
                            }
                            if (hit != null) hit.second.add(pt) else unmatched++
                        }
                        if (unmatched > 0) warnings.add("有 $unmatched 个点要素未落在任何线上，已忽略")
                        out.map { it.first to it.second.toList() }
                    }
                    // 单条线路：名称优先 Placemark 名 → Document 名 → 文件名
                    else -> {
                        val single = linePms.firstOrNull()
                        val name = when {
                            single != null && single.name.isNotBlank() && !isPlaceholderName(single.name) -> single.name
                            raw.size == 1 && raw.first().name.isNotBlank() && !isPlaceholderName(raw.first().name) -> raw.first().name
                            // Document 名优先，但要排除 "layer0" / "未命名" 这类导出占位名
                            docName.isNotBlank() && !isPlaceholderName(docName) -> docName
                            fileName.isNotBlank() -> fileName
                            docName.isNotBlank() -> docName
                            else -> "未命名线路"
                        }
                        listOf(name to raw)
                    }
                }
            }
        }

        return groups.map { (name, pms) ->
            val lineName = name.trim().ifBlank { "未命名线路" }
            val voltage = pms.firstNotNullOfOrNull { it.voltage }
                ?: VoltagePalette.parseLevel(lineName)
                ?: fileVoltage
                ?: 110
            val line = ImportLine(name = lineName, voltageLevel = voltage)
            buildTowers(line, pms)
            line
        }
    }

    private fun fallbackName(p: RawPlacemark, fileName: String): String = when {
        p.folder.isNotBlank() && !isPlaceholderName(p.folder) -> p.folder
        p.name.isNotBlank() && !isPlaceholderName(p.name) -> p.name
        p.doc.isNotBlank() && !isPlaceholderName(p.doc) -> p.doc
        fileName.isNotBlank() -> fileName
        p.folder.isNotBlank() -> p.folder
        p.name.isNotBlank() -> p.name
        p.doc.isNotBlank() -> p.doc
        else -> "未命名线路"
    }

    /**
     * GIS 导出工具常见的占位名（"layer0" / "未命名图层" / "New Folder" ...），
     * 不应作为线路名，遇到时回退到文件名。
     */
    internal fun isPlaceholderName(raw: String): Boolean {
        val n = raw.trim().lowercase()
        if (n.isEmpty()) return true
        return n.matches(
            Regex("""(layer|图层|placemark|folder|document|default|untitled|无标题|未命名|new_?folder|新建文件夹)[\s_\-]*\d*""")
        )
    }

    /**
     * 由一个分组的全部 Placemark 生成杆塔：
     *
     * - 线坐标（LineString/Polygon）作为**排序骨架**
     * - 点要素作为**杆塔实体**，按沿线路的投影距离排序
     * - 线顶点若没有对应点，也补成杆塔（避免丢塔）
     * - **编号一律沿用文件里的原始编号**，仅缺编号时按顺序生成
     */
    private fun buildTowers(line: ImportLine, pms: List<RawPlacemark>) {
        val linePts = mutableListOf<Pair<Double, Double>>()
        val points = mutableListOf<NamedPoint>()

        pms.forEach { p ->
            p.geometries.forEach { g ->
                when (g.type) {
                    "line", "polygon" -> linePts.addAll(parseCoordString(g.coords))
                    else -> parseCoordString(g.coords).forEach { c ->
                        points.add(
                            NamedPoint(
                                lon = c.first, lat = c.second,
                                number = extractTowerNoOrNull("${p.name} ${p.desc}"),
                                remark = extractRemark(p.name, p.desc)
                            )
                        )
                    }
                }
            }
        }

        // 首尾不闭合：GIS 导出的线常把首尾写成同一点，去掉尾部重复点
        while (linePts.size > 1 && samePoint(
                linePts.first().first, linePts.first().second,
                linePts.last().first, linePts.last().second
            )
        ) {
            linePts.removeAt(linePts.size - 1)
        }

        if (linePts.size >= 2) {
            line.lineCoords.addAll(linePts)
            // 候选杆塔：全部点 + 不在点上的线顶点
            val candidates = mutableListOf<NamedPoint>()
            candidates.addAll(points)
            linePts.forEach { c ->
                if (points.none { samePoint(it.lon, it.lat, c.first, c.second) }) {
                    candidates.add(NamedPoint(c.first, c.second, null, null))
                }
            }
            // 按沿线路的投影距离排序，保证 1..N 依次相连
            val sorted = candidates.sortedBy { projectionAlong(linePts, it.lon, it.lat) }
            sorted.forEach { pt ->
                line.towers.add(
                    ImportTower(
                        towerNumber = pt.number ?: "${line.towers.size + 1}",
                        lon = pt.lon, lat = pt.lat,
                        remark = pt.remark,
                        sourceRow = line.towers.size + 1
                    )
                )
            }
        } else if (points.isNotEmpty()) {
            // 只有点：按文件出现顺序，编号保留
            points.forEach { pt ->
                line.towers.add(
                    ImportTower(
                        towerNumber = pt.number ?: "${line.towers.size + 1}",
                        lon = pt.lon, lat = pt.lat,
                        remark = pt.remark,
                        sourceRow = line.towers.size + 1
                    )
                )
            }
        } else if (linePts.size == 1) {
            val c = linePts.first()
            line.towers.add(
                ImportTower(towerNumber = "1", lon = c.first, lat = c.second, sourceRow = 1)
            )
        }
    }

    /** 点在线（多段折线）上的累计投影距离，用于沿线排序 */
    private fun projectionAlong(poly: List<Pair<Double, Double>>, lon: Double, lat: Double): Double {
        if (poly.size < 2) return 0.0
        var best = Double.MAX_VALUE
        var bestDist = 0.0
        var acc = 0.0
        for (i in 0 until poly.size - 1) {
            val (x1, y1) = poly[i]
            val (x2, y2) = poly[i + 1]
            val segLen = Math.hypot(x2 - x1, y2 - y1)
            if (segLen <= 0.0) continue
            val t = (((lon - x1) * (x2 - x1) + (lat - y1) * (y2 - y1)) / (segLen * segLen))
                .coerceIn(0.0, 1.0)
            val px = x1 + t * (x2 - x1)
            val py = y1 + t * (y2 - y1)
            val d = Math.hypot(lon - px, lat - py)
            if (d < best) {
                best = d
                bestDist = acc + t * segLen
            }
            acc += segLen
        }
        return bestDist
    }

    private fun samePoint(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Boolean =
        kotlin.math.abs(lon1 - lon2) < 1e-6 && kotlin.math.abs(lat1 - lat2) < 1e-6

    /** 备注：去掉 KML 描述里的 HTML 标签，避免地图弹窗显示一堆标签 */
    private fun extractRemark(name: String, desc: String): String? {
        val cleaned = desc.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val v = cleaned.ifBlank { name }.trim()
        return v.ifBlank { null }
    }

    /** 去掉完全重复坐标的要素（保留首个），**不重排编号** */
    private fun ImportLine.dedupTowers(): ImportLine {
        if (towers.isEmpty()) return this
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<ImportTower>()
        towers.forEach { t ->
            val key = "${"%.6f".format(t.lon)},${"%.6f".format(t.lat)}"
            if (seen.add(key)) out.add(t)
        }
        if (out.size == towers.size) return this
        return copy(towers = out.toMutableList())
    }

    private fun extractTowerNoOrNull(text: String): String? {
        if (text.isBlank()) return null
        val t = text.trim()
        // 整体就是一个编号（"0012" / "N35" / "T12" / "12-1" / "#8"）时原样保留，
        // 不要剥掉字母前缀，也不要补零/去零
        if (t.matches(Regex("""^[A-Za-z#]?\d{1,6}([-_/]\d{1,4})?$"""))) return t
        val patterns = listOf(
            Regex("""(\d+)\s*号(?:杆|塔)"""),
            Regex("""(?:#|N|T|塔|杆)\s*(\d+[A-Za-z0-9\-]*)"""),
            Regex("""^\s*(\d+[A-Za-z0-9\-]*)\s*$""")
        )
        patterns.forEach { p ->
            p.find(text)?.let { m -> return m.groupValues[1] }
        }
        return null
    }

    private fun parseCoordString(s: String): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        // KML: lon,lat[,alt] 空格分隔
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
