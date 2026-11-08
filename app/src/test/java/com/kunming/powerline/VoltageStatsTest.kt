package com.kunming.powerline

import com.kunming.powerline.util.VoltageStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoltageStatsTest {

    @Test
    fun `空列表返回四个等级全为 0`() {
        val m = VoltageStats.countByLevel(emptyList())
        assertEquals(setOf(500, 220, 110, 35), m.keys)
        assertTrue(m.values.all { it == 0 })
    }

    @Test
    fun `混合电压等级按等级分别计数`() {
        val m = VoltageStats.countByLevel(listOf(500, 220, 220, 110, 35, 220, 500))
        assertEquals(2, m[500])
        assertEquals(3, m[220])
        assertEquals(1, m[110])
        assertEquals(1, m[35])
    }

    @Test
    fun `未知电压等级被忽略且不产生额外 key`() {
        val m = VoltageStats.countByLevel(listOf(1000, 10, 66, 220, -1))
        assertEquals(1, m[220])
        assertEquals(4, m.keys.size)
        assertEquals(0, m[500])
    }

    @Test
    fun `统计总数等于有效线路数`() {
        val input = listOf(500, 220, 110, 35, 35, 35, 66)
        val m = VoltageStats.countByLevel(input)
        assertEquals(input.count { it in VoltageStats.LEVELS }, m.values.sum())
    }

    @Test
    fun `条数文案带单位`() {
        assertEquals("12 条", VoltageStats.label(12))
        assertEquals("0 条", VoltageStats.label(0))
    }
}
