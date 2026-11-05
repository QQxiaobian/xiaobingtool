package com.kunming.powerline.data.parser

import android.content.Context
import android.net.Uri
import android.util.Xml
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
                lower.endsWith(".kmz") -> parseKmz(bytes, onProgress)
                lower.endsWith(".kml") -> parseKml(String(bytes, Charsets.UTF_8), onProgress)
                lower.endsWith(".gpx") -> parseGpx(String(bytes, Charsets.UTF_8), onProgress)
                lower.endsWith(".geojson") || lower.endsWith(".json") -> parseGeoJson(String(bytes, Charsets.UTF_8), onProgress)
                lower.endsWith(".shp") || lower.endsWith(".zip") -> parseShapefileZip(bytes, onProgress)
                else -> {
                    // 按内容嗅探
                    val head = String(bytes, 0, minOf(400, bytes.size), Charsets.UTF_8).trim()
                    when {
                        head.startsWith("{") -> parseGeoJson(String(bytes, Charsets.UTF_8), onProgress)
                        head.contains("<kml", true) -> parseKml(String(bytes, Charsets.UTF_8), onProgress)
                        head.contains("<gpx", true) -> parseGpx(String(bytes, Charsets.UTF_8), onProgress)
                        else -> return@withContext Result.failure(Exception("不支持的文件格式：$name"))
                    }
                }
            }
            Result.success(result.copy(format = formatOf(lower, result.format)))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun formatOf(name: String, fallback: String): String = when {
        name.endsWith(".kmz") || name.endsWith(".kml") -> "kml"
        name.endsWith(".gpx") -> "gpx"
        name.endsWith(".geojson") || name.endsWith(".json") -> "geojson"
        name.endsWith(".shp") || name.endsWith(".zip") -> "shp"
        else -> fallback
    }

    // ==================================================================
    // KML
    // ==================================================================
    /**
     * KML 解析：
     * - <Placemark> 的 <name>/<description> 映射线路名 / 杆塔号 / 备注
     * - <LineString> -> 线路几何
     * - <Point> -> 杆塔
     */
    suspend fun parseKml(xml: String, onProgress: (Float) -> Unit = {}): ParseResult {
        val lines = LinkedHashMap<String, ImportLine>()
        val errors = mutableListOf<ImportError>()
        var placemarkName = ""
        var placemarkDesc = ""
        var currentVoltage: Int? = null
        var geomType: String? = null
        val coordsBuffer = StringBuilder()
        var seq = 0

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(xml.toByteArray()), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase()) {
                            "placemark" -> {
                                placemarkName = ""
                                placemarkDesc = ""
                                currentVoltage = null
                            }
                            "lineString" -> { geomType = "line"; coordsBuffer.clear() }
                            "point" -> { geomType = "point"; coordsBuffer.clear() }
                            "polygon" -> { geomType = "polygon"; coordsBuffer.clear() }
                            "name" -> if (parser.nextText().isNotBlank()) placemarkName = parser.text.trim()
                            "description" -> placemarkDesc = parser.nextText().trim()
                            "coordinates" -> {
                                coordsBuffer.append(parser.nextText().trim())
                                handleGeometry(
                                    geomType, coordsBuffer.toString(), placemarkName, placemarkDesc,
                                    currentVoltage, lines, errors, ++seq
                                )
                                geomType = null
                            }
                        }
                        // ExtendedData 中的 voltage
                        if (parser.name.equals("Data", true)) {
                            val k = parser.getAttributeValue(null, "name")
                            if (k != null && (k.contains("电压") || k.equals("voltage", true))) {
                                val p2 = parser
                                // 继续读 value 子标签
                                var e2 = p2.next()
                                while (e2 != XmlPullParser.END_TAG || p2.name != "Data") {
                                    if (e2 == XmlPullParser.START_TAG && p2.name.equals("value", true)) {
                                        currentVoltage = com.kunming.powerline.util.VoltagePalette
                                            .parseLevel(p2.nextText())
                                    }
                                    e2 = p2.next()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("Placemark", true)) {
                            // 如果 placemark 内没有几何但 name 有值，忽略
                        }
                    }
                }
                event = parser.next()
            }
            onProgress(1f)
        } catch (t: Throwable) {
            errors.add(ImportError(-1, "KML 解析异常：${t.message}"))
        }

        val list = lines.values.toList()
        val (valid, verr) = ImportValidator.validate(list)
        errors.addAll(verr)
        return ParseResult(valid, errors, ImportValidator.detectSameTower(valid), "kml")
    }

    private fun handleGeometry(
        type: String?,
        coords: String,
        name: String,
        desc: String,
        voltage: Int?,
        lines: MutableMap<String, ImportLine>,
        errors: MutableList<ImportError>,
        seq: Int
    ) {
        val pts = parseCoordString(coords)
        if (pts.isEmpty()) return
        when (type) {
            "point" -> {
                val (lon, lat) = pts.first()
                val lineName = extractLineName(name, desc)
                val line = lines.getOrPut(lineName) {
                    ImportLine(
                        name = lineName,
                        voltageLevel = voltage ?: com.kunming.powerline.util.VoltagePalette.parseLevel(lineName) ?: 110
                    )
                }
                val towerNo = extractTowerNo(name, desc) ?: "T${line.towers.size + 1}"
                line.towers.add(
                    ImportTower(
                        towerNumber = towerNo,
                        lon = lon, lat = lat, remark = desc.ifBlank { null }, sourceRow = seq
                    )
                )
            }
            "line", "polygon" -> {
                val lineName = extractLineName(name, desc)
                val line = lines.getOrPut(lineName) {
                    ImportLine(
                        name = lineName,
                        voltageLevel = voltage ?: com.kunming.powerline.util.VoltagePalette.parseLevel(lineName) ?: 110
                    )
                }
                if (line.lineCoords.isEmpty()) {
                    line.lineCoords.addAll(pts)
                    // 线转杆塔（若没有 Point）
                    if (line.towers.isEmpty()) {
                        pts.forEachIndexed { i, p ->
                            line.towers.add(
                                ImportTower(
                                    towerNumber = "P${i + 1}",
                                    lon = p.first, lat = p.second, sourceRow = seq
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun extractLineName(name: String, desc: String): String {
        // 常见命名："220kV某某线 12号塔"
        val text = name.ifBlank { desc }
        val m = Regex("""^(.*?\d+\s*(?:kV|KV|kv|千伏)?.*?线)""").find(text)
        if (m != null) return m.groupValues[1].trim()
        return if (text.length > 24) text.take(24) else text.ifBlank { "未命名线路" }
    }

    private fun extractTowerNo(name: String, desc: String): String? {
        val text = "$name $desc"
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

    // ==================================================================
    // KMZ
    // ==================================================================
    private suspend fun parseKmz(bytes: ByteArray, onProgress: (Float) -> Unit): ParseResult {
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
            val r = parseKml(kml!!, onProgress)
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

        val (valid, verr) = ImportValidator.validate(lines.values.toList())
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
                val voltage = com.kunming.powerline.util.VoltagePalette.parseLevel(voltageStr.ifBlank { name }) ?: 110
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
        val (valid, verr) = ImportValidator.validate(lines.values.toList())
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
            din.skipBytes(24) // 文件头 100 字节，读 24 后取 shapeType
            val shpType = Integer.reverseBytes(din.readInt())
            din.skipBytes(100 - 28)

            val records = mutableListOf<Pair<Int, List<Pair<Double, Double>>>>()
            while (din.available() >= 8) {
                /* val recNo = */ Integer.reverseBytes(din.readInt())
                val contentLen = Integer.reverseBytes(din.readInt()) * 2
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
                val voltage = com.kunming.powerline.util.VoltagePalette.parseLevel(
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
        val (valid, verr) = ImportValidator.validate(lines.values.toList())
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

        val fields = mutableListOf<Triple<String, Char, Int>>()
        var b = din.read()
        while (b != 0x0D && din.available() > 0) {
            val nameBytes = ByteArray(11)
            din.readFully(nameBytes)
            val name = String(nameBytes, Charsets.ISO_8859_1).trim('\u0000', ' ')
            val type = din.read().toChar()
            din.skipBytes(4)
            val len = din.read().toInt() and 0xFF
            din.skipBytes(15)
            fields.add(Triple(name, type, len))
            b = din.read()
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
