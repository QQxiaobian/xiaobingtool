package com.kunming.powerline.data.parser

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.kunming.powerline.util.VoltagePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 地理信息文件统一解析入口
 * 支持：.kml / .kmz / .geojson / .json / .gpx / .shp(zip)
 */
@Singleton
class GeoFileParser @Inject constructor(
    private val context: Context
) {

    suspend fun parse(uri: Uri, onProgress: (Float) -> Unit = {}): Result<ParseResult> = withContext(Dispatchers.IO) {
        try {
            val name = queryName(uri) ?: ""
            val lower = name.lowercase()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("无法读取文件"))

            val result: ParseResult = when {
                lower.endsWith(".kmz") -> parseKmz(bytes, onProgress, name)
                lower.endsWith(".kml") -> parseKml(String(bytes, Charsets.UTF_8), onProgress, name)
                lower.endsWith(".gpx") -> parseGpx(String(bytes, Charsets.UTF_8), onProgress)
                lower.endsWith(".geojson") || lower.endsWith(".json") -> parseGeoJson(String(bytes, Charsets.UTF_8), onProgress)
                lower.endsWith(".shp") || lower.endsWith(".zip") -> parseShapefileZip(bytes, onProgress)
                else -> {
                    // 按内容嗅探
                    val head = String(bytes, 0, minOf(400, bytes.size), Charsets.UTF_8).trim()
                    when {
                        head.startsWith("{") -> parseGeoJson(String(bytes, Charsets.UTF_8), onProgress)
                        head.contains("<kml", true) -> parseKml(String(bytes, Charsets.UTF_8), onProgress, name)
                        head.contains("<gpx", true) -> parseGpx(String(bytes, Charsets.UTF_8), onProgress)
                        else -> return@withContext Result.failure(Exception("不支持的文件格式：$name"))
                    }
                }
            }
            Result.success(mergeKmlByFile(result.copy(format = formatOf(lower, result.format)), name))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * KML / KMZ 文件的后处理（**不改名、不重编号**）：
     * - 线路名沿用文件内部名称（Folder / Document / Placemark），
     *   只有名称为空或明显是占位符时才用文件名兜底
     * - 电压等级：文件内没有写时按文件名识别（"220kV" / "220千伏" / "500kV线路" ...）
     * - 杆塔编号保持原样，绝不重排
     */
    private fun mergeKmlByFile(result: ParseResult, fileName: String): ParseResult {
        val lowerName = fileName.lowercase()
        val isKml = lowerName.endsWith(".kml") || lowerName.endsWith(".kmz") ||
            (lowerName.isBlank() && result.format == "kml")
        if (!isKml || result.lines.isEmpty()) return result
        val baseName = fileName.trim()
            .removeSuffix(".kml").removeSuffix(".kmz")
            .removeSuffix(".KML").removeSuffix(".KMZ")
            .substringBeforeLast(".kml").substringBeforeLast(".kmz")
        val fileVoltage = VoltagePalette.parseLevel(baseName)
        val multi = result.lines.size > 1

        val fixed = result.lines.mapIndexed { i, l ->
            val placeholder = l.name.isBlank() || l.name == "未命名线路"
            val name = when {
                !placeholder -> l.name
                baseName.isNotBlank() && !multi -> baseName
                baseName.isNotBlank() -> "${baseName}_${i + 1}"
                else -> "未命名线路"
            }
            // 只有文件内确实没有电压信息（解析成默认 110 且名称里也没有）时才用文件名
            val voltage = if (l.voltageLevel == 110 && fileVoltage != null &&
                VoltagePalette.parseLevel(l.name) == null
            ) fileVoltage else l.voltageLevel
            l.copy(name = name, voltageLevel = voltage)
        }
        return result.copy(lines = fixed)
    }

    private fun formatOf(name: String, fallback: String): String = when {
        name.endsWith(".kmz") || name.endsWith(".kml") -> "kml"
        name.endsWith(".gpx") -> "gpx"
        name.endsWith(".geojson") || name.endsWith(".json") -> "geojson"
        name.endsWith(".shp") || name.endsWith(".zip") -> "shp"
        else -> fallback
    }

    /**
     * 批量导入：解析多个地理信息文件，结果合并。
     * 用于多选文件场景：线路名按文件来源去重合并。
     */
    suspend fun parseMultiple(uris: List<Uri>, onProgress: (Float) -> Unit = {}): Result<ParseResult> =
        withContext(Dispatchers.IO) {
            val allLines = LinkedHashMap<String, ImportLine>()
            val allErrors = mutableListOf<ImportError>()
            var lastFormat = ""
            uris.forEachIndexed { idx, uri ->
                val r = parse(uri) { pct -> onProgress((idx + pct) / uris.size) }
                r.onSuccess { pr ->
                    pr.lines.forEach { il ->
                        // 同名线路（同一条线路拆成多个文件）才合并；
                        // 合并时保留各自文件里的原始编号，不重新排号
                        val existing = allLines[il.name]
                        if (existing != null) {
                            existing.towers.addAll(il.towers)
                            if (existing.lineCoords.isEmpty()) existing.lineCoords.addAll(il.lineCoords)
                        } else {
                            allLines[il.name] = il
                        }
                    }
                    allErrors.addAll(pr.errors)
                    if (pr.format.isNotBlank()) lastFormat = pr.format
                }.onFailure { allErrors.add(ImportError(-1, "文件 $idx 解析失败：${it.message}")) }
            }
            val list = allLines.values.toList()
            val (valid, verr) = ImportValidator.validate(list, allowDuplicateNumbers = true)
            allErrors.addAll(verr)
            Result.success(ParseResult(valid, allErrors, ImportValidator.detectSameTower(valid), lastFormat.ifBlank { "multi" }))
        }

    // ==================================================================
    // KML
    // ==================================================================
    /**
     * KML 解析委托给 [KmlParser]（独立 object，便于单元测试）。
     */
    suspend fun parseKml(xml: String, onProgress: (Float) -> Unit = {}, fileName: String = ""): ParseResult =
        KmlParser.parse(xml, onProgress, fileName)

    // ==================================================================
    // KMZ
    // ==================================================================
    private suspend fun parseKmz(bytes: ByteArray, onProgress: (Float) -> Unit, fileName: String = ""): ParseResult {
        var kml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".kml", true)) {
                    kml = zis.readBytes().toString(Charsets.UTF_8)
                    break
                }
                entry = zis.nextEntry
            }
        }
        return if (kml != null) {
            val r = parseKml(kml!!, onProgress, fileName)
            r.copy(format = "kml")
        } else {
            ParseResult(emptyList(), listOf(ImportError(-1, "KMZ 中没有找到 .kml 文件")), emptyList(), "kml")
        }
    }

    // ==================================================================
    // GPX
    // ==================================================================
    suspend fun parseGpx(xml: String, onProgress: (Float) -> Unit = {}): ParseResult {
        val lines = LinkedHashMap<String, ImportLine>()
        val errors = mutableListOf<ImportError>()
        var currentTrackName = ""
        var currentWptName = ""
        var currentWptDesc = ""
        var lat = 0.0
        var lon = 0.0
        var elev: Double? = null
        var insideTrkpt = false
        var trackLine: ImportLine? = null
        var seq = 0

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(xml.toByteArray()), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                        "trk" -> {
                            val t = lines.getOrPut("GPX 轨迹 ${lines.size + 1}") {
                                ImportLine("GPX 轨迹 ${lines.size + 1}", 110)
                            }
                            trackLine = t
                        }
                        "name" -> {
                            val n = parser.nextText().trim()
                            if (n.isNotBlank()) {
                                if (insideTrkpt) currentWptName = n else currentTrackName = n
                                val tl = trackLine
                                if (tl != null && tl.towers.isEmpty()) {
                                    val key = lines.keys.firstOrNull { lines[it] === tl }
                                    if (key != null && key != n) {
                                        val v = lines.remove(key)!!
                                        val renamed = v.copy(name = n)
                                        lines[n] = renamed
                                        trackLine = renamed
                                    }
                                }
                            }
                        }
                        "wpt" -> {
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            currentWptName = ""
                            currentWptDesc = ""
                            elev = null
                        }
                        "trkpt" -> {
                            insideTrkpt = true
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        }
                        "ele" -> {
                            val v = parser.nextText().trim().toDoubleOrNull()
                            elev = v
                        }
                        "desc", "cmt" -> {
                            val d = parser.nextText().trim()
                            if (!insideTrkpt) currentWptDesc = d
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name.lowercase()) {
                        "wpt" -> {
                            seq++
                            val lineName = "GPX 航点"
                            val line = lines.getOrPut(lineName) { ImportLine(lineName, 110) }
                            line.towers.add(
                                ImportTower(
                                    towerNumber = currentWptName.ifBlank { "W$seq" },
                                    lon = lon, lat = lat, elevation = elev,
                                    remark = currentWptDesc.ifBlank { null }, sourceRow = seq
                                )
                            )
                        }
                        "trkpt" -> {
                            insideTrkpt = false
                            trackLine?.let { t ->
                                t.towers.add(
                                    ImportTower(
                                        towerNumber = "P${t.towers.size + 1}",
                                        lon = lon, lat = lat, elevation = elev, sourceRow = seq
                                    )
                                )
                            }
                        }
                        "trk" -> trackLine = null
                    }
                }
                event = parser.next()
            }
            onProgress(1f)
        } catch (t: Throwable) {
            errors.add(ImportError(-1, "GPX 解析异常：${t.message}"))
        }

        val (valid, verr) = ImportValidator.validate(lines.values.toList(), allowDuplicateNumbers = true)
        errors.addAll(verr)
        return ParseResult(valid, errors, emptyList(), "gpx")
    }

    // ==================================================================
    // GeoJSON
    // ==================================================================
    suspend fun parseGeoJson(json: String, onProgress: (Float) -> Unit = {}): ParseResult {
        val lines = LinkedHashMap<String, ImportLine>()
        val errors = mutableListOf<ImportError>()
        try {
            val root = JSONObject(json)
            val features = when (root.optString("type")) {
                "FeatureCollection" -> root.optJSONArray("features") ?: JSONArray()
                "Feature" -> JSONArray().put(root)
                else -> JSONArray()
            }
            for (i in 0 until features.length()) {
                if (i % 100 == 0) onProgress(i.toFloat() / features.length().coerceAtLeast(1))
                val f = features.optJSONObject(i) ?: continue
                val props = f.optJSONObject("properties") ?: JSONObject()
                val geom = f.optJSONObject("geometry") ?: continue
                val name = props.optString("name").ifBlank { props.optString("NAME").ifBlank { "未命名线路" } }
                val voltageStr = props.optString("voltage").ifBlank { props.optString("VOLTAGE") }
                val voltage = VoltagePalette.parseLevel(voltageStr.ifBlank { name }) ?: 110
                val towerNo = props.optString("tower_number")
                    .ifBlank { props.optString("tower").ifBlank { props.optString("杆塔号") } }

                val line = lines.getOrPut(name) {
                    ImportLine(name, voltage, remark = props.optString("description").ifBlank { null })
                }
                when (geom.optString("type")) {
                    "Point" -> {
                        val c = geom.optJSONArray("coordinates") ?: continue
                        val lon = c.optDouble(0, Double.NaN)
                        val lat = c.optDouble(1, Double.NaN)
                        if (lon.isNaN() || lat.isNaN()) continue
                        line.towers.add(
                            ImportTower(
                                towerNumber = towerNo.ifBlank { "T${line.towers.size + 1}" },
                                lon = lon, lat = lat,
                                elevation = if (c.length() > 2) c.optDouble(2, Double.NaN).takeIf { !it.isNaN() } else null,
                                sourceRow = i + 1
                            )
                        )
                    }
                    "MultiPoint" -> {
                        val cs = geom.optJSONArray("coordinates") ?: continue
                        for (k in 0 until cs.length()) {
                            val c = cs.optJSONArray(k) ?: continue
                            line.towers.add(
                                ImportTower("T${line.towers.size + 1}", c.optDouble(0), c.optDouble(1), sourceRow = i + 1)
                            )
                        }
                    }
                    "LineString" -> {
                        val cs = geom.optJSONArray("coordinates") ?: continue
                        for (k in 0 until cs.length()) {
                            val c = cs.optJSONArray(k) ?: continue
                            val lon = c.optDouble(0)
                            val lat = c.optDouble(1)
                            line.lineCoords.add(lon to lat)
                            line.towers.add(ImportTower("P${k + 1}", lon, lat, sourceRow = i + 1))
                        }
                    }
                    "MultiLineString", "Polygon", "MultiPolygon" -> {
                        // 展平为顺序点
                        flattenCoords(geom.optJSONArray("coordinates")).forEachIndexed { k, p ->
                            line.towers.add(ImportTower("P${k + 1}", p.first, p.second, sourceRow = i + 1))
                        }
                    }
                }
            }
            onProgress(1f)
        } catch (t: Throwable) {
            errors.add(ImportError(-1, "GeoJSON 解析异常：${t.message}"))
        }
        val (valid, verr) = ImportValidator.validate(lines.values.toList(), allowDuplicateNumbers = true)
        errors.addAll(verr)
        return ParseResult(valid, errors, emptyList(), "geojson")
    }

    private fun flattenCoords(arr: JSONArray?): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val item = arr.optJSONArray(i) ?: continue
            if (item.length() >= 2 && item.opt(0) is Number && item.opt(1) is Number) {
                out.add(item.optDouble(0) to item.optDouble(1))
            } else {
                out.addAll(flattenCoords(item))
            }
        }
        return out
    }

    // ==================================================================
    // Shapefile（zip 包：.shp + .dbf + .shx + .prj）
    // ==================================================================
    private suspend fun parseShapefileZip(bytes: ByteArray, onProgress: (Float) -> Unit): ParseResult {
        var shp: ByteArray? = null
        var dbf: ByteArray? = null
        var prj: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                when {
                    e.name.endsWith(".shp", true) -> shp = zis.readBytes()
                    e.name.endsWith(".dbf", true) -> dbf = zis.readBytes()
                    e.name.endsWith(".prj", true) -> prj = zis.readBytes().toString(Charsets.UTF_8)
                }
                e = zis.nextEntry
            }
        }
        if (shp == null) {
            return ParseResult(emptyList(), listOf(ImportError(-1, "压缩包中未找到 .shp 文件")), emptyList(), "shp")
        }
        return ShapefileReader.read(shp!!, dbf, prj, onProgress)
    }

    // ==================================================================
    // 专题图层（点 / 线 / 面，通用导入）
    // ==================================================================
    /**
     * 把地理信息文件解析成"专题图层"：保留原始点、线、面，不再解释成线路+杆塔。
     */
    suspend fun parseThematic(uri: Uri): Result<ThematicParseResult> = withContext(Dispatchers.IO) {
        try {
            val fileName = queryName(uri) ?: ""
            val lower = fileName.lowercase()
            val baseName = fileName.substringBeforeLast('.')
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("无法读取文件"))

            val result = when {
                lower.endsWith(".shp") -> {
                    ShapefileThematicReader.read(bytes, null, null, baseName)
                }
                lower.endsWith(".zip") -> {
                    val parts = unzipShapefile(bytes)
                    if (parts.shp == null) {
                        ThematicParseResult(
                            emptyList(),
                            listOf(ImportError(-1, "压缩包中未找到 .shp 文件（请把 .shp/.dbf/.prj 一起打包）")),
                            emptyList(), "shp"
                        )
                    } else {
                        ShapefileThematicReader.read(
                            parts.shp, parts.dbf, parts.prj,
                            baseName.ifBlank { parts.name },
                            { }
                        )
                    }
                }
                lower.endsWith(".kmz") -> {
                    val kml = unzipKml(bytes)
                    if (kml == null) {
                        ThematicParseResult(
                            emptyList(), listOf(ImportError(-1, "KMZ 中没有找到 .kml 文件")),
                            emptyList(), "kml"
                        )
                    } else ThematicParser.parseKml(kml, baseName)
                }
                lower.endsWith(".kml") -> ThematicParser.parseKml(String(bytes, Charsets.UTF_8), baseName)
                lower.endsWith(".gpx") -> ThematicParser.parseGpx(String(bytes, Charsets.UTF_8), baseName)
                lower.endsWith(".geojson") || lower.endsWith(".json") ->
                    ThematicParser.parseGeoJson(String(bytes, Charsets.UTF_8), baseName)
                else -> {
                    val head = String(bytes, 0, minOf(400, bytes.size), Charsets.UTF_8).trim()
                    when {
                        head.startsWith("{") -> ThematicParser.parseGeoJson(String(bytes, Charsets.UTF_8), baseName)
                        head.contains("<kml", true) -> ThematicParser.parseKml(String(bytes, Charsets.UTF_8), baseName)
                        head.contains("<gpx", true) -> ThematicParser.parseGpx(String(bytes, Charsets.UTF_8), baseName)
                        bytes.size > 100 && isShapefile(bytes) ->
                            ShapefileThematicReader.read(bytes, null, null, baseName)
                        bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() -> {
                            // PK 头：可能是打包的 shp
                            val parts = unzipShapefile(bytes)
                            if (parts.shp != null) {
                                ShapefileThematicReader.read(parts.shp, parts.dbf, parts.prj, baseName.ifBlank { parts.name })
                            } else {
                                return@withContext Result.failure(Exception("压缩包中没有找到 .shp 文件：$fileName"))
                            }
                        }
                        else -> return@withContext Result.failure(Exception("该格式暂不支持导入专题图层：$fileName"))
                    }
                }
            }
            Result.success(result)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** 批量导入专题图层：多个文件各自成层 */
    suspend fun parseThematicMultiple(
        uris: List<Uri>,
        onProgress: (Float) -> Unit = {}
    ): Result<ThematicParseResult> = withContext(Dispatchers.IO) {
        val layers = mutableListOf<ImportLayer>()
        val errors = mutableListOf<ImportError>()
        val warnings = mutableListOf<String>()
        var format = ""
        uris.forEachIndexed { idx, uri ->
            onProgress(idx.toFloat() / uris.size.coerceAtLeast(1))
            parseThematic(uri).onSuccess { r ->
                layers.addAll(r.layers)
                errors.addAll(r.errors)
                warnings.addAll(r.warnings)
                if (r.format.isNotBlank()) format = r.format
            }.onFailure { errors.add(ImportError(-1, "文件 ${idx + 1} 解析失败：${it.message}")) }
        }
        onProgress(1f)
        Result.success(ThematicParseResult(layers, errors, warnings, format.ifBlank { "multi" }))
    }

    private data class ShpParts(
        val shp: ByteArray?,
        val dbf: ByteArray?,
        val prj: String?,
        val name: String
    )

    /** 从 zip 中取出 shp / dbf / prj（同名前缀的第一组） */
    private fun unzipShapefile(bytes: ByteArray): ShpParts {
        var shp: ByteArray? = null
        var dbf: ByteArray? = null
        var prj: String? = null
        var name = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                when {
                    e.name.endsWith(".shp", true) && shp == null -> {
                        shp = zis.readBytes()
                        name = e.name.substringBeforeLast('/').substringAfterLast('/')
                            .substringBeforeLast('.')
                    }
                    e.name.endsWith(".dbf", true) && dbf == null -> dbf = zis.readBytes()
                    e.name.endsWith(".prj", true) && prj == null -> prj = zis.readBytes().toString(Charsets.UTF_8)
                }
                e = zis.nextEntry
            }
        }
        return ShpParts(shp, dbf, prj, name)
    }

    /** shapefile 文件码固定 9994（大端），用于内容嗅探 */
    fun isShapefile(bytes: ByteArray): Boolean =
        bytes.size > 100 && bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
            bytes[2] == 0x27.toByte() && bytes[3] == 0x0A.toByte()

    private fun unzipKml(bytes: ByteArray): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".kml", true)) {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun queryName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment
            }
        } catch (t: Throwable) {
            uri.lastPathSegment
        }
    }
}
/**
 * 轻量 Shapefile 读取器（不依赖 GeoTools）
 * - 支持 Point / PolyLine(Polyline) / Polygon 主类型
 * - 读取 .dbf 属性表
 * - 投影：WGS84 / CGCS2000 直接使用；其他（如西安80、Web Mercator）按 .prj 做粗略处理
 */
