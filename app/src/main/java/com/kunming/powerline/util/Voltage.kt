package com.kunming.powerline.util

import androidx.compose.ui.graphics.Color

/**
 * 电压等级颜色规范（图例固定左下角）
 * 500kV 红 / 220kV 蓝 / 110kV 橙 / 35kV 绿
 */
data class VoltageStyle(val level: Int, val color: Color, val widthDp: Float, val label: String)

object VoltagePaletteUi {
    val styles = listOf(
        VoltageStyle(500, Color(0xFFD32F2F), 5f, "500kV"),
        VoltageStyle(220, Color(0xFF1976D2), 4f, "220kV"),
        VoltageStyle(110, Color(0xFFF57C00), 3f, "110kV"),
        VoltageStyle(35, Color(0xFF388E3C), 2f, "35kV")
    )
}

object VoltagePalette {
    val styles = listOf(
        VoltageStyle(500, Color(0xFFD32F2F), 5f, "500kV"),
        VoltageStyle(220, Color(0xFF1976D2), 4f, "220kV"),
        VoltageStyle(110, Color(0xFFF57C00), 3f, "110kV"),
        VoltageStyle(35, Color(0xFF388E3C), 2f, "35kV")
    )

    val levels = listOf(500, 220, 110, 35)

    fun colorOf(level: Int): Color = styles.firstOrNull { it.level == level }?.color ?: Color(0xFF9E9E9E)
    fun widthOf(level: Int): Float = styles.firstOrNull { it.level == level }?.widthDp ?: 2f
    fun labelOf(level: Int): String = "$level" + "kV"

    fun hexColorOf(level: Int): String = when (level) {
        500 -> "#D32F2F"
        220 -> "#1976D2"
        110 -> "#F57C00"
        35 -> "#388E3C"
        else -> "#9E9E9E"
    }

    /** 从文本解析电压等级，如 "220kV" / "220" / "500千伏" */
    fun parseLevel(text: String): Int? {
        val digits = Regex("""(\d{2,3})""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return if (digits in levels) digits else null
    }

    /** 把非标准电压等级映射到最近的规范等级 */
    fun nearestLevel(v: Int): Int = levels.minByOrNull { kotlin.math.abs(it - v) } ?: 35
}
