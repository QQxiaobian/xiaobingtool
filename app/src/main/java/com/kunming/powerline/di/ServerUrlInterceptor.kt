package com.kunming.powerline.di

import com.kunming.powerline.data.prefs.ServerConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 PMS 请求动态指向运行时配置的服务器地址。
 *
 * Retrofit 的 baseUrl 在构建时就固定了，改地址不能重建 Retrofit 单例；
 * 这里用拦截器在**每次请求**时把 scheme/host/port/路径前缀换成设置页里的值，
 * 保留 `/api/...` 路径，因此改地址后无需重启 App、无需重新打包。
 *
 * 注意：只挂在 PmsApi 专用的 OkHttpClient 上，不能加到全局 client，
 * 否则 TileDownloader 下载天地图在线瓦片时也会被错误地重定向到 NAS。
 */
@Singleton
class ServerUrlInterceptor @Inject constructor(
    private val config: ServerConfig
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = config.current.pmsBase
        val uri = runCatching { URI(base) }.getOrNull()
        val host = uri?.host
        if (host.isNullOrBlank()) return chain.proceed(request)

        val scheme = uri.scheme?.lowercase() ?: "http"
        val port = if (uri.port > 0) {
            uri.port
        } else {
            if (scheme == "https") 443 else 80
        }
        val prefix = (uri.path ?: "").trimEnd('/')

        val newUrl = request.url.newBuilder()
            .scheme(scheme)
            .host(host)
            .port(port)
            .encodedPath(prefix + request.url.encodedPath)
            .build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
