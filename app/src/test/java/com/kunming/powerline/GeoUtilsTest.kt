package com.kunming.powerline

import com.kunming.powerline.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地理计算核心算法单元测试
 *
 * 覆盖：距离、折线长度、球面面积、点在线上最短距离、
 * WGS84↔GCJ02 往返、度分秒解析、中国范围判定。
 */
class GeoUtilsTest {

    @Test
    fun haversine_knownDistance() {
        // 昆明 — 北京大圆距离约 2088 km（容差 1%）
        val d = GeoUtils.distance(102.7123, 25.0401, 116.4074, 39.9042)
        assertTrue("实际 ${d / 1000} km", d in 2_060_000.0..2_110_000.0)
    }

    @Test
    fun haversine_oneDegreeLatitudeIsAbout111km() {
        val d = GeoUtils.distance(102.7, 25.0, 102.7, 26.0)
        // 1 度纬度约 111.19 km
        assertTrue("实际 $d", d in 110_500.0..112_000.0)
    }

    @Test
    fun polylineLength_isSumOfSegments() {
        val pts = listOf(102.7000 to 25.0000, 102.7010 to 25.0000, 102.7010 to 25.0010)
        val len = GeoUtils.polylineLength(pts)
        val manual = GeoUtils.distance(102.7, 25.0, 102.701, 25.0) +
            GeoUtils.distance(102.701, 25.0, 102.701, 25.001)
        assertEquals(len, manual, 1e-6)
        assertTrue(len > 100) // 每段约 100m
    }

    @Test
    fun polygonArea_smallSquareMatchesPlanarApprox() {
        // 昆明附近 0.001° × 0.001° 的方块，约 110m × 100m
        val sideLon = 0.001
        val sideLat = 0.001
        val w = GeoUtils.distance(102.7, 25.0, 102.7 + sideLon, 25.0)
        val h = GeoUtils.distance(102.7, 25.0, 102.7, 25.0 + sideLat)
        val poly = listOf(
            102.7 to 25.0,
            102.7 + sideLon to 25.0,
            102.7 + sideLon to 25.0 + sideLat,
            102.7 to 25.0 + sideLat
        )
        val area = GeoUtils.polygonArea(poly)
        // 球面超量法在这么小的尺度下应接近平面矩形面积（容差 3%）
        assertEquals(w * h, area, w * h * 0.03)
    }

    @Test
    fun polygonArea_isOrderIndependent() {
        val a = listOf(102.70 to 25.00, 102.71 to 25.00, 102.71 to 25.01, 102.70 to 25.01)
        val b = a.reversed()
        assertEquals(GeoUtils.polygonArea(a), GeoUtils.polygonArea(b), 1.0)
    }

    @Test
    fun pointToSegment_distanceToMidpoint() {
        val pts = listOf(102.7000 to 25.0000, 102.7100 to 25.0000)
        // 取线段中点正北 20m 处（米制坐标）
        val mid = 102.7050 to 25.0000
        val (dLon, dLat) = GeoUtils.offsetMetersToDegrees(mid.first, mid.second, 0.0, 20.0)
        val d = GeoUtils.pointToPolylineDistance(mid.first + dLon, mid.second + dLat, pts)
        assertTrue("实际 $d", d in 15.0..25.0)
    }

    @Test
    fun gcj02_roundTrip_withinOneMeter() {
        val lon = 102.7123
        val lat = 25.0401
        val (glon, glat) = GeoUtils.wgs84ToGcj02(lon, lat)
        // 昆明地区 GCJ02 偏移应在数百米量级
        val shifted = GeoUtils.distance(lon, lat, glon, glat)
        assertTrue("偏移 $shifted", shifted in 100.0..800.0)

        val (wlon, wlat) = GeoUtils.gcj02ToWgs84(glon, glat)
        val err = GeoUtils.distance(lon, lat, wlon, wlat)
        assertTrue("往返误差 $err m", err < 1.0)
    }

    @Test
    fun parseDms_variants() {
        assertNotNull(GeoUtils.parseDms("102°42'44.3\""))
        assertNotNull(GeoUtils.parseDms("25°2'24.4\""))
        assertEquals(102.7123, GeoUtils.parseDms("102.7123")!!, 1e-9)
        assertEquals(-102.7123, GeoUtils.parseDms("-102.7123")!!, 1e-9)
        assertTrue(GeoUtils.parseDms("abc") == null)
    }

    @Test
    fun isValidChinaCoord_rejectsOutOfRange() {
        assertTrue(GeoUtils.isValidChinaCoord(102.7123, 25.0401))
        assertTrue(!GeoUtils.isValidChinaCoord(0.0, 0.0))
        assertTrue(!GeoUtils.isValidChinaCoord(100.0, 60.0))   // 纬度越界
        assertTrue(!GeoUtils.isValidChinaCoord(20.0, 25.0))    // 经度越界
    }

    @Test
    fun formatDistance_reasonable() {
        assertNotNull(GeoUtils.formatDistance(120.0))
        assertNotNull(GeoUtils.formatDistance(1500.0))
        assertNotNull(GeoUtils.formatDistance(12500.0))
        assertTrue(GeoUtils.formatArea(1_000_000.0).isNotBlank())
    }
}
