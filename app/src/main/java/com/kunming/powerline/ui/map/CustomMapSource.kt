package com.kunming.powerline.ui.map

/**
 * 自定义地图源在渲染层的纯数据规格（不依赖 Room / Android，便于单元测试）。
 */
data class CustomSourceSpec(
    val id: String,
    val name: String,
    /** 已展开 {s} 的瓦片 URL 列表 */
    val tiles: List<String>,
    val tileSize: Int = 256,
    val minZoom: Int = 0,
    val maxZoom: Int = 18,
    /** xyz / tms，对应 MapLibre raster source 的 scheme */
    val scheme: String = "xyz",
    /** 叠加不透明度 0~1 */
    val opacity: Float = 1f,
    /** true = 替代内置底图作为主底图 */
    val asBase: Boolean = false
)

/**
 * 自定义地图源（XYZ / TMS / 自建地图服务器）模板处理。
 *
 * 支持：
 * - 任意 XYZ 模板，必须含 {z}/{x}/{y}
 * - 可选 {s} 子域占位符（配合 "a,b,c" 展开成多 URL 轮询）
 * - TMS 方案通过 style 的 scheme 字段表达，而不是改写 URL
 */
object CustomMapSource {

    /** 返回错误文案，null 表示模板可用 */
    fun validate(template: String): String? {
        val t = template.trim()
        if (t.isEmpty()) return "瓦片地址不能为空"
        val okScheme = t.startsWith("http://", true) ||
            t.startsWith("https://", true) || t.startsWith("file://", true)
        if (!okScheme) return "地址需以 http:// / https:// / file:// 开头"
        if (!t.contains("{z}")) return "地址缺少 {z} 层级占位符"
        if (!t.contains("{x}")) return "地址缺少 {x} 列号占位符"
        if (!t.contains("{y}")) return "地址缺少 {y} 行号占位符"
        if (t.contains('"') || t.contains('\\')) return "地址不能包含引号或反斜杠"
        if (t.contains("\n") || t.contains("\r")) return "地址不能包含换行"
        return null
    }

    /** 把 {s} 展开为子域名列表；没有 {s} 时返回单元素列表 */
    fun expand(template: String, subdomains: String = ""): List<String> {
        val t = template.trim()
        if (!t.contains("{s}")) return listOf(t)
        val subs = subdomains.split(",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        // 未填子域时去掉 {s} 及其后紧跟的点，避免生成 "https://.example.com"
        if (subs.isEmpty()) return listOf(t.replace("{s}.", "").replace("{s}", ""))
        return subs.map { t.replace("{s}", it) }
    }

    /** 由表单参数构造渲染规格；模板非法时返回 null */
    fun spec(
        id: String,
        name: String,
        template: String,
        subdomains: String = "",
        tileSize: Int = 256,
        minZoom: Int = 0,
        maxZoom: Int = 18,
        scheme: String = "xyz",
        opacity: Float = 1f,
        asBase: Boolean = false
    ): CustomSourceSpec? {
        if (validate(template) != null) return null
        val size = when (tileSize) { 256, 512 -> tileSize; else -> 256 }
        val z0 = minZoom.coerceIn(0, 24)
        val z1 = maxZoom.coerceIn(z0, 24)
        return CustomSourceSpec(
            id = id,
            name = name.trim().ifBlank { "自定义地图源" },
            tiles = expand(template, subdomains),
            tileSize = size,
            minZoom = z0,
            maxZoom = z1,
            scheme = if (scheme.equals("tms", true)) "tms" else "xyz",
            opacity = opacity.coerceIn(0f, 1f),
            asBase = asBase
        )
    }

    /** JSON 字符串转义，用户填的 URL / 名称直接进 style JSON 前必须调用 */
    fun jsonEscape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
