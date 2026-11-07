package com.kunming.powerline

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.log.Logger
import org.maplibre.android.module.http.HttpRequestUtil
import java.util.concurrent.TimeUnit

/**
 * 浏览器风格 User-Agent 拦截器。
 *
 * 必须用具名类（不能用 lambda）：R8 在 release 混淆优化时，
 * 匿名 lambda interceptor 即使被 setOkHttpClient 引用也可能被内联/简化，
 * 但具名 Interceptor 子类因为可被外部引用，不会被剥离。
 *
 * 天地图"浏览器端"类型 Key 会按 User-Agent 判断来源，原生 UA（包名）
 * 会被天地图风控判为非浏览器请求返回 403（错误码 301012）。
 * 注入浏览器特征 UA（Mozilla + AppleWebKit + Chrome + Safari）后，
 * 天地图会放行瓦片请求（已用 curl 在网络层验证 200 + JPEG）。
 */
class BrowserUserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // 已有 UA 不动，没 UA 或是默认 native UA 时换成浏览器 UA
        val ua = original.header("User-Agent")
        val browserUA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0 Mobile Safari/537.36"
        val request =
            if (ua.isNullOrBlank() || ua.startsWith("com.kunming.powerline") || ua.contains("maplibre", ignoreCase = true)) {
                original.newBuilder().header("User-Agent", browserUA).build()
            } else {
                original
            }
        return chain.proceed(request)
    }
}

@HiltAndroidApp
class PowerLineApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            MapLibre.getInstance(
                this,
                BuildConfig.TIANDITU_KEY.ifBlank { null },
                WellKnownTileServer.MapLibre
            )
        } catch (t: Throwable) {
            Log.e("PowerLineApp", "MapLibre 初始化失败：${t.message}", t)
        }
        // 注入 OkHttp 客户端，强制带浏览器 User-Agent
        try {
            val client =
                OkHttpClient.Builder()
                    .addInterceptor(BrowserUserAgentInterceptor())
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
            HttpRequestUtil.setOkHttpClient(client)
            // 失败时打印瓦片 URL，方便排查
            HttpRequestUtil.setPrintRequestUrlOnFailure(true)
        } catch (t: Throwable) {
            Log.e("PowerLineApp", "MapLibre UA 设置失败：${t.message}", t)
        }
        try {
            Logger.setVerbosity(if (BuildConfig.DEBUG_MODE) Logger.INFO else Logger.WARN)
        } catch (_: Throwable) {
        }
    }
}