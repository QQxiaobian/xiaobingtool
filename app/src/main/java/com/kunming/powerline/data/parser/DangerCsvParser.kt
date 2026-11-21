package com.kunming.powerline.data.parser

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 净空危险点报告 CSV 解析（1.6.4）
 *
 * 现场报告由无人机巡检系统导出，实际约束比想象中多：
 * - **编码不固定**：Excel / WPS「另存为 CSV」默认 GB2312（GBK），部分系统导 UTF-8 BOM；
 * - **经纬度带半球后缀**：`102.867557°E` / `24.858408°N`，直接 toDouble 会失败；
 * - **表头带单位**：`高度(m)`、`净空实测距离(m)`，且不同报告列名略有差异；
 * - **空值不统一**：空白、`—`、`/`、`>30`（超量程）都可能出现。
 *
 * 因此这里做三层兜底：编码探测 → 表头别名归一 → 数值/坐标宽松解析。
 * 纯 Kotlin 实现（无 Android 依赖），可在 JVM 单测里直接跑。
 */
object DangerCsvParser {

    /** 列语义键 */
    private const val K_SEQ = "seq"
    private const val K_SPAN = "span"
    private const val K_TIME = "time"
    private const val K_LON = "lon"
    private const val K_LAT = "lat"
    private const val K_HEIGHT = "height"
    private const val K_SPACING = "spacing"
    private const val K_FROM_SMALL = "fromSmall"
    private const val K_GROUND = "ground"
    private const val K_HORIZ = "horiz"
    private const val K_VERT = "vert"
    private const val K_CLEARANCE = "clearance"
    private const val K_DEFECT = "defect"
    private const val K_SAFE = "safe"
    private const val K_VEG = "veg"

    /**
     * 表头别名。比较前会归一（去空格、全角括号转半角、小写），
     * 并优先用「去掉括号单位后的核心名」精确匹配，避免
     * 「净空实测距离」被「距离」误配。
     */
    private val ALIASES: Map<String, List<String>> = mapOf(
        K_SEQ to listOf("序号", "编号", "no", "seq", "index"),
        K_SPAN to listOf("杆塔区间", "区间", "档段", "塔段", "杆号区间"),
        K_TIME to listOf("测量时间", "测量日期", "时间", "日期", "检测时间"),
        K_LON to listOf("经度", "lon", "lng", "longitude", "x"),
        K_LAT to listOf("纬度", "lat", "latitude", "y"),
        K_HEIGHT to listOf("高度", "导线高度", "高程", "海拔", "height"),
        K_SPACING to listOf("档距", "档距长度", "spacing", "span"),
        K_FROM_SMALL to listOf("距小号塔距离", "距小号侧距离", "小号侧距离", "fromsmalltower"),
        K_GROUND to listOf("地物类型", "地物", "障碍物类型", "目标类型", "groundtype"),
        K_HORIZ to listOf("水平距离", "水平净距", "horizontaldistance"),
        K_VERT to listOf("垂直距离", "垂直净距", "verticaldistance"),
        K_CLEARANCE to listOf("净空实测距离", "净空距离", "净空", "实测净空", "clearance"),
        K_DEFECT to listOf("缺陷等级", "缺陷级别", "隐患等级", "等级", "defectlevel"),
        K_SAFE to listOf("安全距离", "安规距离", "规定距离", "safedistance"),
        K_VEG to listOf("植被类型", "植被", "树种", "植物类型", "vegetation")
    )

    /** 一行危险点 */
    data class Record(
        val seqNo: String? = null,
        val towerSpan: String? = null,
        val measuredAt: String? = null,
        val lon: Double,
        val lat: Double,
        val heightM: Double? = null,
        val spanM: Double? = null,
        val distSmallTowerM: Double? = null,
        val groundType: String? = null,
        val horizM: Double? = null,
        val vertM: Double? = null,
        val clearanceM: Double? = null,
        val defectLevel: String? = null,
        val safeDistanceM: Double? = null,
        val vegetationType: String? = null,
        /** 原始行号（含表头，便于用户回表核对） */
        val rowNo: Int = 0
    )

    data class RowError(val rowNo: Int, val message: String)

    data class Result(
        val records: List<Record>,
        val errors: List<RowError>,
        val headers: List<String>,
        val encoding: String,
        /** 表头是否识别到经纬度列 */
        val hasCoordinateColumn: Boolean
    ) {
        val ok: Boolean get() = records.isNotEmpty()
    }

    // ------------------------------------------------------------------
    // 编码
    // ------------------------------------------------------------------

    private val GB18030: Charset = Charset.forName("GB18030")

