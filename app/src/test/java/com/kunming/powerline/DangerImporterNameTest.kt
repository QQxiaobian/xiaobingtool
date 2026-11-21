package com.kunming.powerline

import com.kunming.powerline.data.parser.DangerImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 危险点报告文件名解析（1.6.4）
 *
 * 现场文件名命名不统一：带全角括号、罗马数字回路号、千伏写法、下划线后缀。
 * 图层名要可读、线路名要能对上台账。
 */
class DangerImporterNameTest {

    @Test
    fun `真实报告文件名提取线路名`() {
        assertEquals(
            "110kV松万线",
            DangerImporter.guessLineName("110kV松万线（缺6-11强干扰未飞）实时工况净空危险点报告_汇总表.csv")
        )
    }

    @Test
    fun `罗马数字回路号不会被截断`() {
        // 曾经的正则会从「回线」重新匹配，得到错误的「回线」
        assertEquals(
            "220kV昆钢Ⅰ回线",
            DangerImporter.guessLineName("220kV昆钢Ⅰ回线净空危险点报告.csv")
        )
        assertEquals(
            "110kV某某Ⅱ线",
            DangerImporter.guessLineName("110kV某某Ⅱ线_2024年净空报告.csv")
        )
    }

    @Test
    fun `千伏中文写法可识别`() {
        assertEquals("500千伏某某线", DangerImporter.guessLineName("500千伏某某线汇总.csv"))
    }

    @Test
    fun `只有线路名时取到第一个线字`() {
        assertEquals("松万线", DangerImporter.guessLineName("2024年净空危险点报告_松万线.csv"))
    }

    @Test
    fun `无线路名返回 null`() {
        assertNull(DangerImporter.guessLineName("净空危险点报告.csv"))
        assertNull(DangerImporter.guessLineName("汇总表.csv"))
    }

    @Test
    fun `图层名去掉扩展名`() {
        assertEquals("110kV松万线净空报告", DangerImporter.layerNameOf("110kV松万线净空报告.csv"))
        assertEquals("危险点图层", DangerImporter.layerNameOf(".csv"))
    }

    @Test
    fun `超长文件名截断到四十字以内`() {
        val long = "某".repeat(60) + ".csv"
        val n = DangerImporter.layerNameOf(long)
        assertEquals(39, n.length)
        assertEquals("某".repeat(38) + "…", n)
    }
}
