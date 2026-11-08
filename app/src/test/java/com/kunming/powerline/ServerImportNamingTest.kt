package com.kunming.powerline

import com.kunming.powerline.data.parser.ImportLine
import com.kunming.powerline.data.parser.ImportTower
import com.kunming.powerline.data.parser.ParseResult
import com.kunming.powerline.data.parser.ServerImportNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.4.0 服务器文件导入线路：文件名命名 + 电压等级自动识别 */
class ServerImportNamingTest {

    @Test
    fun `文件名去掉路径与扩展名且保留中文`() {
        assertEquals("220kV昆北线", ServerImportNaming.baseName("220kV昆北线.kml"))
        assertEquals("行政区划", ServerImportNaming.baseName("/data/files/行政区划.KMZ"))
        assertEquals("某某线", ServerImportNaming.baseName("某某线.geojson"))
        assertEquals("无扩展名", ServerImportNaming.baseName("无扩展名"))
    }

    @Test
    fun `识别常见电压写法`() {
        assertEquals(220, ServerImportNaming.voltageOf("220kV某某线"))
        assertEquals(500, ServerImportNaming.voltageOf("500 KV 线路"))
        assertEquals(110, ServerImportNaming.voltageOf("某某110千伏线"))
        assertEquals(35, ServerImportNaming.voltageOf("35kV"))
        assertEquals(110, ServerImportNaming.voltageOf("110_某某线"))
        // 非规范但带单位的值映射到最近的规范等级
        assertEquals(35, ServerImportNaming.voltageOf("10kV配电线"))
    }

    @Test
    fun `年份与编号不会被误判成电压`() {
        assertNull(ServerImportNaming.voltageOf("2024年巡检台账"))
        assertNull(ServerImportNaming.voltageOf("线路编号202405"))
        assertNull(ServerImportNaming.voltageOf("某某线"))
        // 1000kV 与规范等级差距过大，不强行映射
        assertNull(ServerImportNaming.voltageOf("1000kV特高压"))
    }

    @Test
    fun `单条线路用文件名并回填电压`() {
        val r = ParseResult(
            lines = listOf(
                ImportLine(name = "文件内部名", voltageLevel = 110).apply {
                    towers.add(ImportTower("#1", 102.7, 25.1))
                }
            ),
            format = "kml"
        )
        val fixed = ServerImportNaming.apply(r, "220kV昆北线.kml")
        assertEquals("220kV昆北线", fixed.lines[0].name)
        assertEquals(220, fixed.lines[0].voltageLevel)
        assertEquals(1, fixed.lines[0].towers.size)
        assertEquals("#1", fixed.lines[0].towers[0].towerNumber)
        assertEquals("kml", fixed.format)
    }

    @Test
    fun `多条线路追加序号且互不重名`() {
        val r = ParseResult(
            lines = listOf(
                ImportLine(name = "A", voltageLevel = 110),
                ImportLine(name = "B", voltageLevel = 35)
            )
        )
        val fixed = ServerImportNaming.apply(r, "500kV某某线.kmz")
        assertEquals(listOf("500kV某某线_1", "500kV某某线_2"), fixed.lines.map { it.name })
        assertEquals(listOf(500, 500), fixed.lines.map { it.voltageLevel })
    }

    @Test
    fun `文件名没有电压时从内部线路名识别`() {
        val r = ParseResult(lines = listOf(ImportLine(name = "220kV内部名", voltageLevel = 110)))
        val fixed = ServerImportNaming.apply(r, "某某线路.kml")
        assertEquals("某某线路", fixed.lines[0].name)
        assertEquals(220, fixed.lines[0].voltageLevel)
    }

    @Test
    fun `文件名与内部名都没有电压时保留解析器结果`() {
        val r = ParseResult(lines = listOf(ImportLine(name = "内部名", voltageLevel = 35)))
        val fixed = ServerImportNaming.apply(r, "某某线路.kml")
        assertEquals(35, fixed.lines[0].voltageLevel)
    }

    @Test
    fun `空解析结果或空文件名原样返回`() {
        val empty = ParseResult(lines = emptyList())
        assertTrue(ServerImportNaming.apply(empty, "220kV线.kml").lines.isEmpty())
        val r = ParseResult(lines = listOf(ImportLine(name = "原名", voltageLevel = 110)))
        assertEquals("原名", ServerImportNaming.apply(r, "").lines[0].name)
    }
}
