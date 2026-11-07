package com.kunming.powerline

import com.kunming.powerline.data.parser.KmlParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KML 解析测试 — 覆盖导致 NPE 的空标签/真实 KML 结构。
 *
 * 修复前：parseKml 在空 <coordinates/> 上 nextText() 返回 null，
 * 后续 .trim() 触发 "getText(...) must not be null"。
 */
class KmlParseTest {

    /** 真实输电线路 KML（LineString + Point + ExtendedData 电压） */
    @Test
    fun realKml_lineAndPoint_parsed() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Folder>
      <name>110kV测试线</name>
      <Placemark>
        <name>110kV测试线</name>
        <LineString>
          <coordinates>102.70,25.04,0 102.71,25.05,0 102.72,25.06,0</coordinates>
        </LineString>
      </Placemark>
      <Placemark>
        <name>110kV测试线 1号塔</name>
        <Point>
          <coordinates>102.70,25.04,0</coordinates>
        </Point>
      </Placemark>
      <Placemark>
        <name>110kV测试线 2号塔</name>
        <Point>
          <coordinates>102.71,25.05,0</coordinates>
        </Point>
      </Placemark>
    </Folder>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml)
        assertFalse("应解析到线路", r.lines.isEmpty())
        assertTrue("应解析到杆塔", r.towerCount > 0)
        assertFalse("不应有 getText NPE", r.errors.any { it.message.contains("getText") })
    }

    /** 空标签场景（修复前的 NPE 根因） */
    @Test
    fun emptyTags_noNpe() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name></name>
      <description></description>
      <LineString>
        <coordinates></coordinates>
      </LineString>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml)
        assertFalse("不应有 getText NPE", r.errors.any { it.message.contains("getText") })
    }

    /** 自闭合空坐标标签 */
    @Test
    fun selfClosingCoordinate_noNpe() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name>测试线</name>
      <Point>
        <coordinates/>
      </Point>
    </Placemark>
    <Placemark>
      <name>测试线 1号塔</name>
      <Point>
        <coordinates>102.70,25.04</coordinates>
      </Point>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml)
        assertFalse("不应 NPE", r.errors.any { it.message.contains("getText") })
    }

    /** MultiGeometry 多几何 */
    @Test
    fun multiGeometry_parsed() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name>220kV某线</name>
      <MultiGeometry>
        <Point><coordinates>102.70,25.04</coordinates></Point>
        <Point><coordinates>116.40,39.90</coordinates></Point>
        <LineString><coordinates>102.70,25.04 116.40,39.90</coordinates></LineString>
      </MultiGeometry>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml)
        assertFalse("应解析到线路，实际: ${r.lines.size} 条, errors: ${r.errors.map { it.message }}", r.lines.isEmpty())
        assertTrue("杆塔数应 >=2，实际: ${r.towerCount}", r.towerCount >= 2)
    }

    /** ExtendedData 含电压字段 */
    @Test
    fun extendedData_voltage_parsed() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name>某线 1号塔</name>
      <ExtendedData>
        <Data name="voltage"><value>500</value></Data>
      </ExtendedData>
      <Point><coordinates>102.70,25.04</coordinates></Point>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml)
        assertTrue("应解析到线路", r.lines.isNotEmpty())
        assertEquals(500, r.lines.first().voltageLevel)
    }

    /** CDATA 描述 */
    @Test
    fun cdataDescription_parsed() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name>1号塔</name>
      <description><![CDATA[耐张塔，备注信息]]></description>
      <Point><coordinates>102.70,25.04</coordinates></Point>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml)
        assertFalse("不应 NPE", r.errors.any { it.message.contains("getText") })
    }

    /** SchemaData 含电压字段 */
    @Test
    fun schemaData_voltage_parsed() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name>某线 1号塔</name>
      <ExtendedData>
        <SchemaData schemaUrl="#S">
          <SimpleData name="voltage">220</SimpleData>
        </SchemaData>
      </ExtendedData>
      <Point><coordinates>102.70,25.04</coordinates></Point>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml)
        assertTrue("应解析到线路", r.lines.isNotEmpty())
        assertEquals(220, r.lines.first().voltageLevel)
    }

    // ==================================================================
    // v1.0.7 用户反馈回归测试
    // ==================================================================

    /**
     * 用户反馈 #2：同一 Placemark 内既有 LineString 又有 Point 时，
     * 每基杆塔不能出现两条记录（原来 Point + LineString 各生成一套）。
     */
    @Test
    fun lineStringWithPoints_noDuplicateTowers() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name>110kV重复点线</name>
      <MultiGeometry>
        <LineString><coordinates>102.70,25.04 102.71,25.05 102.72,25.06</coordinates></LineString>
        <Point><coordinates>102.70,25.04</coordinates></Point>
        <Point><coordinates>102.71,25.05</coordinates></Point>
        <Point><coordinates>102.72,25.06</coordinates></Point>
      </MultiGeometry>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml)
        assertEquals("每个坐标只应有一个杆塔，实际 ${r.towerCount}", 3, r.towerCount)
    }

    /**
     * 用户反馈 #3：导入后必须能连成线，且杆塔按 1..N 顺序排列（首基 → 末基）。
     */
    @Test
    fun towers_orderedAlongLineString() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name>220kV顺序线</name>
      <LineString><coordinates>102.70,25.04 102.71,25.05 102.72,25.06 102.73,25.07</coordinates></LineString>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml, fileName = "220kV顺序线")
        val line = r.lines.first()
        assertEquals(4, line.towers.size)
        assertEquals(listOf("1", "2", "3", "4"), line.towers.map { it.towerNumber })
        // 顺序必须与线坐标一致，否则连线会打结
        assertEquals(102.70, line.towers[0].lon, 1e-9)
        assertEquals(102.73, line.towers[3].lon, 1e-9)
        // 升序单调，保证连线不自交
        assertTrue(line.towers.zipWithNext().all { (a, b) -> b.lon > a.lon })
    }

    /**
     * 用户反馈 #6：KML 内没有电压信息时，按文件名自动识别电压等级。
     */
    @Test
    fun voltageFromFileName_whenKmlHasNone() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name>某线</name>
      <LineString><coordinates>102.70,25.04 102.71,25.05</coordinates></LineString>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        assertEquals(500, KmlParser.parse(kml, fileName = "500kV某某线").lines.first().voltageLevel)
        assertEquals(220, KmlParser.parse(kml, fileName = "220千伏某某线").lines.first().voltageLevel)
        assertEquals(35, KmlParser.parse(kml, fileName = "35kV试验线").lines.first().voltageLevel)
    }

    /** 纯 Point 序列（无 LineString）也要按出现顺序编号连线 */
    @Test
    fun pointsOnly_numberedInOrder() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark><name>110kV点线 1号塔</name><Point><coordinates>102.70,25.04</coordinates></Point></Placemark>
    <Placemark><name>110kV点线 2号塔</name><Point><coordinates>102.71,25.05</coordinates></Point></Placemark>
    <Placemark><name>110kV点线 3号塔</name><Point><coordinates>102.72,25.06</coordinates></Point></Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml, fileName = "110kV点线")
        val line = r.lines.first()
        assertEquals(3, line.towers.size)
        assertEquals(listOf("1", "2", "3"), line.towers.map { it.towerNumber })
    }

    /**
     * 用户反馈：首尾两基不要连线。
     * KML 线首尾坐标相同（GIS 导出常见闭环）时，去掉闭合尾点，
     * 避免第 1 基与最后 1 基之间多连一条边。
     */
    @Test
    fun closedLine_opened_noLoopBack() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name>110kV闭环线</name>
      <LineString><coordinates>102.70,25.04 102.71,25.05 102.72,25.06 102.70,25.04</coordinates></LineString>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml, fileName = "110kV闭环线")
        val line = r.lines.first()
        assertEquals("闭合尾点应被去掉，实际 ${line.towers.size} 基", 3, line.towers.size)
        // 首基与末基坐标不同（不闭环）
        assertTrue(
            "首末杆塔不应重合",
            kotlin.math.abs(line.towers.first().lon - line.towers.last().lon) > 1e-6 ||
                kotlin.math.abs(line.towers.first().lat - line.towers.last().lat) > 1e-6
        )
    }

    /**
     * 用户反馈：按文件里面的名称导入。
     * Folder 名应作为线路名，而不是被文件名覆盖。
     */
    @Test
    fun folderName_usedAsLineName() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>导出结果</name>
    <Folder>
      <name>220kV昆导线</name>
      <Placemark><name>01</name><Point><coordinates>102.70,25.04</coordinates></Point></Placemark>
      <Placemark><name>02</name><Point><coordinates>102.71,25.05</coordinates></Point></Placemark>
    </Folder>
    <Folder>
      <name>110kV海埂线</name>
      <Placemark><name>01</name><Point><coordinates>102.80,25.10</coordinates></Point></Placemark>
      <Placemark><name>02</name><Point><coordinates>102.81,25.11</coordinates></Point></Placemark>
    </Folder>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml, fileName = "随便起的文件名")
        assertEquals(2, r.lines.size)
        assertEquals(setOf("220kV昆导线", "110kV海埂线"), r.lines.map { it.name }.toSet())
        assertEquals(220, r.lines.first { it.name.contains("220") }.voltageLevel)
    }

    /**
     * 用户反馈：不要修改编号。
     * 文件里的 "0012" / "N35" 必须原样保留。
     */
    @Test
    fun originalTowerNumbers_preserved() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>110kV保号线</name>
    <Folder>
      <name>110kV保号线</name>
      <Placemark><name>0012</name><Point><coordinates>102.70,25.04</coordinates></Point></Placemark>
      <Placemark><name>N35</name><Point><coordinates>102.71,25.05</coordinates></Point></Placemark>
      <Placemark><name>8号塔</name><Point><coordinates>102.72,25.06</coordinates></Point></Placemark>
    </Folder>
  </Document>
