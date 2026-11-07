package com.kunming.powerline.data.sensor

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 方位角传感器（真北）
 *
 * 优先使用 TYPE_ROTATION_VECTOR（系统融合了加速度计 + 磁力计 + 陀螺仪，
 * 在绝大多数手机上都能稳定输出航向）；该传感器不可用时退化为
 * 加速度计 + 磁力计自行解算。
 *
 * 输出做了两点处理：
 * 1. `.conflate()`：只保留最新值，避免高频传感器回调被丢包（否则箭头几乎不动）
 * 2. 角度低通滤波：抑制抖动
 */
@Singleton
class CompassSensor @Inject constructor(private val context: Context) {

    private val manager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** 方位角（度，0=北，顺时针） */
    fun azimuthFlow(
        trueNorth: () -> Boolean,
        location: () -> Pair<Double, Double>?
    ): Flow<Float> = callbackFlow {
        val rotationVector = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val hasRV = rotationVector != null
        val hasClassic = accel != null && mag != null
        if (!hasRV && !hasClassic) {
            trySend(0f)
            awaitClose { }
            return@callbackFlow
        }

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMag = false

        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothed = Float.NaN

        fun publish(degRaw: Float) {
            var deg = degRaw
            if (trueNorth()) {
                val ll = location()
                if (ll != null) {
                    val geo = GeomagneticField(
                        ll.second.toFloat(), ll.first.toFloat(), 1800f,
                        System.currentTimeMillis()
                    )
                    deg = (deg + geo.declination + 360f) % 360f
                }
            }
            // 低通滤波，抑制抖动
            smoothed = if (smoothed.isNaN()) deg else {
                val d = ((deg - smoothed + 540f) % 360f) - 180f
                (smoothed + d * 0.25f + 360f) % 360f
            }
            trySend(smoothed)
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        // 旋转矢量 -> 旋转矩阵 -> 方位角
                        SensorManager.getRotationMatrixFromVector(rotation, event.values)
                        SensorManager.getOrientation(rotation, orientation)
                        var deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        if (deg < 0) deg += 360f
                        publish(deg)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                        fallbackCompute()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasMag = true
                        fallbackCompute()
                    }
                }
            }

            /** 仅在旋转矢量不可用时，用加速度计 + 磁力计解算 */
            private fun fallbackCompute() {
                if (hasRV) return
                if (!hasGravity || !hasMag) return
                if (!SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) return
                SensorManager.getOrientation(rotation, orientation)
                var deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (deg < 0) deg += 360f
                publish(deg)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationVector != null) {
            manager?.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        }
        if (accel != null && mag != null) {
            manager?.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            manager?.registerListener(listener, mag, SensorManager.SENSOR_DELAY_GAME)
        }

        awaitClose {
            manager?.unregisterListener(listener)
        }
    }.conflate()

    /**
     * 是否有可用于定向的传感器
     */
    fun hasCompass(): Boolean =
        manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            (manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
                manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)

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
