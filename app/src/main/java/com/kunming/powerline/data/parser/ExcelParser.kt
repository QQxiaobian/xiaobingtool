package com.kunming.powerline.data.parser

import android.content.Context
import android.net.Uri
import com.kunming.powerline.util.VoltagePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导入模板列定义
 */
object TemplateColumns {
    const val LINE_NAME = "线路名称"
    const val VOLTAGE = "电压等级"
    const val START_TOWER = "起始杆塔号"
    const val END_TOWER = "终止杆塔号"
    const val TOWER_NO = "杆塔号"
    const val LON = "经度"
    const val LAT = "纬度"
    const val ELEVATION = "海拔"
    const val TOWER_TYPE = "杆塔类型"
    const val CIRCUIT_COUNT = "回路数"
    const val SAME_TOWER = "同塔多回标识"

    val all = listOf(
        LINE_NAME, VOLTAGE, START_TOWER, END_TOWER, TOWER_NO, LON, LAT,
        ELEVATION, TOWER_TYPE, CIRCUIT_COUNT, SAME_TOWER
    )

    /** 常见别名，用于自动映射 */
    val aliases: Map<String, List<String>> = mapOf(
        LINE_NAME to listOf("线路名称", "线路名", "line_name", "linename", "线路", "名称"),
        VOLTAGE to listOf("电压等级", "电压", "voltage", "电压(kv)", "电压等级(kv)", "voltage_level"),
        START_TOWER to listOf("起始杆塔号", "起始杆号", "start_tower", "起始杆塔"),
        END_TOWER to listOf("终止杆塔号", "终止杆号", "end_tower", "终止杆塔"),
        TOWER_NO to listOf("杆塔号", "杆号", "塔号", "tower_number", "tower", "杆塔编号", "塔位号"),
        LON to listOf("经度", "lon", "longitude", "x", "东经", "经度(°)"),
        LAT to listOf("纬度", "lat", "latitude", "y", "北纬", "纬度(°)"),
        ELEVATION to listOf("海拔", "高程", "elevation", "altitude", "海拔(m)"),
        TOWER_TYPE to listOf("杆塔类型", "塔型", "type", "杆塔型式", "塔型名称"),
        CIRCUIT_COUNT to listOf("回路数", "回数", "circuit_count", "回路"),
        SAME_TOWER to listOf("同塔多回标识", "同塔标识", "same_tower", "同塔多回")
    )
}

/**
 * 表格列映射结果（用户可手动调整）
 */
data class ColumnMapping(
    val lineName: Int = -1,
    val voltage: Int = -1,
    val towerNo: Int = -1,
    val lon: Int = -1,
    val lat: Int = -1,
    val elevation: Int = -1,
    val towerType: Int = -1,
    val circuitCount: Int = -1,
    val startTower: Int = -1,
    val endTower: Int = -1,
    val sameTower: Int = -1
) {
    val valid: Boolean get() = towerNo >= 0 && lon >= 0 && lat >= 0

    fun describe(headers: List<String>): String {
        fun n(i: Int) = if (i >= 0 && i < headers.size) headers[i] else "—"
        return "线路名:${n(lineName)}  电压:${n(voltage)}  杆塔号:${n(towerNo)}  经度:${n(lon)}  纬度:${n(lat)}"
    }
}

data class SheetPreview(
    val fileName: String,
    val sheetName: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val mapping: ColumnMapping,
    /** 原始行号（Excel 行号） */
    val rowIndices: List<Int>
)

/**
 * Excel / CSV 台账解析
 */
