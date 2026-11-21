package com.kunming.powerline.data.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 危险点报告文件读取（1.6.4）
 *
 * 负责「文件 → 字节 → 文本 → 结构化行」这一段：
 * 编码探测交给 [DangerCsvParser]，这里只处理 Android 侧的 URI / 文件名。
 */
@Singleton
class DangerImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Loaded(
        val fileName: String,
        /** 图层名：文件名去掉扩展名，过长时截断，避免列表里挤成一团 */
        val layerName: String,
        val result: DangerCsvParser.Result
    )

    suspend fun load(uri: Uri): Result<Loaded> = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryName(uri) ?: "危险点报告.csv"
            val bytes = openStream(uri)?.use { it.readBytes() }
                ?: error("无法读取文件（$name）")
            require(bytes.isNotEmpty()) { "文件为空（$name）" }
            val parsed = DangerCsvParser.parseBytes(bytes)
            Loaded(name, layerNameOf(name), parsed)
        }
    }

    /** 文件名 → 图层名：去扩展名；超过 40 字保留前 38 字加省略号 */
    fun layerNameOf(fileName: String): String = Companion.layerNameOf(fileName)

    /**
     * 从文件名推断线路名：`110kV松万线（…）实时工况净空危险点报告_汇总表` → `110kV松万线`
     *
     * 现场线路名里常见罗马数字（Ⅰ/Ⅱ/Ⅲ）、全角括号、下划线，字符类不能只收
     * 中文与字母数字，否则 `220kV昆钢Ⅰ回线` 会被从「回线」重新匹配成「回线」。
     * 这里用「排除分隔符 + 非贪婪到第一个『线』」的写法，兼容各类命名。
     * 识别不到返回 null（由上层用已有线路名匹配兜底）。
     */
    fun guessLineName(fileName: String): String? = Companion.guessLineName(fileName)

    companion object {
        fun layerNameOf(fileName: String): String {
            val base = fileName.substringBeforeLast('.', fileName).trim().ifBlank { "危险点图层" }
            return if (base.length <= 40) base else base.take(38) + "…"
        }

        fun guessLineName(fileName: String): String? {
            val base = fileName.substringBeforeLast('.', fileName)
            // 排除空白、括号、逗号顿号、斜杠与下划线；非贪婪取到第一个「线」
            val m = Regex("""[^\s（()），,、/\\_]{1,20}?线""").find(base) ?: return null
            return m.value.trim().ifBlank { null }
        }
    }

    private fun openStream(uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
}
