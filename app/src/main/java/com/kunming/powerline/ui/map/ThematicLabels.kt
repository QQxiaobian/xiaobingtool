package com.kunming.powerline.ui.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 专题要素的地图名称合成
 *
 * 数据来源是 KML / GeoJSON / GPX，名称与备注关系比较随意：
 * - `<name>` 一般是区域名或点位名
 * - 备注多数放在 `<description>` / `备注` / `remark` 等属性里
 * - 有些文件整层都没有要素名，只能退回图层名
 *
 * 这里统一成「第一行名称 + 第二行备注」两行文案，地图上由 text-max-width 控制折行。
 */
object ThematicLabels {

    /** 备注类属性名的候选（小写比较） */
    private val DESC_KEYS = listOf(
        "description", "desc", "remark", "note", "notes", "comment",
        "备注", "说明", "描述", "内容", "地点"
    )

    /** 备注最多展示的字数，超出截断，避免长描述铺满屏幕 */
    const val MAX_DESC_CHARS = 18

    /** 备注当作名称时最多取几个字（行政区划常见：无 name、只有 description） */
    const val MAX_NAME_FROM_DESC = 12

    /** 单层要素过多时不再用图层名兜底，否则满屏重复文字 */
    const val FALLBACK_MAX_FEATURES = 50

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 合成地图标注文案。
     *
     * @param name           要素名（KML Placemark 名）
     * @param propertiesJson 原始属性 JSON
     * @param layerName      图层名，作为无要素名时的兜底
     * @param layerRemark    图层备注，附图只有一个区域时显示在名称下方
     * @param kind           point / line / polygon
     * @param featureCount   该图层要素数量，用于判断是否值得兜底
     * @return 空串表示不标注
     */
    fun compose(
        name: String? = null,
        propertiesJson: String? = null,
        layerName: String? = null,
        layerRemark: String? = null,
        kind: String = "polygon",
        featureCount: Int = 1
    ): String {
        val own = name?.trim()?.takeIf { it.isNotEmpty() }
        val fromFeature = descriptionOf(propertiesJson)
        // 图层备注只在"一层一个区域"时补位，多面图层重复显示备注会糊满屏幕
        val fromLayer = if (featureCount <= 1) layerRemark?.let { clean(it) } else null
        val desc = fromFeature ?: fromLayer

        // 名称缺省时用备注首段充当名称。
        // 行政区划图常见「<name> 为空、只有 description」，过去直接返回空串导致整层无标注；
        // 这里改为取备注前 12 个字作为名称，至少每个区域在地图上都有字可看。
        val base = own
            ?: desc?.takeIf { it.isNotBlank() }?.let { summaryOf(it) }
            ?: fallbackName(layerName, kind, featureCount)
            ?: return ""

        // 名称本身就是备注摘要时，不再重复第二行
        if (own == null) return base

        // 备注若以名称开头（"五华区，面积…"），第二行去掉重复前缀只留后半句
        val extra = desc
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith(base)) it.removePrefix(base).trimStart(' ', '，', ',', '、', '：', ':', '·', '-') else it }
            ?.takeIf { it.isNotBlank() && it != base }
        if (extra == null) return base
        return base + "\n" + ellipsis(extra, MAX_DESC_CHARS)
    }

    /**
     * 无要素名时的兜底：
     * 点和线通常一条一个名字，用图层名会在每个点上重复，故只对面做兜底；
     * 且**仅限单要素图层**——多面图层（行政区划、成片巡视区）每个面都写同一个
     * 图层名会糊满屏幕，这种情况由渲染层的「整层标注」补一条即可。
     */
    fun fallbackName(layerName: String?, kind: String, featureCount: Int): String? {
        if (kind != "polygon") return null
        if (featureCount > 1) return null
        return layerName?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * 备注摘要：按标点切出第一段，再按名称长度截断。
     * 用于「无 name、只有 description」的数据，避免把整段介绍挂到地图上。
     */
    fun summaryOf(text: String): String {
        val seg = text
            .split('，', ',', '；', ';', '|', '、', '：', ':', '。')
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.trim('.', '·', '-')
            .orEmpty()
        return ellipsis(seg.ifBlank { text }, MAX_NAME_FROM_DESC)
    }

    /** 从属性 JSON 里取备注类字段 */
    fun descriptionOf(propertiesJson: String?): String? {
        val props = parseProps(propertiesJson)
        if (props.isEmpty()) return null
        // 先按优先级精确匹配，再退化为包含匹配
        DESC_KEYS.forEach { key ->
            props.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
                ?.let { return clean(it) }
        }
        return props.entries.firstOrNull { (k, _) ->
            val lk = k.lowercase()
            lk.contains("desc") || lk.contains("备注") || lk.contains("remark")
        }?.value?.let { clean(it) }
    }

    /** 属性 JSON → Map，解析失败返回空表（导入数据脏不能影响渲染） */
    fun parseProps(propertiesJson: String?): Map<String, String> {
        if (propertiesJson.isNullOrBlank()) return emptyMap()
        return try {
            val obj = json.parseToJsonElement(propertiesJson) as? JsonObject ?: return emptyMap()
            obj.entries.mapNotNull { (k, v) ->
                val s = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
                s.takeIf { it.isNotBlank() }?.let { k to it }
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** KML description 常含 HTML 标签与换行，压成单行纯文本 */
    private fun clean(raw: String): String = raw
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun ellipsis(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max) + "…"
}
