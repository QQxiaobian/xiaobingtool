package com.kunming.powerline

import com.kunming.powerline.data.parser.DangerCsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

/**
 * 净空危险点报告 CSV 解析（1.6.4）
 *
 * 样本取自现场真实报告：GB2312 编码、经纬度带 °E / °N 后缀、表头带 (m) 单位。
 */
class DangerCsvParserTest {

    private val header =
        "序号,杆塔区间,测量时间,经度,纬度,高度(m),档距(m),距小号塔距离(m)," +
            "地物类型,水平距离(m),垂直距离(m),净空实测距离(m),缺陷等级,安全距离(m)"

    private val rows = listOf(
        "2,00—01,2024/7/5,102.867557°E,24.858408°N,1972.77,32.31,14.38,植被,2.24,6.12,6.51,重大,7",
        "9,01—02,2024/7/5,102.867284°E,24.857939°N,1963.68,159.31,46.49,建筑物,1.75,12.62,12.74,紧急,5.385",
        "13,01—02,2024/7/5,102.867271°E,24.857847°N,1963.26,159.31,56.57,建筑物,1.96,11.6,11.77,紧急,5.385",
        "29,01—02,2024/7/5,102.867193°E,24.857334°N,1960.08,159.31,113.17,植被,5.11,8.98,10.34,一般,13"
    )

    private fun gbkBytes(): ByteArray =
        (header + "\n" + rows.joinToString("\n")).toByteArray(Charset.forName("GB18030"))

    @Test
    fun `GB2312 编码的报告能正确解码并解析`() {
        val r = DangerCsvParser.parseBytes(gbkBytes())
        assertEquals(4, r.records.size)
        assertEquals("GB18030", r.encoding)
        assertTrue(r.errors.isEmpty())
        assertTrue(r.hasCoordinateColumn)
    }

    @Test
    fun `经纬度带半球后缀可解析`() {
        val r = DangerCsvParser.parseBytes(gbkBytes())
        val first = r.records.first { it.seqNo == "2" }
        assertEquals(102.867557, first.lon, 1e-9)
        assertEquals(24.858408, first.lat, 1e-9)
    }

    @Test
    fun `报告字段完整落入结构化字段`() {
        val r = DangerCsvParser.parseBytes(gbkBytes())
        val d = r.records.first { it.seqNo == "2" }
        assertEquals("00—01", d.towerSpan)
        assertEquals("2024/7/5", d.measuredAt)
        assertEquals(1972.77, d.heightM!!, 1e-9)
        assertEquals(32.31, d.spanM!!, 1e-9)
        assertEquals(14.38, d.distSmallTowerM!!, 1e-9)
        assertEquals("植被", d.groundType)
        assertEquals(2.24, d.horizM!!, 1e-9)
        assertEquals(6.12, d.vertM!!, 1e-9)
        assertEquals(6.51, d.clearanceM!!, 1e-9)
        assertEquals("重大", d.defectLevel)
        assertEquals(7.0, d.safeDistanceM!!, 1e-9)
        // 报告没有「植被类型」列，回退为地物类型，保证筛选可用
        assertEquals("植被", d.vegetationType)
    }

    @Test
    fun `按净空距离升序排列 最危险在前`() {
        val r = DangerCsvParser.parseBytes(gbkBytes())
        val clears = r.records.map { it.clearanceM }
        assertEquals(listOf(6.51, 10.34, 11.77, 12.74), clears)
        assertEquals("2", r.records.first().seqNo)
    }

    @Test
    fun `个别行坐标缺失只报该行 不影响其它行`() {
        val text = header + "\n" +
            "1,00—01,2024/7/5,102.86°E,24.85°N,1900,30,10,植被,2,6,6.2,一般,7\n" +
            "2,00—01,2024/7/5,,,1900,30,10,植被,2,6,6.2,一般,7\n" +
            "3,00—02,2024/7/5,102.87°E,24.86°N,1900,30,10,植被,2,6,5.8,重大,7"
        val r = DangerCsvParser.parse(text)
        assertEquals(2, r.records.size)
        assertEquals(1, r.errors.size)
        assertEquals(3, r.errors.first().rowNo)
        assertTrue(r.errors.first().message.contains("经纬度"))
    }

