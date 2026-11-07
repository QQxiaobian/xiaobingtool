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

    // ------------------------------------------------------------------
    // 无 <name>、只有 <description> 的行政区划数据
    // ------------------------------------------------------------------

    @Test
    fun `KML 只有 description 时用备注首段作为名称`() {
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml><Document><name>行政区划</name>
              <Placemark>
                <description>五华区，昆明市主城区之一，面积 381 平方公里</description>
                <Polygon><outerBoundaryIs><LinearRing>
                  <coordinates>102.70,25.05 102.72,25.05 102.72,25.07 102.70,25.05</coordinates>
                </LinearRing></outerBoundaryIs></Polygon>
              </Placemark>
            </Document></kml>
        """.trimIndent()

        val r = ThematicParser.parseKml(kml, "区划.kml")
        val f = r.layers.first().features.first()
        assertEquals("五华区", f.name)
        // 备注原文仍保留在属性里，点击信息卡可看全文
        assertEquals(true, f.properties["description"]?.startsWith("五华区") == true)
    }

    @Test
    fun `备注首段过长或是 URL 时不拿来当名称`() {
        val long = "本区域位于昆明市西北部，东经 102 度，北纬 25 度，包含多个街道与社区，总面积约 381 平方公里"
        assertEquals("", ThematicParser.nameFromDesc(long))
        assertEquals("", ThematicParser.nameFromDesc("https://example.com/area/530102"))
        assertEquals("", ThematicParser.nameFromDesc("530102.00"))
        assertEquals("盘龙区", ThematicParser.nameFromDesc("盘龙区\n面积 340 平方公里"))
    }

    @Test
    fun `图层内多要素无名称时每个面都用备注摘要做标注`() {
        val props = """{"description":"西山区，昆明市主城西部"}"""
        val text = com.kunming.powerline.ui.map.ThematicLabels.compose(
            name = null,
            propertiesJson = props,
            layerName = "行政区划",
            layerRemark = null,
            kind = "polygon",
            featureCount = 14
        )
        assertEquals("西山区", text)
    }

    @Test
    fun `备注摘要按标点截断且不超过 12 字`() {
        val t = com.kunming.powerline.ui.map.ThematicLabels.summaryOf("昆明市呈贡区人民政府驻地，海拔 1900 米")
        assertEquals("昆明市呈贡区人民政府驻地", t)
        val long = com.kunming.powerline.ui.map.ThematicLabels.summaryOf("一二三四五六七八九十十一十二十三")
        assertEquals("一二三四五六七八九十十一…", long)
    }

}
