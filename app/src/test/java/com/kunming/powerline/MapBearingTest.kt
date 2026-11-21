package com.kunming.powerline

import com.kunming.powerline.util.MapBearing
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设备方位 → MapLibre bearing 换算（车头朝上）。
 *
 * 核心回归点：bearing 必须等于设备方位（正号）。若退回到 -az，
 * 设备朝东(90°)会得到 270°（西在上），表现为「地图与手机同向转、方向反了」。
 */
class MapBearingTest {

    @Test
    fun north_keepsZero() {
        assertEquals(0.0, MapBearing.fromDeviceHeading(0f), 1e-9)
    }

    @Test
    fun east_isPositiveNinety_notWest() {
        // 设备朝东 → bearing=90（东在上），而不是 -90=270（西在上）
        assertEquals(90.0, MapBearing.fromDeviceHeading(90f), 1e-9)
        assertEquals(90.0, MapBearing.fromDeviceHeading(90.0), 1e-9)
    }

    @Test
    fun southAndWest() {
        assertEquals(180.0, MapBearing.fromDeviceHeading(180f), 1e-9)
        assertEquals(270.0, MapBearing.fromDeviceHeading(270f), 1e-9)
    }

    @Test
    fun negativeAndOver360_normalized() {
        assertEquals(330.0, MapBearing.fromDeviceHeading(-30f), 1e-9)
        assertEquals(120.0, MapBearing.fromDeviceHeading(480f), 1e-9)
        assertEquals(0.0, MapBearing.fromDeviceHeading(360f), 1e-9)
    }

    @Test
    fun headingUp_arrowStaysAtScreenTop() {
        // 车头朝上：地图 bearing = 设备方位 az 时，画在图层里、相对图层指 az 的方向箭头
        // 在屏幕上朝向 = az - bearing = 0，即箭头恒指屏幕正上方。
        for (az in listOf(0, 45, 90, 135, 180, 225, 270, 315)) {
            val bearing = MapBearing.fromDeviceHeading(az.toFloat())
            val screenHeading = ((az - bearing + 360) % 360 + 360) % 360
            assertEquals("az=$az 时箭头应指屏幕正上方", 0.0, screenHeading.toDouble(), 1e-9)
        }
    }

    @Test
    fun oldNegativeSign_wouldReverseDirection() {
        // 记录历史缺陷：旧逻辑 -az 时，设备朝东会得到 270（西在上），方向相反
        val oldBearing = ((-90 % 360) + 360) % 360
        assertEquals(270, oldBearing)
        assertEquals(90.0, MapBearing.fromDeviceHeading(90f), 1e-9)
    }

    @Test
    fun shortestDelta_wrapsAroundNorth() {
        assertEquals(20.0, MapBearing.shortestDelta(350.0, 10.0), 1e-9)
        assertEquals(20.0, MapBearing.shortestDelta(10.0, 350.0), 1e-9)
        assertEquals(180.0, MapBearing.shortestDelta(0.0, 180.0), 1e-9)
        assertEquals(0.0, MapBearing.shortestDelta(90.0, 90.0), 1e-9)
    }

    @Test
    fun shortestDelta_largeGap() {
        assertEquals(170.0, MapBearing.shortestDelta(10.0, 200.0), 1e-9)
    }
}
