package com.kunming.powerline

import com.kunming.powerline.ui.map.MapIcons
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 方向箭头图标名映射：角度 -> 预渲染图标（每 10° 一档，就近取整）
 */
class HeadingIconTest {

    @Test
    fun north_isZero() {
        assertEquals("icon-heading-0", MapIcons.headingIconName(0f))
        assertEquals("icon-heading-0", MapIcons.headingIconName(4f))
        assertEquals("icon-heading-0", MapIcons.headingIconName(355f))
    }

    @Test
    fun east_is90() {
        assertEquals("icon-heading-90", MapIcons.headingIconName(90f))
        assertEquals("icon-heading-90", MapIcons.headingIconName(86f))
    }

    @Test
    fun negativeAndOver360_normalized() {
        assertEquals("icon-heading-330", MapIcons.headingIconName(-30f))
        assertEquals("icon-heading-120", MapIcons.headingIconName(480f))
        assertEquals("icon-heading-270", MapIcons.headingIconName(266f))
    }

    @Test
    fun stepIsExact() {
        assertEquals(36, (0 until 360 step 10).map { MapIcons.headingIconName(it.toFloat()) }.toSet().size)
    }
}
