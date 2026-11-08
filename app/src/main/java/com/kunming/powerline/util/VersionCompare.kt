package com.kunming.powerline.util

/**
 * 版本号比较（纯 Kotlin，供「检查更新」判断 GitHub Release 是否比本机新）。
 *
 * 支持 `v` 前缀、位数不等（1.4 与 1.4.0 视为相同）、非数字段按 0 处理，
 * 预发布后缀（如 `-rc1`）在正式包场景统一忽略。
 */
object VersionCompare {

    /** latest 比 current 新时返回 true */
    fun isNewer(current: String, latest: String): Boolean = compare(current, latest) < 0

    /** a < b 返回负数，a == b 返回 0，a > b 返回正数 */
    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun parts(s: String): List<Int> = s.trim()
        .removePrefix("v").removePrefix("V")
        .substringBefore('-')
        .split('.')
        .map { seg -> seg.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        .ifEmpty { listOf(0) }
}
