package com.kunming.powerline

import com.kunming.powerline.util.GlobalSearch
import com.kunming.powerline.util.SearchRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全局搜索内核（1.6.3）的纯 JVM 单元测试。
 *
 * 覆盖：所有图层类型可检索、名称/备注匹配、多关键词 AND、大小写、
 * 排序、无坐标结果、去重、结果上限，以及 GeoJSON 首坐标解析。
 */
class GlobalSearchTest {

    private val allLayers = listOf(
        SearchRecord("220kV某甲线", "线路 · 220kV · 12 基杆塔", 102.7, 25.0,
            "line", "L1", 13.0, listOf("某甲线", "甲变电站", "乙变电站"), 0),
        SearchRecord("220kV某甲线 12", "杆塔 · 220kV某甲线", 102.71, 25.01,
            "tower", "T12", 17.0, listOf("12", "耐张"), 10),
        SearchRecord("五华区", "专题要素 · 行政区划 · 面", 102.72, 25.02,
            "thematic", "F1", 13.0, listOf("五华区", "面积 12.3 平方公里"), 12),
        SearchRecord("施工隐患", "危险点 · Ⅰ 级", 102.73, 25.03,
            "danger", "D1", 17.0, listOf("施工", "吊车"), 20),
        SearchRecord("跨越昆磨高速", "交叉跨越 · 道路", 102.74, 25.04,
            "crossing", "C1", 17.0, listOf("高速公路"), 20),
        SearchRecord("塔头金具", "现场照片", 102.75, 25.05,
            "photo", "P1", 17.0, listOf("金具", "缺陷"), 30),
        SearchRecord("巡视范围A", "自定义标注 · polygon", 102.76, 25.06,
            "overlay", "O1", 16.0, listOf("通道", "A区"), 25),
        SearchRecord("行政区划.kml", "专题图层 · 面 · 16 个要素", 102.77, 25.07,
            "thematic-layer", "LY1", 13.0, listOf("昆明", "kml"), 8),
        SearchRecord("2026-巡视台账.xlsx", "导入批次 · excel · 40 基杆塔", null, null,
            "batch", "B1", 16.0, listOf("台账"), 60)
    )

    @Test
    fun `全局搜索覆盖所有图层类型`() {
        val kinds = GlobalSearch.match("", allLayers)
        assertTrue(kinds.isEmpty())

        // 每种类型都能被自己的关键词命中
        val cases = listOf(
            "某甲线" to "line", "耐张" to "tower", "五华区" to "thematic",
            "吊车" to "danger", "高速公路" to "crossing", "金具" to "photo",
            "通道" to "overlay", "昆明" to "thematic-layer", "台账" to "batch"
        )
        cases.forEach { (kw, kind) ->
            val hits = GlobalSearch.match(kw, allLayers)
            assertTrue("关键词「$kw」应命中 $kind", hits.any { it.kind == kind })
        }
    }

    @Test
    fun `备注与附加字段也能命中`() {
        val hits = GlobalSearch.match("甲变电站", allLayers)
        assertEquals(1, hits.size)
        assertEquals("line", hits.first().kind)
        assertEquals("220kV某甲线", hits.first().title)
    }

    @Test
    fun `多关键词需同时匹配`() {
        assertEquals(0, GlobalSearch.match("五华区 吊车", allLayers).size)
        assertEquals(1, GlobalSearch.match("五华区 面积", allLayers).size)
        // 中文逗号与顿号等同空格
        assertEquals(1, GlobalSearch.match("五华区，面积", allLayers).size)
    }

    @Test
    fun `大小写不敏感`() {
        val rec = listOf(SearchRecord("KunMing 220kV Line", "线路", 102.0, 25.0,
            "line", "L9", keywords = listOf("YunNan")))
        assertEquals(1, GlobalSearch.match("kunming", rec).size)
        assertEquals(1, GlobalSearch.match("YUNNAN", rec).size)
    }