object ShapefileReader {

    fun read(shp: ByteArray, dbf: ByteArray?, prj: String?, onProgress: (Float) -> Unit = {}): ParseResult {
        val errors = mutableListOf<ImportError>()
        val lines = LinkedHashMap<String, ImportLine>()
        try {
            val din = DataInputStream(ByteArrayInputStream(shp))
            // 文件头 100 字节：跳过文件码与未用字段，取 offset 32 处的几何类型（小端）
            din.skipBytes(28)
            val shpType = Integer.reverseBytes(din.readInt())
            din.skipBytes(100 - 32)

            val records = mutableListOf<Pair<Int, List<Pair<Double, Double>>>>()
            while (din.available() >= 8) {
                /* val recNo = */ din.readInt()          // 大端
                val contentLen = din.readInt() * 2       // 大端，单位为 16 位字
                if (contentLen <= 0 || din.available() < contentLen) break
                val content = ByteArray(contentLen)
                din.readFully(content)
                val c = DataInputStream(ByteArrayInputStream(content))
                val type = Integer.reverseBytes(c.readInt())
                when (type) {
                    1 -> { // Point
                        val x = Double.fromBits(java.lang.Long.reverseBytes(c.readLong()))
                        val y = Double.fromBits(java.lang.Long.reverseBytes(c.readLong()))
                        records.add(records.size to listOf(x to y))
                    }
                    11, 21, 28 -> { // PointZ / PointM / PointM
                        val x = Double.fromBits(java.lang.Long.reverseBytes(c.readLong()))
                        val y = Double.fromBits(java.lang.Long.reverseBytes(c.readLong()))
                        records.add(records.size to listOf(x to y))
                    }
                    3, 13, 23, 5, 15, 25 -> { // PolyLine / Polygon
                        val tmp = DataInputStream(ByteArrayInputStream(content))
                        tmp.skipBytes(4 + 32) // type + box
                        val numParts = Integer.reverseBytes(tmp.readInt())
                        val nPts = Integer.reverseBytes(tmp.readInt())
                        tmp.skipBytes(4 * numParts.coerceAtLeast(0))
                        val pts = mutableListOf<Pair<Double, Double>>()
                        for (k in 0 until nPts) {
                            if (tmp.available() < 16) break
                            val x = Double.fromBits(java.lang.Long.reverseBytes(tmp.readLong()))
                            val y = Double.fromBits(java.lang.Long.reverseBytes(tmp.readLong()))
                            pts.add(x to y)
                        }
                        records.add(records.size to pts)
                    }
                    else -> records.add(records.size to emptyList())
                }
            }

            val attrs = dbf?.let { DbfReader.read(it) } ?: emptyList()
            val needsMercator = prj?.contains("Mercator", true) == true
            val isGk = prj?.let { it.contains("Gauss_Kruger", true) || it.contains("Xi'an", true) || it.contains("Xian_1980", true) } == true

            records.forEachIndexed { i, (_, pts) ->
                if (i % 200 == 0) onProgress(i.toFloat() / records.size.coerceAtLeast(1))
                val a = attrs.getOrNull(i) ?: emptyMap()
                val name = pick(a, "name", "NAME", "线路名称", "线路名", "LINE_NAME") ?: "SHP 图层 ${i + 1}"
                val voltage = VoltagePalette.parseLevel(
                    pick(a, "voltage", "VOLTAGE", "电压等级", "电压") ?: name
                ) ?: 110
                val towerNo = pick(a, "tower_number", "TOWER_NO", "杆塔号", "杆号")

                val line = lines.getOrPut(name) { ImportLine(name, voltage) }
                pts.forEachIndexed { k, p ->
                    var (x, y) = p
                    if (needsMercator) {
                        val ll = mercatorToLonLat(x, y)
                        x = ll.first; y = ll.second
                    } else if (isGk && kotlin.math.abs(x) > 1000) {
                        // 高斯投影坐标（米），按中央子午线近似反算
                        val ll = gaussKrugerToLonLat(x, y, a["_zone"]?.toIntOrNull())
                        x = ll.first; y = ll.second
                    }
                    line.towers.add(
                        ImportTower(
                            towerNumber = if (pts.size == 1) (towerNo ?: "T${i + 1}") else (towerNo ?: "P${k + 1}"),
                            lon = x, lat = y,
                            sourceRow = i + 1,
                            remark = pick(a, "remark", "备注")
                        )
                    )
                }
            }
            onProgress(1f)
        } catch (t: Throwable) {
            errors.add(ImportError(-1, "Shapefile 解析异常：${t.message}"))
        }
        val (valid, verr) = ImportValidator.validate(lines.values.toList(), allowDuplicateNumbers = true)
        errors.addAll(verr)
        return ParseResult(valid, errors, emptyList(), "shp")
    }