@Singleton
class ExcelParser @Inject constructor(
    private val context: Context
) {

    /** 读取工作簿预览（前 maxRows 行） */
    suspend fun preview(uri: Uri, maxRows: Int = 300): Result<SheetPreview> = withContext(Dispatchers.IO) {
        try {
            val name = queryName(uri) ?: "未命名"
            if (name.endsWith(".csv", true)) {
                return@withContext previewCsv(uri, name, maxRows)
            }
            val sheet = readSheet(uri) ?: return@withContext Result.failure(Exception("无法打开文件（仅支持 .xlsx / .csv，.xls 请另存为 xlsx）"))
            val headerRow = findHeaderRow(sheet) ?: 0
            val headers = sheet.rows.getOrElse(headerRow) { emptyList() }
            val rows = mutableListOf<List<String>>()
            val idx = mutableListOf<Int>()
            for (r in headerRow + 1 until sheet.rows.size) {
                if (rows.size >= maxRows) break
                val vals = sheet.rows[r]
                if (vals.isEmpty() || vals.all { it.isBlank() }) continue
                rows.add(vals)
                idx.add(sheet.rowNumbers.getOrElse(r) { r + 1 })
            }
            val mapping = autoMap(headers)
            Result.success(SheetPreview(name, sheet.name, headers, rows, mapping, idx))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun previewCsv(uri: Uri, name: String, maxRows: Int): Result<SheetPreview> {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: return Result.failure(Exception("无法读取文件"))
        val lines = text.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return Result.failure(Exception("空文件"))
        val delimiter = if (lines[0].count { it == ',' } >= lines[0].count { it == '\t' }) ',' else '\t'
        val headers = splitCsvLine(lines[0], delimiter)
        val rows = mutableListOf<List<String>>()
        val idx = mutableListOf<Int>()
        for (i in 1 until lines.size) {
            if (rows.size >= maxRows) break
            rows.add(splitCsvLine(lines[i], delimiter))
            idx.add(i + 1)
        }
        return Result.success(SheetPreview(name, "CSV", headers, rows, autoMap(headers), idx))
    }

    private fun splitCsvLine(line: String, d: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        line.forEach { c ->
            when {
                c == '"' -> inQuote = !inQuote
                c == d && !inQuote -> { out.add(sb.toString().trim()); sb.clear() }
                c == '\r' -> {}
                else -> sb.append(c)
            }
        }
        out.add(sb.toString().trim())
        return out
    }

    /** 完整解析 */
    suspend fun parse(
        uri: Uri,
        mapping: ColumnMapping,
        onProgress: (Float) -> Unit = {}
    ): Result<ParseResult> = withContext(Dispatchers.IO) {
        try {
            val name = queryName(uri) ?: ""
            val rows: List<List<String>>
            val rowIdx: List<Int>
            if (name.endsWith(".csv", true)) {
                val p = previewCsv(uri, name, Int.MAX_VALUE).getOrThrow()
                rows = p.rows
                rowIdx = p.rowIndices
            } else {
                val sheet = readSheet(uri) ?: return@withContext Result.failure(Exception("无法打开文件（仅支持 .xlsx / .csv）"))
                val headerRow = findHeaderRow(sheet) ?: 0
                val rl = mutableListOf<List<String>>()
                val il = mutableListOf<Int>()
                for (r in headerRow + 1 until sheet.rows.size) {
                    val vals = sheet.rows[r]
                    if (vals.isEmpty() || vals.all { it.isBlank() }) continue
                    rl.add(vals)
                    il.add(sheet.rowNumbers.getOrElse(r) { r + 1 })
                }
                rows = rl
                rowIdx = il
            }

            val errors = mutableListOf<ImportError>()
            val lineMap = LinkedHashMap<String, ImportLine>()

            rows.forEachIndexed { i, vals ->
                if (i % 50 == 0) onProgress(i.toFloat() / rows.size.coerceAtLeast(1))
                val rowNo = rowIdx.getOrElse(i) { i + 2 }

                fun cell(idx: Int): String = if (idx in vals.indices) vals[idx].trim() else ""

                val lineName = cell(mapping.lineName).ifBlank { "未命名线路" }
                val voltageRaw = cell(mapping.voltage)
                val voltage = VoltagePalette.parseLevel(voltageRaw)
                    ?: VoltagePalette.parseLevel(lineName)
                    ?: 110

                val towerNo = cell(mapping.towerNo)
                val lon = parseDouble(cell(mapping.lon))
                val lat = parseDouble(cell(mapping.lat))
                val elev = parseDouble(cell(mapping.elevation))
                val towerType = cell(mapping.towerType).ifBlank { null }
                val circuitCount = cell(mapping.circuitCount).toIntOrNull()
                    ?: Regex("""(\d+)\s*回""").find(cell(mapping.circuitCount))?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1

                if (lon == null || lat == null) {
                    errors.add(ImportError(rowNo, "【$lineName】坐标缺失或格式错误（经度=${cell(mapping.lon)}, 纬度=${cell(mapping.lat)}）", vals.joinToString(" | ")))
                    return@forEachIndexed
                }
                if (towerNo.isBlank()) {
                    errors.add(ImportError(rowNo, "【$lineName】杆塔号为空", vals.joinToString(" | ")))
                    return@forEachIndexed
                }

                val line = lineMap.getOrPut(lineName) {
                    ImportLine(
                        name = lineName,
                        voltageLevel = voltage,
                        startStation = null,
                        endStation = null
                    )
                }
                line.towers.add(
                    ImportTower(
                        towerNumber = towerNo,
                        lon = lon,
                        lat = lat,
                        elevation = elev,
                        type = towerType,
                        circuitCount = circuitCount,
                        circuitNo = 1,
                        sourceRow = rowNo
                    )
                )
            }

            onProgress(1f)
            val lines = lineMap.values.map { l ->
                l.copy(towers = l.towers.sortedWith(compareTowerNumbers()).toMutableList())
            }
            val (valid, verr) = ImportValidator.validate(lines)
            errors.addAll(verr)
            Result.success(
                ParseResult(
                    lines = valid,
                    errors = errors,
                    warnings = ImportValidator.detectSameTower(valid),
                    format = "excel"
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** 按杆塔号自然排序（"12" < "12-1" < "13"） */
    fun compareTowerNumbers(): Comparator<ImportTower> = Comparator { a, b ->
        val na = numericPrefix(a.towerNumber)
        val nb = numericPrefix(b.towerNumber)
        when {
            na != null && nb != null && na != nb -> na.compareTo(nb)
            else -> a.towerNumber.compareTo(b.towerNumber, ignoreCase = true)
        }
    }

    private fun numericPrefix(s: String): Int? =
        Regex("""^#?(\d+)""").find(s.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** 读取工作表（xlsx），失败返回 null */
    private fun readSheet(uri: Uri): XlsxReader.Sheet? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            try {
                XlsxReader.readFirstSheet(input)
            } catch (t: Throwable) {
                null
            }
        }
    }

    private fun findHeaderRow(sheet: XlsxReader.Sheet): Int? {
        for (r in 0 until minOf(sheet.rows.size, 11)) {
            val vals = sheet.rows[r].map { it.trim() }
            if (vals.any { it in TemplateColumns.aliases.getValue(TemplateColumns.TOWER_NO) } &&
                vals.any { it in TemplateColumns.aliases.getValue(TemplateColumns.LON) }
            ) return r
        }
        // 找不到标准表头时，若首行不像数据（含「线路/杆塔/经度」等词）则视为表头
        val first = sheet.rows.firstOrNull()?.joinToString("") ?: ""
        return if (first.contains("线路") || first.contains("杆塔")) 0 else 0
    }

    fun autoMap(headers: List<String>): ColumnMapping {
        fun find(key: String): Int {
            val alias = TemplateColumns.aliases[key].orEmpty()
            headers.forEachIndexed { i, h ->
                val norm = h.trim().lowercase().replace(" ", "").replace("（", "(").replace("）", ")")
                if (alias.any { a -> a.lowercase().replace(" ", "") == norm }) return i
            }
            // 模糊包含
            headers.forEachIndexed { i, h ->
                val norm = h.trim().lowercase()
                if (alias.any { a -> norm.contains(a.lowercase()) }) return i
            }
            return -1
        }
        return ColumnMapping(
            lineName = find(TemplateColumns.LINE_NAME),
            voltage = find(TemplateColumns.VOLTAGE),
            towerNo = find(TemplateColumns.TOWER_NO),
            lon = find(TemplateColumns.LON),
            lat = find(TemplateColumns.LAT),
            elevation = find(TemplateColumns.ELEVATION),
            towerType = find(TemplateColumns.TOWER_TYPE),
            circuitCount = find(TemplateColumns.CIRCUIT_COUNT),
            startTower = find(TemplateColumns.START_TOWER),
            endTower = find(TemplateColumns.END_TOWER),
            sameTower = find(TemplateColumns.SAME_TOWER)
        )
    }

    private fun parseDouble(s: String): Double? {
        if (s.isBlank()) return null
        val cleaned = s.replace("°", "").replace("\"", "").replace("′", "").trim()
        cleaned.toDoubleOrNull()?.let { return it }
        // 度分秒
        com.kunming.powerline.util.GeoUtils.parseDms(s)?.let { return it }
        // 度分（如 103.05 或 103°14.5'）
        val dm = Regex("""(\d+)[°度]\s*([\d.]+)['′]""").find(s)
        if (dm != null) {
            val d = dm.groupValues[1].toDoubleOrNull() ?: return null
            val m = dm.groupValues[2].toDoubleOrNull() ?: return null
            return d + m / 60.0
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
