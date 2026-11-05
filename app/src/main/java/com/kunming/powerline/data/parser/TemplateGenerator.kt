package com.kunming.powerline.data.parser

import android.content.Context
import com.kunming.powerline.util.FileSaver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生成标准导入模板（assets 内置，首次使用时如果缺失会自动生成）
 */
@Singleton
class TemplateGenerator @Inject constructor(private val context: Context) {

    companion object {
        const val ASSET_NAME = "import_template.xlsx"
    }

    /** 生成模板 Excel 字节流（零依赖，XlsxWriter） */
    fun generate(): ByteArray {
        val cols = listOf(
            "线路名称", "电压等级", "起始杆塔号", "终止杆塔号", "杆塔号",
            "经度", "纬度", "海拔", "杆塔类型", "回路数", "同塔多回标识"
        )
        val samples: List<List<Any?>> = listOf(
            listOf("220kV 昆明甲线", 220, "1", "5", "1", 102.7123, 25.0401, 1892, "耐张", 2, "同塔"),
            listOf("220kV 昆明甲线", 220, "1", "5", "2", 102.7168, 25.0424, 1890, "直线", 2, "同塔"),
            listOf("220kV 昆明甲线", 220, "1", "5", "3", 102.7215, 25.0449, 1888, "直线", 2, ""),
            listOf("220kV 昆明甲线", 220, "1", "5", "4", 102.7262, 25.0471, 1885, "转角", 2, ""),
            listOf("220kV 昆明甲线", 220, "1", "5", "5", 102.7308, 25.0495, 1883, "终端", 2, "")
        )
        val notes: List<Pair<String, String>> = listOf(
            "字段" to "说明",
            "线路名称" to "必填。同一条线路使用相同名称，系统按线路名分组。",
            "电压等级" to "必填。取值 500 / 220 / 110 / 35，单位 kV。",
            "起始杆塔号 / 终止杆塔号" to "选填。仅用于描述线路首末杆塔，可留空。",
            "杆塔号" to "必填。同线路内唯一，支持 1、1-1、N12 等格式，按数字自然排序。",
            "经度" to "必填。十进制度（WGS84 / CGCS2000），如 102.7123；也支持 102°42'44.3\" 度分秒格式。",
            "纬度" to "必填。十进制度（WGS84 / CGCS2000），如 25.0401；取值范围 3~54。",
            "海拔" to "选填。单位 m。",
            "杆塔类型" to "选填。耐张 / 直线 / 转角 / 终端 等。",
            "回路数" to "选填。同塔架设的回数，如 2 表示双回。",
            "同塔多回标识" to "选填。填写 同塔 时，系统对同址杆塔做视觉偏移，避免符号重叠。",
            "" to "",
            "注意" to "坐标必须为十进制度，不得使用带方向字母的格式；空行会被自动忽略。"
        )

        return XlsxWriter.write(
            listOf(
                XlsxWriter.Sheet(
                    name = "线路台账",
                    headers = cols,
                    rows = samples,
                    widths = listOf(24, 12, 12, 12, 10, 13, 13, 10, 12, 9, 15)
                ),
                XlsxWriter.Sheet(
                    name = "填写说明",
                    headers = listOf("字段", "说明"),
                    rows = notes.drop(1).map { listOf(it.first, it.second) },
                    widths = listOf(26, 96)
                )
            )
        )
    }

    /** 把内置模板复制到 SAF 指定位置 */
    fun writeTemplateTo(context: Context, uri: android.net.Uri): Boolean {
        val bytes = FileSaver.readAsset(context, ASSET_NAME) ?: generate()
        return FileSaver.writeBytes(context, uri, bytes)
    }

    /** 导出错误行 CSV */
    fun errorRowsCsv(errors: List<ImportError>, header: List<String> = listOf("行号", "错误说明", "原始内容")): String {
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM，Excel 打开中文不乱码
        sb.append(header.joinToString(",")).append("\n")
        errors.forEach { e ->
            sb.append(e.row).append(',')
                .append(escapeCsv(e.message)).append(',')
                .append(escapeCsv(e.rawContent)).append('\n')
        }
        return sb.toString()
    }

    private fun escapeCsv(s: String): String {
        val v = s.replace("\"", "\"\"")
        return if (v.contains(',') || v.contains('\n')) "\"$v\"" else v
    }
}
