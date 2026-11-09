package com.kunming.powerline

import com.kunming.powerline.util.MapPointKind
import com.kunming.powerline.util.MapPointTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPointTypesTest {

    @Test
    fun `名称为空时使用类型默认名`() {
        assertEquals("危险点", MapPointTypes.normalizeName(MapPointKind.DANGER, ""))
        assertEquals("视频监控", MapPointTypes.normalizeName(MapPointKind.CAMERA, "   "))
        assertEquals("1 号杆旁", MapPointTypes.normalizeName(MapPointKind.FAVORITE, "  1 号杆旁  "))
    }

    @Test
    fun `等级规范化只接受一二三级`() {
        assertEquals("Ⅰ", MapPointTypes.normalizeLevel("1"))
        assertEquals("Ⅰ", MapPointTypes.normalizeLevel("Ⅰ"))
        assertEquals("Ⅱ", MapPointTypes.normalizeLevel("2"))
        assertEquals("Ⅲ", MapPointTypes.normalizeLevel("3"))
        assertEquals("Ⅲ", MapPointTypes.normalizeLevel(""))
        assertEquals("Ⅲ", MapPointTypes.normalizeLevel("特级"))
    }

    @Test
    fun `海拔只解析合法数字`() {
        assertEquals(1892.5, MapPointTypes.parseAltitude(" 1892.5 ")!!, 0.0001)
        assertNull(MapPointTypes.parseAltitude("约 1900 米"))
        assertNull(MapPointTypes.parseAltitude(""))
    }

    @Test
    fun `附加字段去空白`() {
        assertEquals("rtsp://192.168.1.12/live", MapPointTypes.cleanExtra("  rtsp://192.168.1.12/live  "))
        assertEquals("", MapPointTypes.cleanExtra("   "))
    }

    @Test
    fun `类型 key 唯一且可反查`() {
        val keys = MapPointKind.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        MapPointKind.entries.forEach { assertEquals(it, MapPointKind.of(it.key)) }
        assertNull(MapPointKind.of("unknown"))
    }

    @Test
    fun `点位文案不含任何单位名称`() {
        // 拆分书写，避免源码里出现完整的单位名称字面量
        val forbidden = listOf("供" + "电", "局", "管理所")
        MapPointKind.entries.forEach { kind ->
            listOf(kind.label, kind.defaultName, kind.extraLabel, kind.extraHint).forEach { text ->
                assertTrue(forbidden.none { text.contains(it) })
            }
        }
        assertTrue(MapPointTypes.savedMessage(MapPointKind.DANGER, "施工隐患").contains("施工隐患"))
    }
}
