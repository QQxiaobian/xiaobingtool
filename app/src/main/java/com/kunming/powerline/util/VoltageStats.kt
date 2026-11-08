package com.kunming.powerline.util

/**
 * 电压等级线路条数统计（纯 Kotlin，供左下角图例显示，不依赖 Android / Compose）。
 *
 * 只统计 App 规范的四个等级（500 / 220 / 110 / 35），未知等级忽略，
 * 避免出现第五行把图例挤乱。
 */
object VoltageStats {

    val LEVELS: List<Int> = listOf(500, 220, 110, 35)

    /** 按规范等级统计，返回值一定包含四个 key（无数据则为 0） */
    fun countByLevel(levels: List<Int>): Map<Int, Int> {
        val result = LEVELS.associateWith { 0 }.toMutableMap()
        levels.forEach { lv ->
            if (result.containsKey(lv)) {
                result[lv] = result.getValue(lv) + 1
            }
        }
        return result
    }

    /** 图例上的条数文字 */
    fun label(count: Int): String = "$count 条"
}