    private fun pick(a: Map<String, String>, vararg keys: String): String? {
        keys.forEach { k ->
            a.entries.firstOrNull { it.key.equals(k, true) }?.value?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    /** Web Mercator -> WGS84 */
    fun mercatorToLonLat(x: Double, y: Double): Pair<Double, Double> {
        val lon = x / 20037508.34 * 180.0
        var lat = y / 20037508.34 * 180.0
        lat = 180 / Math.PI * (2 * kotlin.math.atan(kotlin.math.exp(lat * Math.PI / 180)) - Math.PI / 2)
        return lon to lat
    }

    /** 高斯-克吕格（CGCS2000 / 西安80 近似）-> WGS84 经纬度 */
    fun gaussKrugerToLonLat(x: Double, y: Double, zone: Int?): Pair<Double, Double> {
        val aM = 6378137.0
        val f = 1 / 298.257222101
        val e2 = 2 * f - f * f
        var yy = y
        var zoneNo = zone ?: 0
        if (zoneNo == 0) {
            zoneNo = if (yy > 1_000_000) (yy / 1_000_000).toInt() else ((yy % 1000) + 0).toInt()
            if (yy > 1_000_000) yy -= zoneNo * 1_000_000 + 500000
        } else {
            yy -= 500000.0
        }
        var l = zoneNo.coerceIn(13, 23) * 6.0 - 3.0
        if (zoneNo < 13) l = 105.0
        val k0 = 0.9996
        val e1 = (1 - kotlin.math.sqrt(1 - e2)) / (1 + kotlin.math.sqrt(1 - e2))
        val m = x / k0
        val mu = m / (aM * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256))
        val phi1 = mu + (3 * e1 / 2 - 27 * e1 * e1 * e1 / 32) * kotlin.math.sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1 * e1 * e1 * e1 / 32) * kotlin.math.sin(4 * mu) +
            (151 * e1 * e1 * e1 / 96) * kotlin.math.sin(6 * mu)
        val sinPhi = kotlin.math.sin(phi1)
        val cosPhi = kotlin.math.cos(phi1)
        val tanPhi = kotlin.math.tan(phi1)
        val n1 = aM / kotlin.math.sqrt(1 - e2 * sinPhi * sinPhi)
        val t1 = tanPhi * tanPhi
        val c1 = e2 / (1 - e2) * cosPhi * cosPhi
        val r1 = aM * (1 - e2) / Math.pow(1 - e2 * sinPhi * sinPhi, 1.5)
        val d = yy / (n1 * k0)
        val latRad = phi1 - (n1 * tanPhi / r1) * (
            d * d / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * e2 / (1 - e2)) * d * d * d * d / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * e2 / (1 - e2) - 3 * c1 * c1) * d * d * d * d * d * d / 720
            )
        val lonRad = Math.toRadians(l) + (
            d - (1 + 2 * t1 + c1) * d * d * d / 6 +
                (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * e2 / (1 - e2) + 24 * t1 * t1) * d * d * d * d * d / 120
            ) / cosPhi
        return Math.toDegrees(lonRad) to Math.toDegrees(latRad)
    }
}

