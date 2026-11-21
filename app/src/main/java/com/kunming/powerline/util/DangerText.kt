package com.kunming.powerline.util

import com.kunming.powerline.data.local.entity.DangerPointEntity
import java.util.Locale

/**
 * 危险点文案（1.6.4）
 *
 * 地图点击与列表共用同一段描述，避免两处各写一套、字段改了漏改。
 */
object DangerText {

    fun fmt(v: Double?, unit: String = ""): String =
        if (v == null) "—" else {
            val s = if (v == v.toLong().toDouble()) v.toLong().toString()
            else String.format(Locale.US, "%.2f", v)
            s + unit
        }

    /** 列表主标题：地物类型 + 净空距离（默认展示口径） */
    fun title(d: DangerPointEntity): String {
        val g = (d.groundType ?: d.vegetationType ?: d.type).ifBlank { "危险点" }
        return if (d.clearanceM != null) "$g · 净空 ${fmt(d.clearanceM, "m")}" else g
    }

    /** 列表副标题：杆塔区间 · 缺陷等级 · 植被类型 */
    fun subtitle(d: DangerPointEntity): String {
        val parts = mutableListOf<String>()
        d.towerSpan?.takeIf { it.isNotBlank() }?.let { parts += "区间 $it" }
        (d.defectLevel ?: d.level).takeIf { it.isNotBlank() }?.let { parts += "$it 级" }
        d.vegetationType?.takeIf { it.isNotBlank() && it != d.groundType }?.let { parts += it }
        d.measuredAt?.takeIf { it.isNotBlank() }?.let { parts += it }
        return if (parts.isEmpty()) "未填写区间与等级" else parts.joinToString(" · ")
    }

    /** 地图点击详情（多行） */
    fun detail(d: DangerPointEntity): String = buildString {
        append("危险点 · ").append((d.groundType ?: d.type).ifBlank { "未分类" })
        if (d.clearanceM != null) append("　净空 ").append(fmt(d.clearanceM, "m"))
        append('\n')
        val l1 = mutableListOf<String>()
        d.towerSpan?.takeIf { it.isNotBlank() }?.let { l1 += "杆塔区间 $it" }
        d.measuredAt?.takeIf { it.isNotBlank() }?.let { l1 += "测量 $it" }
        if (l1.isNotEmpty()) append(l1.joinToString(" · ")).append('\n')
        val l2 = mutableListOf<String>()
        (d.defectLevel ?: d.level).takeIf { it.isNotBlank() }?.let { l2 += "缺陷等级 $it" }
        if (d.safeDistanceM != null) l2 += "安全距离 ${fmt(d.safeDistanceM, "m")}"
        if (l2.isNotEmpty()) append(l2.joinToString(" · ")).append('\n')
        val l3 = mutableListOf<String>()
        if (d.horizM != null) l3 += "水平 ${fmt(d.horizM, "m")}"
        if (d.vertM != null) l3 += "垂直 ${fmt(d.vertM, "m")}"
        if (d.heightM != null) l3 += "导线高 ${fmt(d.heightM, "m")}"
        if (l3.isNotEmpty()) append(l3.joinToString(" · ")).append('\n')
        val l4 = mutableListOf<String>()
        d.vegetationType?.takeIf { it.isNotBlank() }?.let { l4 += "植被类型 $it" }
        if (d.spanM != null) l4 += "档距 ${fmt(d.spanM, "m")}"
        if (d.distSmallTowerM != null) l4 += "距小号塔 ${fmt(d.distSmallTowerM, "m")}"
        if (l4.isNotEmpty()) append(l4.joinToString(" · ")).append('\n')
        d.measure?.takeIf { it.isNotBlank() }?.let { append("措施：$it\n") }
        append(String.format(Locale.US, "%.6f, %.6f", d.lat, d.lon))
    }
}
