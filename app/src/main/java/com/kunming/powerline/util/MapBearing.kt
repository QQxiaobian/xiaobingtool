package com.kunming.powerline.util

/**
 * 设备朝向 → MapLibre 地图 bearing 的换算（纯函数，便于单测）。
 *
 * 约定：
 * - 设备方位角（罗盘 az / GPS bearing）：0=真北，顺时针增加，东=90、南=180、西=270。
 * - MapLibre 的 [bearing] 是「屏幕正上方对应的罗盘方位」：bearing=0 表示北在上，
 *   bearing=90 表示东在上（见 MapLibre Style Spec / CameraPosition.bearing 文档）。
 *
 * 因此「车头朝上 / heading-up」（让设备朝向始终位于屏幕正上方）时，
 * 地图 bearing 应直接等于设备方位角（正号），即 [fromDeviceHeading]。
 *
 * 历史缺陷：此前用 `-az`（负号），导致设备顺时针转时地图也跟着顺时针转，
 * 屏幕上方显示的是相反方位（如设备朝东却显示西在上），方向箭头也以 2 倍速乱转。
 */
object MapBearing {

    /**
     * 设备方位角 → 地图 bearing（heading-up）。
     * 规范化到 [0, 360)；同时兼容负值与超过 360 的输入。
     */
    fun fromDeviceHeading(headingDeg: Float): Double =
        ((headingDeg.toDouble() % 360.0) + 360.0) % 360.0

    /** Double 版本，供 GPS bearing / 现有 Double 计算使用 */
    fun fromDeviceHeading(headingDeg: Double): Double =
        ((headingDeg % 360.0) + 360.0) % 360.0

    /**
     * 方位差的最短角距离（度，0~180），用于判断是否需要旋转以避免抖动。
     * 例如 a=350、b=10 的差为 20 而非 340。
     */
    fun shortestDelta(a: Double, b: Double): Double {
        val d = ((a - b + 540.0) % 360.0) - 180.0
        return kotlin.math.abs(d)
    }
}