/**
 * DBF 属性表读取（dBASE III/IV）
 */
object DbfReader {
    fun read(bytes: ByteArray): List<Map<String, String>> {
        if (bytes.size < 32) return emptyList()
        val din = DataInputStream(ByteArrayInputStream(bytes))
        din.skipBytes(4)
        val numRecords = Integer.reverseBytes(din.readInt())
        val headerLen = java.lang.Short.reverseBytes(din.readShort()).toInt()
        val recordLen = java.lang.Short.reverseBytes(din.readShort()).toInt()
        din.skipBytes(20)

        // 字段描述区：每项 32 字节 = 名称(11) + 类型(1) + 偏移(4) + 长度(1) + 小数位(1) + 保留(14)
        // 以单个 0x0D 结束（不是完整的 32 字节），所以必须逐项按 32 字节对齐读取
        val fields = mutableListOf<Triple<String, Char, Int>>()
        while (din.available() >= 32) {
            val nameBytes = ByteArray(11)
            din.readFully(nameBytes)
            if (nameBytes[0] == 0x0D.toByte()) break
            val name = String(nameBytes, Charsets.ISO_8859_1).trim('\u0000', ' ')
            val type = din.read().toChar()
            din.skipBytes(4)
            val len = din.read().toInt() and 0xFF
            din.skipBytes(15)
            if (name.isNotBlank() && len > 0) fields.add(Triple(name, type, len))
        }

        val out = mutableListOf<Map<String, String>>()
        val dataStart = headerLen
        var pos = dataStart
        for (r in 0 until numRecords) {
            if (pos + recordLen > bytes.size) break
            val deleted = bytes[pos] == '*'.code.toByte()
            var p = pos + 1
            val map = HashMap<String, String>()
            if (!deleted) {
                fields.forEach { (n, _, len) ->
                    val raw = String(bytes, p, minOf(len, bytes.size - p), Charsets.ISO_8859_1).trim()
                    map[n] = decodeGbk(raw)
                    p += len
                }
                out.add(map)
            }
            pos += recordLen
        }
        return out
    }

    /** DBF 中中文通常是 GBK 编码 */
    private fun decodeGbk(s: String): String = try {
        val bytes = s.toByteArray(Charsets.ISO_8859_1)
        String(bytes, charset("GBK")).trim()
    } catch (t: Throwable) {
        s
    }
}
