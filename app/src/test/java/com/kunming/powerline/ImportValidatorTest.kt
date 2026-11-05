package com.kunming.powerline

import com.kunming.powerline.data.parser.ImportLine
import com.kunming.powerline.data.parser.ImportTower
import com.kunming.powerline.data.parser.ImportValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入校验规则测试（规范 10.8）
 */
class ImportValidatorTest {

    private fun tower(no: String, lon: Double = 102.7123, lat: Double = 25.0401, row: Int = 1) =
        ImportTower(towerNumber = no, lon = lon, lat = lat, sourceRow = row)

    private fun line(name: String = "110kV 测试线", v: Int = 110, vararg ts: ImportTower) =
        ImportLine(name = name, voltageLevel = v, towers = ts.toMutableList())

    @Test
    fun validLine_passes() {
        val (ok, err) = ImportValidator.validate(listOf(line("110kV 测试线", 110, tower("1"), tower("2"))))
        assertEquals(1, ok.size)
        assertEquals(2, ok[0].towers.size)
        assertTrue(err.isEmpty())
    }

    @Test
    fun blankName_rejected() {
        val (ok, err) = ImportValidator.validate(listOf(line("  ", 110, tower("1"))))
        assertTrue(ok.isEmpty())
        assertTrue(err.isNotEmpty())
    }

    @Test
    fun invalidVoltage_rejected() {
        val (ok, err) = ImportValidator.validate(listOf(line("测试线", 66, tower("1"))))
        assertTrue(ok.isEmpty())
        assertTrue(err.any { it.message.contains("电压等级") })
    }

    @Test
    fun outOfChinaCoord_rejected() {
        val (ok, err) = ImportValidator.validate(listOf(line("测试线", 110, tower("1", 0.0, 0.0), tower("2"))))
        // 非法坐标的杆塔被剔除，但线路保留至少有效杆塔
        assertTrue(err.any { it.message.contains("越界") })
        val total = ok.sumOf { it.towers.size }
        assertEquals(1, total)
    }

    @Test
    fun duplicateTowerNumber_rejected() {
        val (ok, err) = ImportValidator.validate(
            listOf(line("测试线", 110, tower("1", row = 1), tower("1", lon = 102.7130, row = 2)))
        )
        assertTrue(err.any { it.message.contains("重复") })
        assertEquals(1, ok.sumOf { it.towers.size })
    }

    @Test
    fun blankTowerNumber_rejected() {
        val (ok, err) = ImportValidator.validate(listOf(line("测试线", 110, tower(""), tower("2"))))
        assertTrue(err.any { it.message.contains("杆塔号为空") })
        assertEquals(1, ok.sumOf { it.towers.size })
    }

    @Test
    fun emptyLine_allowed() {
        val (ok, err) = ImportValidator.validate(listOf(line("空线路", 220)))
        assertEquals(1, ok.size)
        assertEquals(0, ok[0].towers.size)
        assertTrue(err.isEmpty())
    }

    @Test
    fun allVoltageLevels_accepted() {
        listOf(500, 220, 110, 35).forEach { v ->
            val (ok, _) = ImportValidator.validate(listOf(line("线$v", v, tower("1"))))
            assertEquals("电压 $v 应通过", 1, ok.size)
        }
    }
}
