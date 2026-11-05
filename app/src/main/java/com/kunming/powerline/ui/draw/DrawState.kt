package com.kunming.powerline.ui.draw

import com.kunming.powerline.data.local.entity.CustomOverlayEntity
import com.kunming.powerline.util.GeoUtils
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.util.UUID

/**
 * 绘制状态机
 */
class DrawState(var tool: DrawTool = DrawTool.LINE) {
    val points = mutableListOf<Pair<Double, Double>>() // lon, lat
    var textContent: String = ""
    var textSize: Float = 14f
    var paused: Boolean = false

    fun add(lon: Double, lat: Double) {
        if (paused) return
        points.add(lon to lat)
    }

    fun undo(): Boolean {
        if (points.isEmpty()) return false
        points.removeAt(points.size - 1)
        return true
    }

    fun clear() {
        points.clear()
    }

    /** 实时测量文字 */
    fun measureText(): String {
        if (points.size < 2) return if (points.isEmpty()) "" else "起点 ${fmt(points[0])}"
        return when (tool) {
            DrawTool.LINE -> "长度 " + GeoUtils.formatDistance(GeoUtils.polylineLength(points))
            DrawTool.POLYGON -> "面积 " + GeoUtils.formatArea(GeoUtils.polygonArea(points))
            DrawTool.CIRCLE -> {
                val r = GeoUtils.distance(points[0].first, points[0].second, points.last().first, points.last().second)
                "半径 ${GeoUtils.formatDistance(r)}  面积 ${GeoUtils.formatArea(Math.PI * r * r)}"
            }
            DrawTool.RECT -> {
                val p0 = points[0]
                val p1 = points.last()
                val w = GeoUtils.distance(p0.first, p0.second, p1.first, p0.second)
                val h = GeoUtils.distance(p0.first, p0.second, p0.first, p1.second)
                "宽 ${GeoUtils.formatDistance(w)}  高 ${GeoUtils.formatDistance(h)}  面积 ${GeoUtils.formatArea(w * h)}"
            }
            DrawTool.TEXT -> "文字：$textContent"
        }
    }

    private fun fmt(p: Pair<Double, Double>) = String.format("%.5f,%.5f", p.first, p.second)

    /** 转换为 GeoJSON（写入 Room） */
    fun toOverlay(name: String = ""): CustomOverlayEntity? {
        if (points.isEmpty()) return null
        val (geometry, type) = when (tool) {
            DrawTool.LINE -> {
                if (points.size < 2) return null
                LineString.fromLngLats(points.map { Point.fromLngLat(it.first, it.second) }) to "line"
            }
            DrawTool.POLYGON -> {
                if (points.size < 3) return null
                val ring = points + points.first()
                Polygon.fromLngLats(listOf(ring.map { Point.fromLngLat(it.first, it.second) })) to "polygon"
            }
            DrawTool.CIRCLE -> {
                if (points.size < 2) return null
                val r = GeoUtils.distance(points[0].first, points[0].second, points.last().first, points.last().second)
                Polygon.fromLngLats(listOf(circleRing(points[0], r).map { Point.fromLngLat(it.first, it.second) })) to "circle"
            }
            DrawTool.RECT -> {
                if (points.size < 2) return null
                val p0 = points[0]
                val p1 = points.last()
                val ring = listOf(
                    p0, p1.first to p0.second, p1, p0.first to p1.second, p0
                )
                Polygon.fromLngLats(listOf(ring.map { Point.fromLngLat(it.first, it.second) })) to "rectangle"
            }
            DrawTool.TEXT -> {
                if (points.isEmpty()) return null
                Point.fromLngLat(points[0].first, points[0].second) to "text"
            }
        }
        val label = name.ifBlank {
            when (type) {
                "line" -> "线段 " + GeoUtils.formatDistance(GeoUtils.polylineLength(points))
                "text" -> textContent.ifBlank { "文字标注" }
                else -> "面 " + GeoUtils.formatArea(
                    GeoUtils.polygonArea(
                        if (tool == DrawTool.CIRCLE && points.size >= 2) {
                            val r = GeoUtils.distance(points[0].first, points[0].second, points.last().first, points.last().second)
                            circleRing(points[0], r)
                        } else points
                    )
                )
            }
        }
        return CustomOverlayEntity(
            id = UUID.randomUUID().toString().replace("-", "").take(16),
            type = type,
            name = label,
            geometryGeojson = geometry.toJson(),
            textSize = textSize
        )
    }

    private fun circleRing(center: Pair<Double, Double>, radiusM: Double, segments: Int = 64): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        for (i in 0..segments) {
            val angle = 2 * Math.PI * i / segments
            val dx = radiusM * Math.cos(angle)
            val dy = radiusM * Math.sin(angle)
            val (dLon, dLat) = GeoUtils.offsetMetersToDegrees(center.first, center.second, dx, dy)
            out.add((center.first + dLon) to (center.second + dLat))
        }
        return out
    }

    /** 预览要素（实时渲染） */
    fun previewFeatures(): List<Feature> {
        if (points.isEmpty()) return emptyList()
        val feats = mutableListOf<Feature>()
        when (tool) {
            DrawTool.LINE -> {
                if (points.size >= 2) {
                    feats.add(
                        Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.first, it.second) }))
                    )
                }
            }
            DrawTool.POLYGON -> {
                if (points.size >= 3) {
                    val ring = (points + points.first()).map { Point.fromLngLat(it.first, it.second) }
                    feats.add(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply { addStringProperty("kind", "fill") })
                }
                if (points.size >= 2) {
                    feats.add(Feature.fromGeometry(LineString.fromLngLats((points + points.first()).map { Point.fromLngLat(it.first, it.second) })))
                }
            }
            DrawTool.CIRCLE -> {
                if (points.size >= 2) {
                    val r = GeoUtils.distance(points[0].first, points[0].second, points.last().first, points.last().second)
                    val ring = circleRing(points[0], r).map { Point.fromLngLat(it.first, it.second) }
                    feats.add(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply { addStringProperty("kind", "fill") })
                }
            }
            DrawTool.RECT -> {
                if (points.size >= 2) {
                    val p0 = points[0]
                    val p1 = points.last()
                    val ring = listOf(p0, p1.first to p0.second, p1, p0.first to p1.second, p0)
                        .map { Point.fromLngLat(it.first, it.second) }
                    feats.add(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply { addStringProperty("kind", "fill") })
                }
            }
            DrawTool.TEXT -> {}
        }
        // 顶点
        points.forEach { p ->
            feats.add(
                Feature.fromGeometry(Point.fromLngLat(p.first, p.second)).apply { addStringProperty("kind", "vertex") }
            )
        }
        return feats
    }

    /** 导出 GeoJSON FeatureCollection */
    fun toGeoJson(overlay: CustomOverlayEntity): String {
        val f = Feature.fromJson("""{"type":"Feature","properties":{},"geometry":${overlay.geometryGeojson}}""")
        f.addStringProperty("type", overlay.type)
        f.addStringProperty("name", overlay.name)
        f.addNumberProperty("createdAt", overlay.createdAt.toDouble())
        return """{"type":"FeatureCollection","features":[${f.toJson()}]}"""
    }
}
