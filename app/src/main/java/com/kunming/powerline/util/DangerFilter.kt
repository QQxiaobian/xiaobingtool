package com.kunming.powerline.util

import com.kunming.powerline.data.local.entity.DangerPointEntity

/**
 * 危险点显示筛选（1.6.4）
 *
 * 现场一份净空报告动辄几百个点，全画在地图上就是一团黄点。
 * 这里把「看什么」交给用户：
 * - **净空距离范围**：只看低于安全距离的紧急点位（如 ≤ 7m）；
 * - **植被 / 地物类型**：只看树障，或排除建筑物。
 *
 * 空集合 = 不限；min 与 max 均可单独使用。
 */
data class DangerFilter(
    val minClearance: Double? = null,
    val maxClearance: Double? = null,
    /** 植被 / 地物类型多选；空 = 全部 */
    val categories: Set<String> = emptySet()
) {
    val isActive: Boolean
        get() = minClearance != null || maxClearance != null || categories.isNotEmpty()

    fun matches(d: DangerPointEntity): Boolean {
        if (minClearance != null || maxClearance != null) {
            val c = d.clearanceM
            // 没有净空值的点（如老的手工危险点）在启用数值筛选时不显示，
            // 否则「只看 ≤7m」里会混进一堆没有距离的点
            if (c == null) return false
            if (minClearance != null && c < minClearance) return false
            if (maxClearance != null && c > maxClearance) return false
        }
        if (categories.isNotEmpty()) {
            val cat = categoryOf(d)
            if (cat == null || cat !in categories) return false
        }
        return true
    }

    fun apply(points: List<DangerPointEntity>): List<DangerPointEntity> =
        if (!isActive) points else points.filter { matches(it) }

    /** 按净空距离排序（最小在前，无值最后） */
    fun sorted(points: List<DangerPointEntity>): List<DangerPointEntity> = points.sortedWith(
        compareBy({ it.clearanceM ?: Double.MAX_VALUE }, { it.seqNo ?: "" })
    )

    companion object {
        /** 类型归属：优先植被类型，其次地物类型，最后老字段 type */
        fun categoryOf(d: DangerPointEntity): String? =
            (d.vegetationType ?: d.groundType ?: d.type).takeIf { !it.isNullOrBlank() }

        /** 全部可选类型（按出现次数降序），供筛选 chips 使用 */
        fun categoriesOf(points: List<DangerPointEntity>): List<Pair<String, Int>> =
            points.mapNotNull { categoryOf(it) }
                .groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key to it.value }

        /**
         * 文本输入 → 数值。空串 = 不限；非法输入返回 [Double.NaN] 供 UI 提示。
         */
        fun parseBound(raw: String): Double? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            return s.toDoubleOrNull()
        }
    }
}
