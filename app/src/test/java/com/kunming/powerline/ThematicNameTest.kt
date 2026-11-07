package com.kunming.powerline

import com.kunming.powerline.data.parser.ThematicParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 行政区划类 KML / GeoJSON 的取名链路：
 * 名称常常不在 `<name>` 里，而在 ExtendedData / SimpleData 或 properties 的各个键上。
 */
class ThematicNameTest {

    @Test
    fun `KML SimpleData 里的名称能作为要素名`() {
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>昆明市行政区划</name>
                <Placemark>
                  <ExtendedData>
                    <SchemaData schemaUrl="#S">
                      <SimpleData name="name">五华区</SimpleData>
                      <SimpleData name="adcode">530102</SimpleData>
                    </SchemaData>
                  </ExtendedData>
                  <Polygon><outerBoundaryIs><LinearRing><coordinates>
                    102.70,25.05 102.72,25.05 102.72,25.07 102.70,25.05
                  </coordinates></LinearRing></outerBoundaryIs></Polygon>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val r = ThematicParser.parseKml(kml, "昆明市.kml")
        assertEquals(1, r.layers.size)
        val f = r.layers.first().features.first()
        assertEquals("五华区", f.name)
        assertEquals("530102", f.properties["adcode"])
    }

    @Test
    fun `KML Data value 里的名称能作为要素名`() {
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml><Document><name>区县</name>
              <Placemark>
                <ExtendedData><Data name="名称"><value>盘龙区</value></Data></ExtendedData>
                <Polygon><outerBoundaryIs><LinearRing>
                  <coordinates>102.72,25.05 102.74,25.05 102.74,25.07 102.72,25.05</coordinates>
                </LinearRing></outerBoundaryIs></Polygon>
              </Placemark>
            </Document></kml>
        """.trimIndent()

        val r = ThematicParser.parseKml(kml, "区县.kml")
        assertEquals("盘龙区", r.layers.first().features.first().name)
    }

    @Test
    fun `Placemark 的 name 优先于扩展属性`() {
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml><Document><name>区县</name>
              <Placemark>
                <name>官渡区</name>
                <ExtendedData><SimpleData name="name">错误名称</SimpleData></ExtendedData>
                <Polygon><outerBoundaryIs><LinearRing>
                  <coordinates>102.74,25.05 102.76,25.05 102.76,25.07 102.74,25.05</coordinates>
                </LinearRing></outerBoundaryIs></Polygon>
              </Placemark>
            </Document></kml>
        """.trimIndent()

        val r = ThematicParser.parseKml(kml, "区县.kml")
        assertEquals("官渡区", r.layers.first().features.first().name)
    }

    @Test
    fun `属性名称键优先取 name 再取中文名称`() {
        assertEquals("西山区", ThematicParser.nameFromAttrs(mapOf("名称" to "西山区", "adcode" to "530112")))
        assertEquals("A 区", ThematicParser.nameFromAttrs(mapOf("NAME" to "A 区")))
        assertEquals("B 区", ThematicParser.nameFromAttrs(mapOf("title" to "B 区")))
        // 只有形如 xxxnamexxx 的键时也能兜底
        assertEquals("C 区", ThematicParser.nameFromAttrs(mapOf("NAME_CHN" to "C 区")))
        assertEquals("", ThematicParser.nameFromAttrs(mapOf("adcode" to "530102")))
    }

}
