package com.kunming.powerline.data.remote.model

import com.squareup.moshi.JsonClass

/**
 * NAS 原始文件库（1.3.0）里的一个文件。
 *
 * 与 /api/datasets 不同：这里存的是 **KML / KMZ / GeoJSON / GPX / SHP(zip) 原始文件**，
 * 由服务端扫描 files 目录得到，App 下载后交给 GeoFileParser 按扩展名解析。
 */
@JsonClass(generateAdapter = true)
data class ServerFileDto(
    val id: String = "",
    val name: String = "",
    val sizeBytes: Long = 0L,
    /** kml / kmz / geojson / json / gpx / shp / zip */
    val ext: String = "",
    val kind: String = "",
    val updatedAt: String = ""
)

/** 纯 Kotlin 的扩展名分类，供 UI 与单元测试复用（不依赖 Android） */
object ServerFileKind {
    val SUPPORTED = setOf("kml", "kmz", "geojson", "json", "gpx", "shp", "zip")

    fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase().trim()

    fun isSupported(name: String): Boolean = extOf(name) in SUPPORTED

    /** 二进制格式：必须靠扩展名分派，不能按文本嗅探 */
    fun isBinary(name: String): Boolean = extOf(name) in setOf("kmz", "shp", "zip")

    /** 该格式是否适合作为「线路 + 杆塔」导入 */
    fun suggestAsLine(name: String): Boolean =
        extOf(name) in setOf("kml", "kmz", "gpx", "geojson", "json")

    fun label(name: String): String = when (extOf(name)) {
        "kml" -> "KML"
        "kmz" -> "KMZ"
        "geojson" -> "GeoJSON"
        "json" -> "JSON"
        "gpx" -> "GPX"
        "shp" -> "SHP"
        "zip" -> "SHP 压缩包"
        else -> "未知格式"
    }

    /** 下载到 App 私有目录时的安全文件名：去掉路径分隔符与控制字符，保留扩展名 */
    fun safeLocalName(name: String): String {
        val base = name.replace('\\', '/').substringAfterLast('/').trim()
        val clean = base.filter { it !in "/\\\r\n\t" && it.code >= 32 }.take(120)
        return clean.ifBlank { "server-file.${extOf(name).ifBlank { "kml" }}" }
    }
}
