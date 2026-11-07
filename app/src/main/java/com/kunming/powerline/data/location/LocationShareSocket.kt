package com.kunming.powerline.data.location

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.kunming.powerline.BuildConfig
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 位置共享 WebSocket 客户端
 * 协议：wss://server/ws/location?room=xxx
 * 上行：{"userId","lon","lat","speed","ts"}
 * 下行：同结构（其他成员位置）
 */
@Singleton
class LocationShareSocket @Inject constructor(
    private val config: com.kunming.powerline.data.prefs.ServerConfig
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var ws: WebSocket? = null
    private var room: String = ""
    private var userId: String = ""

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, PeerLocation>>(emptyMap())
    val peers: StateFlow<Map<String, PeerLocation>> = _peers.asStateFlow()

    private val _events = MutableSharedFlow<String>(
        extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<String> = _events.asSharedFlow()

    data class PeerLocation(
        val userId: String,
        val lon: Double,
        val lat: Double,
        val speed: Float,
        val ts: Long
    )

    fun connect(roomId: String, userId: String) {
        if (roomId.isBlank()) {
            _events.tryEmit("房间号为空")
            return
        }
        this.room = roomId
        this.userId = userId
        close()
        // 运行时地址：改服务器后重新开启共享即连到新地址
        val base = config.current.wsLocation
        val url = if (base.contains("room=")) base else "$base?room=$roomId"
        val req = Request.Builder().url(url).build()
        _events.tryEmit("正在连接 $url")
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
                _events.tryEmit("已连接位置共享房间：$roomId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val o = JSONObject(text)
                    val id = o.optString("userId", "")
                    val lon = o.optDouble("lon", Double.NaN)
                    val lat = o.optDouble("lat", Double.NaN)
                    if (id.isNotBlank() && id != userId && !lon.isNaN() && !lat.isNaN()) {
                        val p = PeerLocation(id, lon, lat, o.optDouble("speed", 0.0).toFloat(), o.optLong("ts", System.currentTimeMillis()))
                        _peers.value = _peers.value + (id to p)
                    }
                } catch (_: Throwable) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                _events.tryEmit("连接失败：${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
                _events.tryEmit("连接已关闭")
            }
        })
    }

    fun send(lon: Double, lat: Double, speed: Float) {
        if (!_connected.value) return
        val json = JSONObject().apply {
            put("userId", userId)
            put("lon", lon)
            put("lat", lat)
            put("speed", speed)
            put("ts", System.currentTimeMillis())
        }.toString()
        ws?.send(json)
    }

    fun close() {
        try {
            ws?.close(1000, "bye")
        } catch (_: Throwable) {
        }
        ws = null
        _connected.value = false
        _peers.value = emptyMap()
    }
}
