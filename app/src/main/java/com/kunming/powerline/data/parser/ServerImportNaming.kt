package com.kunming.powerline.data.parser

import com.kunming.powerline.util.VoltagePalette
import kotlin.math.abs

/**
 * 服务器文件导入线路时的命名 / 电压识别规则（1.4.0，纯 Kotlin，便于 JVM 单测）。
 *
 * - 线路名默认取服务器上的文件名（去掉扩展名）；一个文件里含多条线路时追加 `_1` / `_2` 区分；
 * - 电压等级优先从文件名识别（"220kV" / "500千伏" / "110_某某线"），
 *   其次尝试文件内部的线路名，最后保留解析器给出的值；
 * - 只在「服务器文件」导入链路调用，本地文件导入的行为保持不变。
 */
object ServerImportNaming {

    /** 取文件名主体：去掉路径与最后一个扩展名，保留中文与大小写 */
    fun baseName(fileName: String): String {
        val n = fileName.trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val dot = n.lastIndexOf('.')
        return (if (dot > 0) n.substring(0, dot) else n).trim()
    }

    /**
     * 从文本识别电压等级（kV / 千伏 / 以等级数字开头）。
     * 带单位的非规范值映射到最近的规范等级；纯数字只在精确命中规范等级时采纳，
     * 避免把 "2024年巡检" 之类的年份 / 编号误判成电压。
     */
    fun voltageOf(text: String): Int? {
        val withUnit = Regex("""(\d{2,4})\s*(?:kV|KV|kv|Kv|千伏)""")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
        val bare = Regex("""^\s*(\d{2,3})(?!\d)""")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
        val raw = withUnit ?: bare ?: return null
        if (raw in VoltagePalette.levels) return raw
        // 只有带单位的写法才允许映射到最近的规范等级（500/220/110/35）
        if (withUnit == null) return null
        val nearest = VoltagePalette.nearestLevel(raw)
        return if (abs(nearest - raw) <= 50) nearest else null
    }

    /** 把解析结果按服务器文件名重命名，并回填电压等级 */
    fun apply(result: ParseResult, fileName: String): ParseResult {
        val base = baseName(fileName)
        if (base.isEmpty() || result.lines.isEmpty()) return result
        val fileVoltage = voltageOf(base)
        val multi = result.lines.size > 1
        val fixed = result.lines.mapIndexed { i, l ->
            val name = if (multi) "${base}_${i + 1}" else base
            // 电压优先级：文件名 > 文件内部线路名 > 解析器原值
            val voltage = fileVoltage
                ?: voltageOf(l.name)
                ?: l.voltageLevel
            l.copy(name = name, voltageLevel = voltage)
        }
        return result.copy(lines = fixed)
    }
}