    /**
     * 字节 → 文本 + 编码名。
     * 先看 BOM，再尝试严格 UTF-8（遇到非法字节会抛异常），最后回落 GB18030。
     * 这样 GB2312 的现场报告不会变成一串问号。
     */
    fun decode(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to "UTF-8 BOM"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to "UTF-16LE"
        }
        try {
            val dec = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return dec.decode(ByteBuffer.wrap(bytes)).toString() to "UTF-8"
        } catch (_: CharacterCodingException) {
            // 不是合法 UTF-8 —— 现场报告绝大多数是 GB2312 / GBK
        }
        return String(bytes, GB18030) to "GB18030"
    }

    fun parseBytes(bytes: ByteArray, lineName: String? = null): Result =
        decode(bytes).let { (text, enc) -> parse(text, lineName).copy(encoding = enc) }

    // ------------------------------------------------------------------
    // 主解析
    // ------------------------------------------------------------------

    fun parse(text: String, lineName: String? = null): Result {
        val allLines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val lines = allLines.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return Result(emptyList(), emptyList(), emptyList(), "UTF-8", false)
        }
        val delim = detectDelimiter(lines.first())
        val headerIdx = findHeaderRow(lines, delim)
        if (headerIdx < 0) {
            return Result(
                emptyList(),
                listOf(RowError(1, "未识别到表头，缺少「经度」「纬度」列")),
                emptyList(), "UTF-8", false
            )
        }
        val headers = splitCsvLine(lines[headerIdx], delim)
        val cols = mapColumns(headers)
        val hasCoord = cols[K_LON] != null && cols[K_LAT] != null

        val records = mutableListOf<Record>()
        val errors = mutableListOf<RowError>()

        for (i in headerIdx + 1 until lines.size) {
            val raw = lines[i]
            if (raw.isBlank()) continue
            // 有些报告在末尾追加「备注」「说明」等非数据行，直接跳过
            val cells = splitCsvLine(raw, delim)
            if (cells.all { it.isBlank() }) continue
            val rowNo = i + 1

            fun cell(key: String): String = cols[key]?.let { idx ->
                cells.getOrElse(idx) { "" }
            }?.trim().orEmpty()

            val lon = coordinate(cell(K_LON))
            val lat = coordinate(cell(K_LAT))
            if (lon == null || lat == null) {
                errors.add(
                    RowError(
                        rowNo,
                        "经纬度缺失或格式错误（经度=「${cell(K_LON)}」纬度=「${cell(K_LAT)}」）"
                    )
                )
                continue
            }
            if (lon !in -180.0..180.0 || lat !in -90.0..90.0) {
                errors.add(RowError(rowNo, "经纬度超出合法范围（$lon, $lat）"))
                continue
            }

            val ground = cell(K_GROUND).ifBlank { null }
            val veg = cell(K_VEG).ifBlank { null }
            records.add(
                Record(
                    seqNo = cell(K_SEQ).ifBlank { null },
                    towerSpan = cell(K_SPAN).ifBlank { null },
                    measuredAt = cell(K_TIME).ifBlank { null },
                    lon = lon,
                    lat = lat,
                    heightM = number(cell(K_HEIGHT)),
                    spanM = number(cell(K_SPACING)),
                    distSmallTowerM = number(cell(K_FROM_SMALL)),
                    groundType = ground,
                    horizM = number(cell(K_HORIZ)),
                    vertM = number(cell(K_VERT)),
                    clearanceM = number(cell(K_CLEARANCE)),
                    defectLevel = cell(K_DEFECT).ifBlank { null },
                    safeDistanceM = number(cell(K_SAFE)),
                    // 报告通常只有「地物类型」；没有单独植被列时用它兜底，
                    // 保证「按植被类型筛选」在只有地物列的报告上也能用
                    vegetationType = veg ?: ground,
                    rowNo = rowNo
                )
            )
        }

        // 净空距离最小的排最前（最危险优先），无净空值的排最后
        val sorted = records.sortedWith(
            compareBy<Record>({ it.clearanceM ?: Double.MAX_VALUE }, { it.rowNo })
        )
        return Result(sorted, errors, headers, "UTF-8", hasCoord)
    }

    // ------------------------------------------------------------------
    // 表头
    // ------------------------------------------------------------------

    /** 找表头行：前 12 行里第一行同时命中经度 + 纬度 */
    private fun findHeaderRow(lines: List<String>, delim: Char): Int {
        val limit = minOf(lines.size, 12)
        for (i in 0 until limit) {
            val cells = splitCsvLine(lines[i], delim)
            val norm = cells.map { core(it) }
            val hasLon = norm.any { it in ALIASES.getValue(K_LON) }
            val hasLat = norm.any { it in ALIASES.getValue(K_LAT) }
            if (hasLon && hasLat) return i
        }
        // 兜底：首行包含「经度」字样即视为表头
        for (i in 0 until limit) {
            if (lines[i].contains("经度") && lines[i].contains("纬度")) return i
        }
        return -1
    }

    private fun mapColumns(headers: List<String>): Map<String, Int> {
        val cores = headers.map { core(it) }
        val norms = headers.map { norm(it) }
        val out = mutableMapOf<String, Int>()
        ALIASES.forEach { (key, aliases) ->
            // 1) 核心名精确相等
            var idx = cores.indexOfFirst { it in aliases }
            // 2) 归一后精确相等（覆盖「经度(°)」之类）
            if (idx < 0) idx = norms.indexOfFirst { it in aliases }
            // 3) 核心名以别名开头（覆盖「净空实测距离 m」这种无括号写法）
            if (idx < 0) {
                idx = cores.indexOfFirst { c -> c.isNotBlank() && aliases.any { a -> c.startsWith(a) } }
            }
            if (idx >= 0) out[key] = idx
        }
        return out
    }

    /** 归一：小写、去空白、全角括号/冒号转半角 */
    private fun norm(h: String): String = h.trim()
        .replace("（", "(").replace("）", ")")
        .replace("：", ":")
        .replace("\u00A0", " ")
        .replace(Regex("\\s+"), "")
        .lowercase()

    /** 去掉括号单位后的核心名：`高度(m)` → `高度` */
    private fun core(h: String): String = norm(h).substringBefore('(')

    private fun detectDelimiter(line: String): Char {
        val counts = charArrayOf(',', '\t', ';').map { d -> d to line.count { it == d } }
        return counts.maxByOrNull { it.second }?.first ?: ','
    }

    /** 支持引号包裹与转义引号（""）的简易 CSV 行切分 */
    fun splitCsvLine(line: String, d: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuote && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else {
                        inQuote = !inQuote
                    }
                }
                c == d && !inQuote -> {
                    out.add(sb.toString().trim()); sb.clear()
                }
                c == '\r' -> {}
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }

    // ------------------------------------------------------------------
    // 数值 / 坐标
    // ------------------------------------------------------------------

    /** 宽松数值：`6.51` / `6.51m` / `>30` / `—` / `/` */
    fun number(raw: String): Double? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s in setOf("—", "-", "--", "/", "\\", "无", "null", "NULL")) return null
        // 去掉单位、比较符、千分位
        val cleaned = s.replace(Regex("[<>≥≤≈~]"), "")
            .replace(Regex("(?i)(m|米|kv|°)$"), "")
            .replace(",", "")
            .trim()
        cleaned.toDoubleOrNull()?.let { return it }
        // 形如「6.51m(实测)」时取第一段数字
        return Regex("""-?\d+(?:\.\d+)?""").find(cleaned)?.value?.toDoubleOrNull()
    }

    /**
     * 经纬度：`102.867557°E`、`24.858408°N`、`E102.86`、`102°52'3.2"E`、纯数字。
     * S / W 自动取负。
     */
    fun coordinate(raw: String): Double? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s in setOf("—", "-", "/", "无")) return null
        val upper = s.uppercase().replace('，', ',')
        val negative = upper.contains('S') || upper.contains('W') || upper.startsWith("-")

        // 度分秒 / 度分：102°52'3.2"E 或 24°51.5'N
        val dms = Regex(
            """(\d+(?:\.\d+)?)\s*[°度]\s*(\d+(?:\.\d+)?)\s*(?:['′分]\s*)?(?:(\d+(?:\.\d+)?)\s*(?:["″秒])?)?"""
        ).find(upper)
        if (dms != null && upper.any { it == '°' || it == '度' || it == '\'' || it == '′' }) {
            val d = dms.groupValues[1].toDoubleOrNull() ?: return null
            val m = dms.groupValues[2].toDoubleOrNull() ?: 0.0
            val sec = dms.groupValues.getOrNull(3)?.toDoubleOrNull() ?: 0.0
            val v = d + m / 60.0 + sec / 3600.0
            return if (negative) -v else v
        }

        // 纯十进制度：去掉 N/S/E/W 与度符号
        val cleaned = upper.replace(Regex("""[NSEW]"""), "")
            .replace("°", "")
            .replace(" ", "")
            .trim()
        val v = cleaned.toDoubleOrNull() ?: return null
        return if (negative && v > 0) -v else v
    }
}
