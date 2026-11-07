package com.kunming.powerline

import com.kunming.powerline.data.parser.ThematicParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 专题图层解析测试：点 / 线 / 面原样保留，按 Folder 分图层，名称不改
 */
class ThematicParseTest {

    @Test
    fun pointsInFolder_oneLayerNamedByFolder() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>巡视成果</name>
    <Folder>
      <name>220kV昆导线通道巡视点</name>
      <Placemark><name>巡视点A</name><Point><coordinates>102.70,25.04</coordinates></Point></Placemark>
      <Placemark><name>巡视点B</name><Point><coordinates>102.71,25.05</coordinates></Point></Placemark>
    </Folder>
  </Document>
</kml>
        """.trimIndent()

        val r = ThematicParser.parseKml(kml, "随便的文件名")
        assertEquals(1, r.layers.size)
        assertEquals("220kV昆导线通道巡视点", r.layers.first().name)
        assertEquals(2, r.featureCount)
        assertEquals("point", r.layers.first().geometryType)
        assertEquals(listOf("巡视点A", "巡视点B"), r.layers.first().features.map { it.name })
    }

    @Test
    fun polygon_keptAsPolygonAndRingClosed() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>区域</name>
    <Placemark>
      <name>一号责任区</name>
      <Polygon><outerBoundaryIs><LinearRing><coordinates>
        102.70,25.04 102.72,25.04 102.72,25.06 102.70,25.04
      </coordinates></LinearRing></outerBoundaryIs></Polygon>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = ThematicParser.parseKml(kml, "区域")
        val f = r.layers.first().features.first()
        assertEquals("polygon", f.kind)
        assertEquals("一号责任区", f.name)
        assertTrue(f.geometryJson.startsWith("""{"type":"Polygon""""))
        assertTrue(f.lon != null && f.lat != null)
    }

    @Test
    fun line_keptAsLine() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>通道</name>
    <Placemark>
      <name>通道1</name>
      <LineString><coordinates>102.70,25.04 102.71,25.05 102.72,25.06</coordinates></LineString>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = ThematicParser.parseKml(kml, "通道")
        assertEquals("line", r.layers.first().features.first().kind)
        assertEquals(1, r.featureCount)
    }

    @Test
    fun multiGeometry_expandedToMultipleFeatures() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>混合</name>
    <Placemark>
      <name>综合点位</name>
      <MultiGeometry>
        <Point><coordinates>102.70,25.04</coordinates></Point>
        <LineString><coordinates>102.70,25.04 102.71,25.05</coordinates></LineString>
      </MultiGeometry>
    </Placemark>
  </Document>
</kml>
        """.trimIndent()

        val r = ThematicParser.parseKml(kml, "混合")
        assertEquals(2, r.featureCount)
        assertEquals("mixed", r.layers.first().geometryType)
    }

    @Test
    fun multipleFolders_multipleLayers() = runBlocking {
        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Folder><name>巡视点</name>
      <Placemark><name>P1</name><Point><coordinates>102.70,25.04</coordinates></Point></Placemark>
    </Folder>
    <Folder><name>责任区</name>
      <Placemark><name>Z1</name><Polygon><outerBoundaryIs><LinearRing>
        <coordinates>102.80,25.10 102.82,25.10 102.82,25.12 102.80,25.10</coordinates>
      </LinearRing></outerBoundaryIs></Polygon></Placemark>
    </Folder>
  </Document>
</kml>
        """.trimIndent()

        val r = ThematicParser.parseKml(kml, "文件")
        assertEquals(2, r.layers.size)
        assertEquals(setOf("巡视点", "责任区"), r.layers.map { it.name }.toSet())
    }
}
