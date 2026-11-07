package com.kunming.powerline

import com.kunming.powerline.data.local.entity.ThematicLayerEntity
import com.kunming.powerline.ui.map.ThematicLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 专题图层地图名称合成规则
 *
 * 名称来源于 KML/GeoJSON，格式很乱：有的 Placemark 有名、有的整层无名、
 * 备注藏在 description 里且带 HTML。这里锁定几条硬规则，防止以后改坏。
 */
class ThematicLabelTest {

    @Test
    fun `要素有名字时直接用名字`() {
        assertEquals(
            "李家山巡视区",
            ThematicLabels.compose(
                name = "李家山巡视区", layerName = "区域图层", kind = "polygon", featureCount = 1
            )
        )
    }

    @Test
    fun `名字前后空格会被裁掉`() {
        assertEquals(
            "35kV 城北线",
            ThematicLabels.compose(
                name = "  35kV 城北线  ", kind = "line", featureCount = 3
            )
        )
    }

    @Test
    fun `备注作为第二行显示`() {
        val props = """{"description":"需重点巡查边坡","name":"A 区"}"""
        val label = ThematicLabels.compose(
            name = "A 区", propertiesJson = props, layerName = "区域图层", featureCount = 1
        )
        assertEquals("A 区\n需重点巡查边坡", label)
    }

    @Test
    fun `中文备注字段也能识别`() {
        val props = """{"备注":"雨季易滑坡"}"""
        assertEquals(
            "B 区\n雨季易滑坡",
            ThematicLabels.compose(name = "B 区", propertiesJson = props, featureCount = 1)
        )
    }

    @Test
    fun `备注与名称相同时不重复`() {
        val props = """{"description":"C 区"}"""
        assertEquals("C 区", ThematicLabels.compose(name = "C 区", propertiesJson = props, featureCount = 1))
    }

    @Test
    fun `过长的备注会截断`() {
        val long = "一".repeat(60)
        val props = """{"description":"$long"}"""
        val label = ThematicLabels.compose(name = "D 区", propertiesJson = props, featureCount = 1)
        val second = label.split("\n")[1]
        assertEquals(ThematicLabels.MAX_DESC_CHARS + 1, second.length) // +1 是省略号
        assertTrue(second.endsWith("…"))
    }

    @Test
    fun `HTML 备注会被清洗成单行`() {
        val props = """{"description":"<b>重点</b>&nbsp;巡查<br/>区域"}"""
        assertEquals(
            "E 区\n重点 巡查 区域",
            ThematicLabels.compose(name = "E 区", propertiesJson = props, featureCount = 1)
        )
    }

    @Test
    fun `面要素无名字时退回图层名`() {
        assertEquals(
            "输电通道",
            ThematicLabels.compose(layerName = "输电通道", kind = "polygon", featureCount = 1)
        )
    }

    @Test
    fun `点要素无名字时不显示图层名`() {
        assertEquals(
            "",
            ThematicLabels.compose(layerName = "巡视点位", kind = "point", featureCount = 20)
        )
    }

    @Test
    fun `要素过多时不做图层名兜底`() {
        assertEquals(
            "",
            ThematicLabels.compose(
                layerName = "输电通道",
                kind = "polygon",
                featureCount = ThematicLabels.FALLBACK_MAX_FEATURES + 1
            )
        )
    }

    @Test
    fun `多面图层无名字时不逐面显示图层名`() {
        // 行政区划这类整层无名数据，若每个面都写图层名会糊满屏，
        // 由渲染层的「整层标注」补一条即可
        assertEquals(
            "",
            ThematicLabels.compose(layerName = "行政区划", kind = "polygon", featureCount = 2)
        )
    }

    @Test
    fun `行政区划每个面都能显示自己的名字`() {
        assertEquals(
            "五华区",
            ThematicLabels.compose(
                name = "五华区",
                propertiesJson = """{"name":"五华区","adcode":"530102"}""",
                layerName = "昆明市",
                kind = "polygon",
                featureCount = 14
            )
        )
    }

    @Test
    fun `点击信息卡能读到完整备注而不截断`() {
        val long = "该区域为 2024 年汛期重点巡视区，包含 12 基杆塔，雨季每周巡视一次"
        val props = """{"description":"$long","adcode":"530102"}"""
        assertEquals(long, ThematicLabels.descriptionOf(props))
    }

    @Test
    fun `空字符串名字等同无名`() {
        assertEquals(
            "",
            ThematicLabels.compose(name = "   ", layerName = "巡视点位", kind = "point", featureCount = 1)
        )
    }

    @Test
    fun `脏 JSON 不影响渲染`() {
        assertEquals(
            "F 区",
            ThematicLabels.compose(name = "F 区", propertiesJson = "{不是 json", featureCount = 1)
        )
        assertTrue(ThematicLabels.parseProps("{].oops").isEmpty())
    }

    @Test
    fun `备注优先级 description 高于 note`() {
        val props = """{"note":"次要","description":"主要"}"""
        assertEquals("G\n主要", ThematicLabels.compose(name = "G", propertiesJson = props, featureCount = 1))
    }

    @Test
    fun `单区域图层把图层备注显示为第二行`() {
        assertEquals(
            "李家山区域\n汛期重点巡视",
            ThematicLabels.compose(
                name = "李家山区域", layerName = "区域", layerRemark = "汛期重点巡视",
                kind = "polygon", featureCount = 1
            )
        )
    }

    @Test
    fun `多面图层不重复显示图层备注`() {
        assertEquals(
            "P1",
            ThematicLabels.compose(
                name = "P1", layerName = "区域", layerRemark = "汛期重点巡视",
                kind = "polygon", featureCount = 30
            )
        )
    }

    @Test
    fun `要素自身备注优先于图层备注`() {
        assertEquals(
            "P1\n点位说明",
            ThematicLabels.compose(
                name = "P1", propertiesJson = """{"remark":"点位说明"}""",
                layerName = "区域", layerRemark = "图层备注",
                kind = "polygon", featureCount = 1
            )
        )
    }

    @Test
    fun `图层透明度百分比换算`() {
        val layer = ThematicLayerEntity(
            id = "TL-1", name = "区域", geometryType = "polygon",
            fillOpacity = 0.35f
        )
        assertEquals(35, layer.opacityPercent)
        assertTrue(layer.hasPolygon)
    }

    @Test
    fun `点图层不提供填充透明度`() {
        val layer = ThematicLayerEntity(id = "TL-2", name = "点位", geometryType = "point")
        assertTrue(!layer.hasPolygon)
    }

    @Test
    fun `混合图层也允许调透明度`() {
        val layer = ThematicLayerEntity(id = "TL-3", name = "混合", geometryType = "mixed")
        assertTrue(layer.hasPolygon)
    }
}
