package com.kunming.powerline

import com.kunming.powerline.data.local.entity.DangerPointEntity
import com.kunming.powerline.util.DangerFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 危险点筛选（1.6.4）：净空距离范围 + 植被 / 地物类型。
 */
class DangerFilterTest {

    private fun point(
        id: String,
        clearance: Double?,
        ground: String? = "植被",
        veg: String? = null,
        level: String = "重大"
    ) = DangerPointEntity(
        id = id,
        type = ground ?: "危险点",
        level = level,
        lon = 102.86, lat = 24.85,
        clearanceM = clearance,
        groundType = ground,
        vegetationType = veg,
        defectLevel = level
    )

    private val data = listOf(
        point("a", 6.51, "植被"),
        point("b", 10.34, "植被", veg = "桉树"),
        point("c", 12.74, "建筑物", level = "紧急"),
        point("d", null, "植被")
    )

    @Test
    fun `空筛选返回全部`() {
        val f = DangerFilter()
        assertFalse(f.isActive)
        assertEquals(4, f.apply(data).size)
    }

    @Test
    fun `只筛上限 低于等于阈值的点保留`() {
        val f = DangerFilter(maxClearance = 10.34)
        val r = f.apply(data).map { it.id }
        assertEquals(listOf("a", "b"), r)
    }

    @Test
    fun `区间筛选包含边界`() {
        val f = DangerFilter(minClearance = 6.51, maxClearance = 12.74)
        assertEquals(listOf("a", "b", "c"), f.apply(data).map { it.id })
    }

    @Test
    fun `启用数值筛选时无净空值的点不显示`() {
        val f = DangerFilter(minClearance = 0.0)
        assertFalse(f.apply(data).any { it.id == "d" })
    }

    @Test
    fun `按植被类型筛选`() {
        val f = DangerFilter(categories = setOf("桉树"))
        assertEquals(listOf("b"), f.apply(data).map { it.id })
    }

    @Test
    fun `按地物类型筛选 多选`() {
        // b 有独立植被类型「桉树」，归到桉树，不再算作「植被」
        val f = DangerFilter(categories = setOf("植被", "建筑物"))
        assertEquals(3, f.apply(data).size)
        val only = DangerFilter(categories = setOf("建筑物"))
        assertEquals(listOf("c"), only.apply(data).map { it.id })
    }

    @Test
    fun `类型与距离组合筛选`() {
        val f = DangerFilter(maxClearance = 11.0, categories = setOf("植被"))
        assertEquals(listOf("a"), f.apply(data).map { it.id })
    }

    @Test
    fun `类型统计按数量降序`() {
        val cats = DangerFilter.categoriesOf(data)
        assertEquals("植被" to 2, cats.first())
        assertTrue(cats.any { it.first == "建筑物" })
    }

    @Test
    fun `排序 净空最小在前 无值最后`() {
        val s = DangerFilter().sorted(data)
        assertEquals(listOf("a", "b", "c", "d"), s.map { it.id })
    }

    @Test
    fun `文本边界解析`() {
        assertEquals(7.0, DangerFilter.parseBound("7")!!, 1e-9)
        assertEquals(6.5, DangerFilter.parseBound(" 6.5 ")!!, 1e-9)
        assertEquals(null, DangerFilter.parseBound(""))
    }

    @Test
    fun `地图标注为地物类型加净空距离`() {
        assertEquals("植被 6.51m", point("a", 6.51).mapLabel)
        assertEquals("植被 7m", point("a", 7.0).mapLabel)
        assertEquals("植被", point("a", null).mapLabel)
    }

    @Test
    fun `缺陷等级配色 重大红 紧急橙 一般黄`() {
        assertEquals("#D32F2F", point("a", 1.0, level = "重大").levelColor)
        assertEquals("#F57C00", point("a", 1.0, level = "紧急").levelColor)
        assertEquals("#FBC02D", point("a", 1.0, level = "一般").levelColor)
    }
}
