package com.kunming.powerline.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.kunming.powerline.BuildConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单次定位结果
 */
data class FixLocation(
    val lon: Double,
    val lat: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val altitude: Double,
    val time: Long,
    val address: String? = null,
    val isWgs84: Boolean = false
) {
    fun toAndroidLocation(): Location = Location("amap").apply {
        latitude = lat
        longitude = lon
        accuracy = this@FixLocation.accuracy
        speed = this@FixLocation.speed
        bearing = this@FixLocation.bearing
        altitude = this@FixLocation.altitude
        time = this@FixLocation.time
    }
}

/**
 * 高德定位封装
 * - 国内 GPS + 基站 + WiFi 融合定位
 * - 隐私合规：必须先 updatePrivacyShow/updatePrivacyAgree
 */
@Singleton
class AmapLocationClient @Inject constructor(
    private val context: android.content.Context
) {
    private var client: com.amap.api.location.AMapLocationClient? = null
    private var started = false

    private val _location = MutableSharedFlow<FixLocation>(
        replay = 1, extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val locations: SharedFlow<FixLocation> = _location.asSharedFlow()

    private val _last = MutableStateFlow<FixLocation?>(null)
    val last: StateFlow<FixLocation?> = _last.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _error.asSharedFlow()

    private var privileged = false

    @SuppressLint("MissingPermission")
    private fun ensureClient(): com.amap.api.location.AMapLocationClient? {
        client?.let { return it }
        return try {
            ensurePrivacy()
            if (BuildConfig.AMAP_KEY.isNotBlank()) {
                com.amap.api.location.AMapLocationClient.setApiKey(BuildConfig.AMAP_KEY)
            }
            val c = com.amap.api.location.AMapLocationClient(context.applicationContext)
            client = c
            c
        } catch (t: Throwable) {
            _error.tryEmit("高德定位初始化失败：${t.message}")
            null
        }
    }

    private fun ensurePrivacy() {
        if (privileged) return
        try {
            com.amap.api.location.AMapLocationClient.updatePrivacyShow(context, true, true)
            com.amap.api.location.AMapLocationClient.updatePrivacyAgree(context, true)
            privileged = true
        } catch (t: Throwable) {
            // 旧版本没有这些方法时忽略
        }
    }

    /**
     * 启动定位
     * @param intervalMs 定位间隔
     * @param highAccuracy GPS 高精度
     */
    @SuppressLint("MissingPermission")
    fun start(intervalMs: Long = 1000L, highAccuracy: Boolean = true, once: Boolean = false): Boolean {
        val c = ensureClient() ?: return false
        try {
            val option = AMapLocationClientOption().apply {
                setLocationMode(
                    if (highAccuracy)
                        AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    else
                        AMapLocationClientOption.AMapLocationMode.Battery_Saving
                )
                setInterval(intervalMs)
                setOnceLocation(once)
                setOnceLocationLatest(once)
                setNeedAddress(true)
                setLocationCacheEnable(true)
                setHttpTimeOut(12000)
                // 温漂补偿 / 关闭传感器辅助
                setSensorEnable(false)
            }
            c.setLocationOption(option)
            c.setLocationListener(listener)
            c.startLocation()
            started = true
            return true
        } catch (t: Throwable) {
            _error.tryEmit("定位启动失败：${t.message}")
            return false
        }
    }

    fun stop() {
        try {
            client?.unRegisterLocationListener(listener)
            client?.stopLocation()
        } catch (_: Throwable) {
        }
        started = false
    }

    fun isRunning(): Boolean = started

    fun destroy() {
        stop()
        try {
            client?.onDestroy()
        } catch (_: Throwable) {
        }
        client = null
    }

    /**
     * 坐标转换：高德返回 GCJ02（国内）或 WGS84（国外）
     * 国网台账一般用 WGS84/CGCS2000，这里统一转为 WGS84 供地图使用；
     * 若设备在国外（isWgs84）直接使用。
     */
    private val listener = AMapLocationListener { loc: AMapLocation? ->
        if (loc == null) return@AMapLocationListener
        if (loc.errorCode != 0) {
            _error.tryEmit("定位失败(${loc.errorCode})：${loc.errorInfo}")
            return@AMapLocationListener
        }
        val outOfChina = !loc.country.isNullOrBlank() && !loc.country.contains("中国")
        val wgs = if (outOfChina) {
            loc.longitude to loc.latitude
        } else {
            com.kunming.powerline.util.GeoUtils.gcj02ToWgs84(loc.longitude, loc.latitude)
        }
        val fix = FixLocation(
            lon = wgs.first,
            lat = wgs.second,
            accuracy = loc.accuracy,
            speed = loc.speed,
            bearing = loc.bearing,
            altitude = loc.altitude,
            time = loc.time,
            address = loc.address ?: loc.poiName ?: loc.district ?: loc.city,
            isWgs84 = outOfChina
        )
        _last.value = fix
        _location.tryEmit(fix)
    }
}
