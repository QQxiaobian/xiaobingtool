package com.kunming.powerline

import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.data.local.entity.TowerEntity
import com.kunming.powerline.util.TowerVisibility
import org.junit.Assert.assertEquals
import org.junit.Test

class TowerVisibilityTest {

    private fun line(id: String, voltage: Int) =
        LineEntity(id = id, name = id, voltageLevel = voltage)

    private fun tower(id: String, lineId: String) =
        TowerEntity(id = id, lineId = lineId, towerNumber = id, lon = 102.7, lat = 25.0)

    @Test
    fun `全部电压等级开启时杆塔全部保留`() {
        val lines = listOf(line("L1", 220), line("L2", 110))
        val towers = listOf(tower("T1", "L1"), tower("T2", "L2"), tower("T3", "L1"))
        val got = TowerVisibility.filter(towers, lines, setOf(500, 220, 110, 35))
        assertEquals(towers.map { it.id }, got.map { it.id })
    }

    @Test
    fun `关闭某电压等级后其杆塔不再显示且顺序不变`() {
        val lines = listOf(line("L1", 220), line("L2", 110), line("L3", 500))
        val towers = listOf(
            tower("T1", "L1"), tower("T2", "L2"),
            tower("T3", "L1"), tower("T4", "L3")
        )
        val got = TowerVisibility.filter(towers, lines, setOf(110, 500))
        assertEquals(listOf("T2", "T4"), got.map { it.id })
    }

    @Test
    fun `筛选集合为空时不绘制任何杆塔`() {
        val lines = listOf(line("L1", 220))
        val towers = listOf(tower("T1", "L1"))
        assertEquals(emptyList<TowerEntity>(), TowerVisibility.filter(towers, lines, emptySet()))
    }

    @Test
    fun `找不到所属线路的杆塔按默认等级处理`() {
        val towers = listOf(tower("T1", "MISSING"))
        val lineMap = emptyMap<String, LineEntity>()
        assertEquals(TowerVisibility.FALLBACK_VOLTAGE, TowerVisibility.voltageOf(lineMap, towers[0]))
        assertEquals(1, TowerVisibility.filter(towers, emptyList(), setOf(110)).size)
        assertEquals(0, TowerVisibility.filter(towers, emptyList(), setOf(220)).size)
    }

    @Test
    fun `电压等级取自所属线路`() {
        val lineMap = listOf(line("L1", 500), line("L2", 35)).associateBy { it.id }
        assertEquals(500, TowerVisibility.voltageOf(lineMap, tower("T1", "L1")))
        assertEquals(35, TowerVisibility.voltageOf(lineMap, tower("T2", "L2")))
    }
}
