package com.kunming.powerline.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 用户设置（DataStore Preferences）
 */
@Singleton
class SettingsStore @Inject constructor(private val context: Context) {

    private object Keys {
        val BASEMAP = stringPreferencesKey("basemap")
        val TEXT_SIZE = floatPreferencesKey("tower_text_size")
        val ROOM_ID = stringPreferencesKey("share_room_id")
        val SHARE_ENABLED = booleanPreferencesKey("share_enabled")
        val SHOW_COMPASS = booleanPreferencesKey("show_compass")
        val TRUE_NORTH = booleanPreferencesKey("true_north")
        val LAYER_STATE = stringPreferencesKey("layer_state")
        val USER_ID = stringPreferencesKey("user_id")
        val VOLTAGE_FILTER = stringPreferencesKey("voltage_filter")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val GROUP_ID = intPreferencesKey("current_group")
        val LABEL_ZOOM = floatPreferencesKey("label_min_zoom")
        // 运行时服务器地址覆盖（空串 = 使用编译期 buildConfigField 默认值）
        val SERVER_PMS = stringPreferencesKey("server_pms_base")
        val SERVER_WS = stringPreferencesKey("server_ws_location")
        val SERVER_TILES = stringPreferencesKey("server_tile_url")
        // App 内自用的模拟定位（不注入系统、不影响其他应用，见 MockLocationScreen）
        val MOCK_ENABLED = booleanPreferencesKey("mock_location_enabled")
        val MOCK_LON = stringPreferencesKey("mock_location_lon")
        val MOCK_LAT = stringPreferencesKey("mock_location_lat")
        val MOCK_ACCURACY = floatPreferencesKey("mock_location_accuracy")
    }

    /** 用户在设置页填写的服务器地址覆盖值 */
    data class ServerOverride(
        val pms: String = "",
        val ws: String = "",
        val tiles: String = ""
    )

    /**
     * App 内自用的模拟定位状态。
     *
     * 边界：仅影响本 App 的地图蓝点与「当前位置」补录，不写入系统 LocationProvider，
     * 不向位置共享 / 巡检轨迹上报，也无需 root。
     */
    data class MockState(
        val enabled: Boolean = false,
        val lon: Double? = null,
        val lat: Double? = null,
        val accuracy: Float = 5f
    ) {
        val valid: Boolean get() = enabled && lon != null && lat != null &&
            lon in -180.0..180.0 && lat in -90.0..90.0
    }

    private val prefs = context.dataStore.data

    val basemap: Flow<String> = prefs.map { it[Keys.BASEMAP] ?: "YN_SATELLITE" }
    val textSize: Flow<Float> = prefs.map { it[Keys.TEXT_SIZE] ?: 11f }
    val roomId: Flow<String> = prefs.map { it[Keys.ROOM_ID] ?: "" }
    val shareEnabled: Flow<Boolean> = prefs.map { it[Keys.SHARE_ENABLED] ?: false }
    val showCompass: Flow<Boolean> = prefs.map { it[Keys.SHOW_COMPASS] ?: true }
    val trueNorth: Flow<Boolean> = prefs.map { it[Keys.TRUE_NORTH] ?: false }
    val offlineMode: Flow<Boolean> = prefs.map { it[Keys.OFFLINE_MODE] ?: false }
    val layerState: Flow<String> = prefs.map { it[Keys.LAYER_STATE] ?: "" }
    val voltageFilter: Flow<String> = prefs.map { it[Keys.VOLTAGE_FILTER] ?: "500,220,110,35" }
    val groupId: Flow<Int> = prefs.map { it[Keys.GROUP_ID] ?: -1 }
    /** 名称（线路名 / 区域名 / 杆塔号）开始显示的缩放级别 */
    val labelZoom: Flow<Float> = prefs.map { it[Keys.LABEL_ZOOM] ?: 11f }
    /** 运行时服务器地址覆盖；三项均为空表示用编译期默认 */
    val serverAddresses: Flow<ServerOverride> = prefs.map {
        ServerOverride(
            it[Keys.SERVER_PMS] ?: "",
            it[Keys.SERVER_WS] ?: "",
            it[Keys.SERVER_TILES] ?: ""
        )
    }
    /** App 内模拟定位（仅本 App 生效） */
    val mockState: Flow<MockState> = prefs.map {
        MockState(
            enabled = it[Keys.MOCK_ENABLED] ?: false,
            lon = it[Keys.MOCK_LON]?.toDoubleOrNull(),
            lat = it[Keys.MOCK_LAT]?.toDoubleOrNull(),
            accuracy = it[Keys.MOCK_ACCURACY] ?: 5f
        )
    }

