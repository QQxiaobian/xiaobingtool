package com.kunming.powerline

import com.kunming.powerline.data.parser.ThematicParser
import com.kunming.powerline.ui.map.ThematicLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 区域（面）图层名称链路：KML -> 解析 -> 地图标注文案
 *
 * 用户反馈"区域名称不显示"，这里把链路各环节钉死，便于定位是解析丢了名字
 * 还是渲染阶段被过滤掉。
 */
class ThematicAreaLabelTest {

    private val areaKml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <name>专项巡视.kml</name>
            <Folder>
              <name>重点区域范围</name>
              <Placemark>
                <description><![CDATA[雨季易滑坡，需重点巡查]]></description>
                <Polygon>
                  <outerBoundaryIs>
                    <LinearRing>
                      <coordinates>102.70,25.04,0 102.72,25.04,0 102.72,25.06,0 102.70,25.06,0 102.70,25.04,0</coordinates>
                    </LinearRing>
                  </outerBoundaryIs>
                </Polygon>
              </Placemark>
            </Folder>
            <Folder>
              <name>杆塔通道</name>
              <Placemark>
                <name>通道 A 段</name>
                <LineString>
                  <coordinates>102.70,25.04,0 102.75,25.08,0</coordinates>
                </LineString>
              </Placemark>
            </Folder>
          </Document>
        </kml>
    """.trimIndent()

    @Test
    fun `无名字的面要素按 Folder 归层_并判定为面图层`() {
        val r = ThematicParser.parseKml(areaKml, "专项巡视.kml")
        val area = r.layers.firstOrNull { it.name == "重点区域范围" }
        assertTrue("应按 Folder 生成区域图层，实际=${r.layers.map { it.name }}", area != null)
        assertEquals("polygon", area!!.geometryType)
        assertEquals(1, area.features.size)
        // 无 <name> 但有 <description>：用备注首段当名称，避免整层无标注
        assertEquals("雨季易滑坡", area.features[0].name)
        assertEquals("polygon", area.features[0].kind)
    }

    @Test
    fun `无名字的区域用备注合成标注`() {
        val r = ThematicParser.parseKml(areaKml, "专项巡视.kml")
        val layer = r.layers.first { it.name == "重点区域范围" }
        val f = layer.features[0]
        val label = ThematicLabels.compose(
            name = f.name,
            propertiesJson = propsJson(f.properties),
            layerName = layer.name,
            layerRemark = layer.remark,
            kind = f.kind,
            featureCount = layer.features.size
        )
        assertTrue("区域标注不能为空，实际=[$label]", label.isNotEmpty())
        // 名称行是备注首段，第二行去掉重复前缀只留后半句
        assertEquals("雨季易滑坡\n需重点巡查", label)
    }

    @Test
    fun `既无名称也无备注的区域用图层名兜底`() {
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml><Document><name>专项巡视</name>
              <Folder><name>重点区域范围</name>
                <Placemark>
                  <Polygon><outerBoundaryIs><LinearRing>
                    <coordinates>102.70,25.04 102.72,25.04 102.72,25.06 102.70,25.04</coordinates>
                  </LinearRing></outerBoundaryIs></Polygon>
                </Placemark>
              </Folder>
            </Document></kml>
        """.trimIndent()
        val r = ThematicParser.parseKml(kml, "专项巡视.kml")
        val layer = r.layers.first { it.name == "重点区域范围" }
        val f = layer.features[0]
        assertEquals(null, f.name)
        val label = ThematicLabels.compose(
            name = f.name,
            propertiesJson = propsJson(f.properties),
            layerName = layer.name,
            layerRemark = layer.remark,
            kind = f.kind,
            featureCount = layer.features.size
        )
        assertEquals("重点区域范围", label)
    }

    @Test
    fun `有名字的通道线要素保留原名字`() {
        val r = ThematicParser.parseKml(areaKml, "专项巡视.kml")
        val layer = r.layers.first { it.name == "杆塔通道" }
        assertEquals("线图层", if (layer.geometryType == "line") "线图层" else layer.geometryType)
        assertEquals("通道 A 段", layer.features[0].name)
    }

    /** 与 ThematicRepository 落库时的拼装方式保持一致 */
    private fun propsJson(m: Map<String, String>): String? =
        m.takeIf { it.isNotEmpty() }?.entries?.joinToString(",", "{", "}") { (k, v) ->
            "\"${k.replace("\"", "\\\"")}\":\"${v.replace("\"", "\\\"")}\""
        }
}