</kml>
        """.trimIndent()

        val line = KmlParser.parse(kml, fileName = "110kV保号线").lines.first()
        assertEquals(listOf("0012", "N35", "8"), line.towers.map { it.towerNumber })
    }

    /** 多个 Folder 各是一条线：杆塔不能串线 */
    @Test
    fun multipleFolders_towersNotMixed() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Folder>
      <name>A线</name>
      <Placemark><name>1</name><Point><coordinates>102.70,25.04</coordinates></Point></Placemark>
    </Folder>
    <Folder>
      <name>B线</name>
      <Placemark><name>1</name><Point><coordinates>103.70,26.04</coordinates></Point></Placemark>
    </Folder>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml)
        assertEquals(2, r.lines.size)
        r.lines.forEach { assertEquals(1, it.towers.size) }
        assertEquals(103.70, r.lines.first { it.name == "B线" }.towers.first().lon, 1e-9)
    }

    /** GIS 导出的占位名 layer0 / 未命名 不应成为线路名，回退到文件名 */
    @Test
    fun placeholderDocName_fallsBackToFileName() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>layer0</name>
    <Placemark><name>1</name><Point><coordinates>102.70,25.04</coordinates></Point></Placemark>
    <Placemark><name>2</name><Point><coordinates>102.71,25.05</coordinates></Point></Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = KmlParser.parse(kml, fileName = "110kV真实线路名")
        assertEquals("110kV真实线路名", r.lines.first().name)
    }
}