package com.kunming.powerline.data.sensor

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 方位角传感器（真北）
 *
 * 规范（第 11 节）：支持「磁北 / 真北」切换。真北 = 磁北 + 磁偏角，
 * 磁偏角由 [GeomagneticField] 按当前经纬度与海拔计算。
 */
@Singleton
class CompassSensor @Inject constructor(private val context: Context) {

    private val manager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** 方位角（度，0=北，顺时针） */
    fun azimuthFlow(trueNorth: () -> Boolean, location: () -> Pair<Double, Double>?): kotlinx.coroutines.flow.Flow<Float> =
        callbackFlow {
            val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mag = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accel == null || mag == null) {
                trySend(0f)
                awaitClose { }
                return@callbackFlow
            }
            val gravity = FloatArray(3)
            val geomagnetic = FloatArray(3)
            var hasGravity = false
            var hasMag = false
            var smoothed = Float.NaN

            val rotation = FloatArray(9)
            val orientation = FloatArray(3)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            System.arraycopy(event.values, 0, gravity, 0, 3); hasGravity = true
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            System.arraycopy(event.values, 0, geomagnetic, 0, 3); hasMag = true
                        }
                    }
                    if (!hasGravity || !hasMag) return
                    if (!SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) return
                    SensorManager.getOrientation(rotation, orientation)
                    var deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    if (deg < 0) deg += 360f

                    if (trueNorth()) {
                        val ll = location()
                        if (ll != null) {
                            val geo = GeomagneticField(ll.second.toFloat(), ll.first.toFloat(), 1800f, System.currentTimeMillis())
                            deg = (deg + geo.declination + 360f) % 360f
                        }
                    }
                    // 低通滤波，抑制抖动
                    smoothed = if (smoothed.isNaN()) deg else {
                        val d = ((deg - smoothed + 540f) % 360f) - 180f
                        (smoothed + d * 0.15f + 360f) % 360f
                    }
                    trySend(smoothed)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            manager?.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            manager?.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
            awaitClose {
                manager?.unregisterListener(listener)
            }
        }

    /**
     * 由加速度计 + 磁力计计算方位角（一次性，用于校准提示）
     */
    fun hasCompass(): Boolean = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null

    companion object {
        /** 磁偏角估算（给定点），单位度 */
        fun declination(lon: Double, lat: Double, altitude: Double = 1800.0): Float =
            GeomagneticField(lat.toFloat(), lon.toFloat(), altitude.toFloat(), System.currentTimeMillis()).declination

        /** 两点间方位角（真北，度） */
        fun bearingTo(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
            val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
            val dl = Math.toRadians(lon2 - lon1)
            val y = sin(dl) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
            return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        }
    }
}
