package com.kunming.powerline.util

/**
 * 点击地图坐标后可创建的业务点位类型（1.6.0）。
 *
 * 长按地图空白处会弹出类型选择，选中后输入名称即写入对应图层。
 * 这里只放纯数据与文本规则，不依赖 Android / Compose，便于 JVM 单测。
 */
enum class MapPointKind(
    val key: String,
    val label: String,
    val emoji: String,
    val defaultName: String,
    val extraLabel: String,
    val extraHint: String
) {
    DANGER("danger", "危险点", "⚠", "危险点", "等级", "Ⅰ / Ⅱ / Ⅲ，可留空"),
    GUARDIAN("guardian", "护线员", "👷", "护线员", "电话", "可留空"),
    FAVORITE("favorite", "收藏点", "⭐", "收藏点", "备注", "可留空"),
    CAMERA("camera", "视频监控", "📹", "视频监控", "RTSP 地址", "可留空"),
    TAKEOFF("takeoff", "起飞点", "🚁", "起飞点", "海拔 (m)", "可留空"),
    CROSSING("crossing", "交叉跨越", "✖", "交叉跨越", "跨越类型", "如：公路 / 铁路，可留空");

    companion object {
        fun of(key: String): MapPointKind? = entries.firstOrNull { it.key == key }
    }
}

object MapPointTypes {

    /** 名称为空时给兜底名，避免地图上出现没有标识的图标 */
    fun normalizeName(kind: MapPointKind, input: String): String =
        input.trim().ifBlank { kind.defaultName }

    /** 危险点等级规范化：只接受 Ⅰ/Ⅱ/Ⅲ（或 1/2/3），其它一律按 Ⅲ */
    fun normalizeLevel(input: String): String = when (input.trim()) {
        "1", "Ⅰ", "I", "一" -> "Ⅰ"
        "2", "Ⅱ", "II", "二" -> "Ⅱ"
        "3", "Ⅲ", "III", "三" -> "Ⅲ"
        else -> "Ⅲ"
    }

    /** 起飞点海拔：非数字按未填写处理 */
    fun parseAltitude(input: String): Double? = input.trim().toDoubleOrNull()

    /** 附加字段写库前统一去空白 */
    fun cleanExtra(input: String): String = input.trim()

    fun savedMessage(kind: MapPointKind, name: String): String =
        "已添加${kind.label}「$name」"
}
