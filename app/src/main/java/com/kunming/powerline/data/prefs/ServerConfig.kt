package com.kunming.powerline.data.prefs

import com.kunming.powerline.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 三个服务端地址的**最终生效值**。
 *
 * 默认值来自编译期 buildConfigField；用户在「设置 → 服务器地址」里改过之后，
 * 由 [ServerConfig] 用 DataStore 里的覆盖值替换，App 无需重新打包。
 */
data class ServerAddresses(
    val pmsBase: String = BuildConfig.PMS_BASE_URL,
    val wsLocation: String = BuildConfig.WS_LOCATION_URL,
    val tileServer: String = BuildConfig.TILE_SERVER_URL
) {
    companion object {
        /** 补齐协议与结尾斜杠：`192.168.1.10:8080` -> `http://192.168.1.10:8080/` */
        fun normalize(input: String): String {
            var s = input.trim()
            if (s.isEmpty()) return ""
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
                s = "http://$s"
            }
            if (!s.endsWith("/")) s = "$s/"
            return s
        }

        /**
         * 从一个主地址推导三件套，供设置页「一键填充」使用。
         * NAS 端单端口部署时，8080 同时提供 REST / WebSocket / 瓦片：
         *
         *   http://192.168.1.10:8080/
         *     -> PMS   http://192.168.1.10:8080/
         *     -> WS    ws://192.168.1.10:8080/ws/location
         *     -> 瓦片  http://192.168.1.10:8080/tiles/{z}/{x}/{y}.png
         */
        fun deriveFrom(input: String): ServerAddresses {
            val base = normalize(input)
            if (base.isEmpty()) return ServerAddresses("", "", "")
            val wsScheme = if (base.startsWith("https://", true)) "wss://" else "ws://"
            val hostPath = base.substringAfter("://").trimEnd('/')
            return ServerAddresses(
                pmsBase = base,
                wsLocation = "$wsScheme$hostPath/ws/location",
                tileServer = "${base.trimEnd('/')}/tiles/{z}/{x}/{y}.png"
            )
        }
    }
}

/**
 * 服务端地址中心。
 *
 * - 暴露 [state] 给 Compose 观察，地址变化时地图会重建底图样式；
 * - 暴露同步的 [current] 给 OkHttp 拦截器 / WebSocket 使用（它们拿不到 Flow）；
 * - 持久化走 SettingsStore，App 重启后自动恢复。
 */
@Singleton
class ServerConfig @Inject constructor(private val store: SettingsStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ServerAddresses())
    val state: StateFlow<ServerAddresses> = _state.asStateFlow()

    /** 同步读取的当前生效地址 */
    val current: ServerAddresses get() = _state.value

    init {
        // 冷启动恢复 + 运行期保持同步（保存时会立即写一次，见 save）
        scope.launch {
            store.serverAddresses.collect { o ->
                _state.value = ServerAddresses(
                    pmsBase = o.pms.ifBlank { BuildConfig.PMS_BASE_URL }.let {
                        if (it.endsWith("/")) it else "$it/"
                    },
                    wsLocation = o.ws.ifBlank { BuildConfig.WS_LOCATION_URL },
                    tileServer = o.tiles.ifBlank { BuildConfig.TILE_SERVER_URL }
                )
            }
        }
    }

    /** 保存用户填写的三个地址；空串表示回落到编译期默认值 */
    suspend fun save(a: ServerAddresses) {
        val pms = if (a.pmsBase.endsWith("/")) a.pmsBase else "${a.pmsBase}/"
        store.setServerAddresses(SettingsStore.ServerOverride(pms, a.wsLocation, a.tileServer))
        // 立即生效，不必等 DataStore 回调（下一次请求就能用新地址）
        _state.value = a.copy(pmsBase = pms)
    }

    /** 恢复为 local.properties 编译进包里的默认地址 */
    suspend fun reset() {
        store.setServerAddresses(SettingsStore.ServerOverride("", "", ""))
        _state.value = ServerAddresses()
    }
}
