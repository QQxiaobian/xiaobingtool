package com.kunming.powerline.util

import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * GeoJSON 构造与坐标偏移工具
 */
object GeoJsonBuilder {

    fun point(lon: Double, lat: Double, props: Map<String, Any?> = emptyMap()): Feature {
        val b = Feature.fromGeometry(Point.fromLngLat(lon, lat))
        props.forEach { (k, v) ->
            when (v) {
                null -> {}
                is String -> b.addStringProperty(k, v)
                is Int -> b.addNumberProperty(k, v)
                is Long -> b.addNumberProperty(k, v)
                is Double -> b.addNumberProperty(k, v)
                is Float -> b.addNumberProperty(k, v.toDouble())
                is Boolean -> b.addBooleanProperty(k, v)
                else -> b.addStringProperty(k, v.toString())
            }
        }
        return b
    }

    fun line(coords: List<Point>, props: Map<String, Any?> = emptyMap()): Feature {
        val b = Feature.fromGeometry(LineString.fromLngLats(coords))
        props.forEach { (k, v) ->
            when (v) {
                null -> {}
                is String -> b.addStringProperty(k, v)
                is Number -> b.addNumberProperty(k, v)
                is Boolean -> b.addBooleanProperty(k, v)
                else -> b.addStringProperty(k, v.toString())
            }
        }
        return b
    }

    fun polygon(ring: List<Point>, props: Map<String, Any?> = emptyMap()): Feature {
        val b = Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
        props.forEach { (k, v) ->
            when (v) {
                null -> {}
                is String -> b.addStringProperty(k, v)
                is Number -> b.addNumberProperty(k, v)
                else -> b.addStringProperty(k, v.toString())
            }
        }
        return b
    }

    fun collection(features: List<Feature>): FeatureCollection =
        FeatureCollection.fromFeatures(features)

    fun emptyCollectionJson(): String =
        """{"type":"FeatureCollection","features":[]}"""

    fun collectionJson(features: List<Feature>): String =
        collection(features).toJson()

    /**
     * 给点要素加视觉偏移（米）——用于同塔多回杆塔显示优化
     */
    fun offsetPoint(
        lon: Double,
        lat: Double,
        dxMeters: Double,
        dyMeters: Double
    ): Pair<Double, Double> = GeoUtils.offsetMetersToDegrees(lon, lat, dxMeters, dyMeters)

    /** 解析坐标串 "lon,lat" */
    fun parseLonLat(s: String?): Pair<Double, Double>? {
        if (s.isNullOrBlank()) return null
        val parts = s.split(',', '，').map { it.trim() }
        if (parts.size < 2) return null
        val lon = parts[0].toDoubleOrNull() ?: return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        return lon to lat
    }
}
