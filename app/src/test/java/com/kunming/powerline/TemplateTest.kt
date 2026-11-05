package com.kunming.powerline

import com.kunming.powerline.data.parser.ImportTower
import com.kunming.powerline.data.parser.TemplateColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 导入模板与 Excel 读取链路测试
 *
 * 验证：assets 里的 import_template.xlsx 可被 Apache POI 正常打开，
 * 列名与 TemplateColumns 定义一致（否则自动列映射会失效）。
 */
class TemplateTest {

    private val template = File("src/main/assets/import_template.xlsx")

    @Test
    fun templateFile_exists() {
        assertTrue("缺少 app/src/main/assets/import_template.xlsx", template.exists())
        assertTrue(template.length() > 1000)
    }

    @Test
    fun template_isValidZipPackage() {
        java.util.zip.ZipFile(template).use { z ->
            listOf("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml")
                .forEach { assertNotNull("缺少部件 $it", z.getEntry(it)) }
        }
    }

    @Test
    fun template_headersMatchDefinition() {
        val text = sheet1Xml()
        listOf("线路名称", "电压等级", "杆塔号", "经度", "纬度").forEach { key ->
            assertTrue("模板缺少关键列：$key", text.contains(key))
        }
    }

    @Test
    fun template_sampleValuesAreSane() {
        val text = sheet1Xml()
        assertTrue("缺少昆明经度样例", text.contains("102.71"))
        assertTrue("缺少昆明纬度样例", text.contains("25.04"))
    }

    private fun sheet1Xml(): String = java.util.zip.ZipFile(template).use { z ->
        z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
    }

    @Test
    fun towerNaturalSort_order() {
        // 与 ExcelParser.numericPrefix 等价的前缀提取逻辑
        fun prefix(s: String): Int? = Regex("""^#?(\d+)""").find(s.trim())?.groupValues?.get(1)?.toIntOrNull()
        val list = listOf("12", "3", "1-1", "N12", "12-2", "#5")
            .map { ImportTower(towerNumber = it, lon = 102.7, lat = 25.0) }
            .sortedWith(compareBy<ImportTower> { prefix(it.towerNumber) ?: Int.MAX_VALUE }
                .thenBy { it.towerNumber })
        assertEquals(listOf("1-1", "3", "#5", "12", "12-2", "N12"), list.map { it.towerNumber })
    }
}

/**
 * 零依赖 xlsx 读写往返测试（替代 Apache POI 后的核心回归）
 */
class XlsxRoundTripTest {

    @Test
    fun writeThenRead_preservesValues() {
        val bytes = com.kunming.powerline.data.parser.XlsxWriter.write(
            listOf(
                com.kunming.powerline.data.parser.XlsxWriter.Sheet(
                    name = "线路台账",
                    headers = listOf("线路名称", "杆塔号", "经度", "纬度", "海拔"),
                    rows = listOf(
                        listOf("110kV 测试线", "1", 102.7123, 25.0401, 1892),
                        listOf("110kV 测试线", "2", 102.7168, 25.0424, 1890)
                    ),
                    widths = listOf(24, 10, 13, 13, 10)
                )
            )
        )
        val sheet = com.kunming.powerline.data.parser.XlsxReader.readFirstSheet(bytes.inputStream())
        assertEquals("线路台账", sheet.name)
        assertEquals(3, sheet.rows.size)   // 表头 + 2 数据行
        assertEquals("线路名称", sheet.rows[0][0])
        assertEquals("110kV 测试线", sheet.rows[1][0])
        assertEquals("1", sheet.rows[1][1])
        // 经纬度精度必须保留（不能丢小数）
        assertEquals("102.7123", sheet.rows[1][2])
        assertEquals("25.0401", sheet.rows[1][3])
        assertEquals("1892", sheet.rows[1][4])
    }

    @Test
    fun readAssetTemplate_works() {
        val f = File("src/main/assets/import_template.xlsx")
        val sheet = f.inputStream().use { com.kunming.powerline.data.parser.XlsxReader.readFirstSheet(it) }
        assertEquals("线路台账", sheet.name)
        assertTrue(sheet.rows.size >= 6)
        assertEquals("线路名称", sheet.rows[0][0])
        assertEquals("杆塔号", sheet.rows[0][4])
    }

    @Test
    fun read_skipsEmptyRowsInNumbers() {
        val bytes = com.kunming.powerline.data.parser.XlsxWriter.write(
            listOf(
                com.kunming.powerline.data.parser.XlsxWriter.Sheet(
                    name = "S",
                    headers = listOf("a", "b"),
                    rows = listOf(listOf("1", "2"), listOf("", ""), listOf("3", "4"))
                )
            )
        )
        val sheet = com.kunming.powerline.data.parser.XlsxReader.readFirstSheet(bytes.inputStream())
        // 空行保留占位，但 rowNumbers 记录真实行号
        assertEquals(listOf(1, 2, 3, 4), sheet.rowNumbers)
    }
}