    @Test
    fun `名称精确匹配优先于仅备注命中`() {
        val recs = listOf(
            SearchRecord("备注里有五华区三个字", "线路", 102.0, 25.0,
                "line", "A", keywords = listOf("五华区"), priority = 0),
            SearchRecord("五华区", "专题要素", 102.1, 25.1,
                "thematic", "B", priority = 50)
        )
        val hits = GlobalSearch.match("五华区", recs)
        assertEquals("五华区", hits.first().title)
        assertEquals("thematic", hits.first().kind)
    }

    @Test
    fun `有坐标的结果优先于无坐标结果`() {
        val recs = listOf(
            SearchRecord("无坐标结果", "导入批次", null, null, "batch", "N1", priority = 0),
            SearchRecord("有坐标结果", "专题要素", 102.0, 25.0, "thematic", "N2", priority = 50)
        )
        val hits = GlobalSearch.match("结果", recs)
        assertEquals("有坐标结果", hits.first().title)
        assertEquals(0.0, hits.last().lon, 0.0)
        assertEquals(0.0, hits.last().lat, 0.0)
    }

    @Test
    fun `按 kind refId 标题去重`() {
        val dup = allLayers + allLayers.first()
        val hits = GlobalSearch.match("某甲线", dup)
        val keys = hits.map { "${it.kind}|${it.refId}|${it.title}" }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `结果数量不超过上限`() {
        val many = (1..260).map { i ->
            SearchRecord("要素$i", "专题要素", 102.0 + i * 0.0001, 25.0,
                "thematic", "F$i", keywords = listOf("批量"))
        }
        val hits = GlobalSearch.match("要素", many)
        assertEquals(GlobalSearch.MAX_RESULTS, hits.size)
    }

    @Test
    fun `空查询与纯空白返回空结果`() {
        assertTrue(GlobalSearch.match("", allLayers).isEmpty())
        assertTrue(GlobalSearch.match("   ", allLayers).isEmpty())
    }

    @Test
    fun `firstCoordinate 支持各种 GeoJSON 几何`() {
        assertEquals(102.7 to 25.0,
            GlobalSearch.firstCoordinate("""{"type":"Point","coordinates":[102.7,25.0]}"""))
        assertEquals(102.7 to 25.0,
            GlobalSearch.firstCoordinate("""{"type":"LineString","coordinates":[[102.7,25.0],[102.8,25.1]]}"""))
        assertEquals(102.7 to 25.0, GlobalSearch.firstCoordinate(
            """{"type":"Polygon","coordinates":[[[102.7,25.0],[102.8,25.0],[102.8,25.1]]]}"""))
        assertEquals(102.7 to 25.0, GlobalSearch.firstCoordinate(
            """{"type":"MultiPolygon","coordinates":[[[[102.7,25.0],[102.8,25.0]]]]}"""))
    }

    @Test
    fun `firstCoordinate 容错异常输入`() {
        assertNull(GlobalSearch.firstCoordinate(null))
        assertNull(GlobalSearch.firstCoordinate(""))
        assertNull(GlobalSearch.firstCoordinate("not json"))
        assertNull(GlobalSearch.firstCoordinate("""{"type":"Point","coordinates":[]}"""))
    }

    @Test
    fun `geometryLabel 中文标签`() {
        assertEquals("点", GlobalSearch.geometryLabel("point"))
        assertEquals("面", GlobalSearch.geometryLabel("polygon"))
        assertEquals("混合", GlobalSearch.geometryLabel("mixed"))
        assertEquals("要素", GlobalSearch.geometryLabel(null))
    }

    @Test
    fun `命中结果保留原始副标题与缩放级别`() {
        val hit = GlobalSearch.match("五华区", allLayers).first()
        assertEquals("专题要素 · 行政区划 · 面", hit.subtitle)
        assertEquals(13.0, hit.zoom, 0.0)
        assertEquals("F1", hit.refId)
        assertFalse(hit.title.isBlank())
    }
}