    @Test
    fun `经纬度超出合法范围被拒绝`() {
        val text = header + "\n" +
            "1,00—01,2024/7/5,302.86°E,24.85°N,1900,30,10,植被,2,6,6.2,一般,7"
        val r = DangerCsvParser.parse(text)
        assertTrue(r.records.isEmpty())
        assertEquals(1, r.errors.size)
        assertTrue(r.errors.first().message.contains("范围"))
    }

    @Test
    fun `UTF-8 BOM 报告同样识别`() {
        val body = (header + "\n" + rows.first()).toByteArray(Charsets.UTF_8)
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body
        val r = DangerCsvParser.parseBytes(bytes)
        assertEquals("UTF-8 BOM", r.encoding)
        assertEquals(1, r.records.size)
        assertEquals("植被", r.records.first().groundType)
    }

    @Test
    fun `表头带单位与无单位都能映射`() {
        val noUnit = "序号,杆塔区间,经度,纬度,地物类型,净空距离,缺陷等级\n" +
            "1,00—01,102.86,24.85,植被,6.2,重大"
        val r = DangerCsvParser.parse(noUnit)
        assertEquals(1, r.records.size)
        assertEquals(6.2, r.records.first().clearanceM!!, 1e-9)
    }

    @Test
    fun `有独立植被类型列时优先使用`() {
        val text = "序号,经度,纬度,地物类型,植被类型,净空实测距离(m)\n" +
            "1,102.86,24.85,植被,桉树,6.2"
        val r = DangerCsvParser.parse(text)
        assertEquals("桉树", r.records.first().vegetationType)
        assertEquals("植被", r.records.first().groundType)
    }

    @Test
    fun `宽松数值解析`() {
        assertEquals(6.51, DangerCsvParser.number("6.51m")!!, 1e-9)
        assertEquals(30.0, DangerCsvParser.number(">30")!!, 1e-9)
        assertEquals(1234.5, DangerCsvParser.number("1,234.5")!!, 1e-9)
        assertNull(DangerCsvParser.number("—"))
        assertNull(DangerCsvParser.number("/"))
        assertNull(DangerCsvParser.number(""))
    }

    @Test
    fun `坐标解析覆盖半球前缀与度分秒`() {
        assertEquals(102.867557, DangerCsvParser.coordinate("102.867557°E")!!, 1e-9)
        assertEquals(24.858408, DangerCsvParser.coordinate("24.858408°N")!!, 1e-9)
        assertEquals(-102.86, DangerCsvParser.coordinate("102.86°W")!!, 1e-6)
        val dms = DangerCsvParser.coordinate("102°52'3.2\"E")!!
        assertEquals(102.8675, dms, 1e-3)
        assertNotNull(DangerCsvParser.coordinate("E102.86"))
        assertNull(DangerCsvParser.coordinate("—"))
    }

    @Test
    fun `无经纬度列返回可读错误`() {
        val r = DangerCsvParser.parse("名称,备注\n甲,乙\n丙,丁")
        assertTrue(r.records.isEmpty())
        assertTrue(r.errors.isNotEmpty())
        assertTrue(r.errors.first().message.contains("表头"))
    }

    @Test
    fun `空文件不崩溃`() {
        val r = DangerCsvParser.parseBytes(ByteArray(0))
        assertTrue(r.records.isEmpty())
        assertEquals("UTF-8", r.encoding)
    }

    @Test
    fun `分号分隔的报告也能解析`() {
        val text = "序号;经度;纬度;地物类型;净空实测距离(m)\n1;102.86;24.85;植被;6.2"
        val r = DangerCsvParser.parse(text)
        assertEquals(1, r.records.size)
        assertEquals("植被", r.records.first().groundType)
    }
}