    suspend fun setBasemap(v: String) = context.dataStore.edit { it[Keys.BASEMAP] = v }
    suspend fun setTextSize(v: Float) = context.dataStore.edit { it[Keys.TEXT_SIZE] = v.coerceIn(8f, 20f) }
    suspend fun setRoomId(v: String) = context.dataStore.edit { it[Keys.ROOM_ID] = v }
    suspend fun setShareEnabled(v: Boolean) = context.dataStore.edit { it[Keys.SHARE_ENABLED] = v }
    suspend fun setShowCompass(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_COMPASS] = v }
    suspend fun setTrueNorth(v: Boolean) = context.dataStore.edit { it[Keys.TRUE_NORTH] = v }
    suspend fun setOfflineMode(v: Boolean) = context.dataStore.edit { it[Keys.OFFLINE_MODE] = v }
    suspend fun setLayerState(v: String) = context.dataStore.edit { it[Keys.LAYER_STATE] = v }
    suspend fun setVoltageFilter(v: String) = context.dataStore.edit { it[Keys.VOLTAGE_FILTER] = v }
    suspend fun setGroupId(v: Int) = context.dataStore.edit { it[Keys.GROUP_ID] = v }
    suspend fun setLabelZoom(v: Float) = context.dataStore.edit { it[Keys.LABEL_ZOOM] = v.coerceIn(6f, 17f) }
    suspend fun setServerAddresses(o: ServerOverride) = context.dataStore.edit {
        it[Keys.SERVER_PMS] = o.pms
        it[Keys.SERVER_WS] = o.ws
        it[Keys.SERVER_TILES] = o.tiles
    }

    suspend fun setMockState(s: MockState) = context.dataStore.edit {
        it[Keys.MOCK_ENABLED] = s.enabled
        it[Keys.MOCK_LON] = s.lon?.toString().orEmpty()
        it[Keys.MOCK_LAT] = s.lat?.toString().orEmpty()
        it[Keys.MOCK_ACCURACY] = s.accuracy.coerceIn(1f, 200f)
    }

    suspend fun saveCamera(lon: Double, lat: Double, zoom: Float) = context.dataStore.edit {
        it[stringPreferencesKey("last_lon")] = lon.toString()
        it[stringPreferencesKey("last_lat")] = lat.toString()
        it[floatPreferencesKey("last_zoom")] = zoom
    }

    suspend fun lastCamera(): Triple<Double, Double, Float>? {
        val p = prefs.first()
        val lon = p[stringPreferencesKey("last_lon")]?.toDoubleOrNull() ?: return null
        val lat = p[stringPreferencesKey("last_lat")]?.toDoubleOrNull() ?: return null
        val zoom = p[floatPreferencesKey("last_zoom")] ?: 12f
        return Triple(lon, lat, zoom)
    }

    /** 首次使用生成稳定 userId（位置共享用） */
    suspend fun ensureUserId(): String {
        val existing = prefs.first()[Keys.USER_ID]
        if (!existing.isNullOrBlank()) return existing
        val id = "user-" + UUID.randomUUID().toString().replace("-", "").take(6)
        context.dataStore.edit { it[Keys.USER_ID] = id }
        return id
    }
}
