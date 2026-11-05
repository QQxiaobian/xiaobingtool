package com.kunming.powerline.util

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * 地理计算工具（WGS84 / 球面近似）
 */
object GeoUtils {

    const val EARTH_RADIUS = 6371008.8 // 平均半径(m)

    /** Haversine 距离，单位米 */
    fun distance(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS * c
    }

    /** 折线总长(m) */
    fun polylineLength(points: List<Pair<Double, Double>>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until points.size) {
            sum += distance(points[i - 1].first, points[i - 1].second, points[i].first, points[i].second)
        }
        return sum
    }

    /**
     * 球面多边形面积（m²），使用球面三角超量法（L'Huilier / 球面多边形面积公式）
     */
    fun polygonArea(points: List<Pair<Double, Double>>): Double {
        if (points.size < 3) return 0.0
        var total = 0.0
        val p0 = points.first()
        for (i in 1 until points.size - 1) {
            total += sphericalTriangleArea(p0, points[i], points[i + 1])
        }
        return abs(total)
    }

    private fun sphericalTriangleArea(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>): Double {
        val va = toVector(a)
        val vb = toVector(b)
        val vc = toVector(c)
        val ab = angleBetween(va, vb)
        val bc = angleBetween(vb, vc)
        val ca = angleBetween(vc, va)
        val s = (ab + bc + ca) / 2.0
        val t = kotlin.math.tan(s / 2) * kotlin.math.tan((s - ab) / 2) *
            kotlin.math.tan((s - bc) / 2) * kotlin.math.tan((s - ca) / 2)
        val e = 4 * kotlin.math.atan(sqrt(abs(t)))
        return e * EARTH_RADIUS * EARTH_RADIUS
    }

    private fun toVector(p: Pair<Double, Double>): DoubleArray {
        val lon = Math.toRadians(p.first)
        val lat = Math.toRadians(p.second)
        return doubleArrayOf(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
    }

    private fun angleBetween(a: DoubleArray, b: DoubleArray): Double {
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        return acos(dot.coerceIn(-1.0, 1.0))
    }

    /** 点到线段距离(m)，用于交叉跨越距离计算 */
    fun pointToSegmentDistance(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double
    ): Double {
        // 以米为单位的局部平面近似
        val latScale = 111320.0
        val lonScale = 111320.0 * cos(Math.toRadians((ay + by) / 2))
        val x0 = (px - ax) * lonScale
        val y0 = (py - ay) * latScale
        val x1 = (bx - ax) * lonScale
        val y1 = (by - ay) * latScale
        val len2 = x1 * x1 + y1 * y1
        if (len2 == 0.0) return sqrt(x0 * x0 + y0 * y0)
        var t = (x0 * x1 + y0 * y1) / len2
        t = t.coerceIn(0.0, 1.0)
        val dx = x0 - t * x1
        val dy = y0 - t * y1
        return sqrt(dx * dx + dy * dy)
    }

    /** 点到折线的最短距离(m) */
    fun pointToPolylineDistance(px: Double, py: Double, line: List<Pair<Double, Double>>): Double {
        if (line.isEmpty()) return Double.MAX_VALUE
        if (line.size == 1) return distance(px, py, line[0].first, line[0].second)
        var min = Double.MAX_VALUE
        for (i in 1 until line.size) {
            val d = pointToSegmentDistance(
                px, py,
                line[i - 1].first, line[i - 1].second,
                line[i].first, line[i].second
            )
            if (d < min) min = d
        }
        return min
    }

    /**
     * 视觉偏移：以米为单位的偏移量换算为经纬度增量
     * 同塔多回线路杆塔按回路号做 2~5m 水平偏移（只影响显示，不改真实坐标）
     */
    fun offsetMetersToDegrees(lon: Double, lat: Double, dxMeters: Double, dyMeters: Double): Pair<Double, Double> {
        val dLat = dyMeters / 111320.0
        val dLon = dxMeters / (111320.0 * cos(Math.toRadians(lat)).coerceAtLeast(1e-6))
        return dLon to dLat
    }

    /** 格式化距离显示 */
    fun formatDistance(m: Double): String = when {
        m < 1.0 -> String.format("%.1f m", m)
        m < 1000.0 -> String.format("%.1f m", m)
        else -> String.format("%.3f km", m / 1000.0)
    }

    /** 格式化面积显示 */
    fun formatArea(m2: Double): String = when {
        m2 < 10000.0 -> String.format("%.1f ㎡", m2)
        else -> String.format("%.4f k㎡", m2 / 1_000_000.0)
    }

    /** 经纬度合法性校验（中国境内粗略范围） */
    fun isValidChinaCoord(lon: Double, lat: Double): Boolean =
        lon in 73.0..136.0 && lat in 3.0..54.0

    fun isValidLonLat(lon: Double, lat: Double): Boolean =
        lon in -180.0..180.0 && lat in -90.0..90.0

    fun nearlySameLocation(lon1: Double, lat1: Double, lon2: Double, lat2: Double, eps: Double = 1e-4): Boolean =
        abs(lon1 - lon2) < eps && abs(lat1 - lat2) < eps

    /**
     * WGS84 -> GCJ02（火星坐标），用于高德地图导航
     */
    fun wgs84ToGcj02(lon: Double, lat: Double): Pair<Double, Double> {
        if (outOfChina(lon, lat)) return lon to lat
        val a = 6378245.0
        val ee = 0.00669342162296594323
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - ee * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (a / sqrtMagic * cos(radLat) * PI)
        return (lon + dLon) to (lat + dLat)
    }

    /**
     * GCJ02 -> WGS84（迭代反算）
     */
    fun gcj02ToWgs84(lon: Double, lat: Double): Pair<Double, Double> {
        if (outOfChina(lon, lat)) return lon to lat
        var wgsLon = lon
        var wgsLat = lat
        repeat(3) {
            val (gLon, gLat) = wgs84ToGcj02(wgsLon, wgsLat)
            wgsLon -= (gLon - lon)
            wgsLat -= (gLat - lat)
        }
        return wgsLon to wgsLat
    }

    private fun outOfChina(lon: Double, lat: Double): Boolean =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    /** 度分秒字符串解析，如 103°02'32.5" -> 103.042361 */
    private val DMS_REGEX = Regex("""(\d+)\s*[°度]\s*(\d+)\s*['′分]\s*([\d.]+)\s*["″秒]?""")

    fun parseDms(input: String): Double? {
        val raw = input.trim()
        // 纯十进制（可带正负号）直接返回
        raw.toDoubleOrNull()?.let { return it }
        val m = DMS_REGEX.find(raw) ?: return null
        val d = m.groupValues[1].toDoubleOrNull() ?: return null
        val mm = m.groupValues[2].toDoubleOrNull() ?: return null
        val ss = m.groupValues[3].toDoubleOrNull() ?: return null
        var v = d + mm / 60.0 + ss / 3600.0
        if (raw.contains('S') || raw.contains('W') || raw.contains('南') || raw.contains('西')) v = -v
        // 支持前置负号：-102°42'44.3"
        if (raw.startsWith("-")) v = -v
        return v
    }

    /** 解析任意坐标输入（十进制度 或 度分秒），返回 (lon,lat) */
    fun parseCoordinatePair(a: String, b: String): Pair<Double, Double>? {
        val lon = parseDms(a) ?: a.toDoubleOrNull()
        val lat = parseDms(b) ?: b.toDoubleOrNull()
        if (lon == null || lat == null) return null
        if (!isValidLonLat(lon, lat)) return null
        return lon to lat
    }
}
