package com.kunming.powerline.data.update

import com.kunming.powerline.BuildConfig
import com.kunming.powerline.util.VersionCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** GitHub 最新 Release 的检查结果 */
data class UpdateInfo(
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val hasUpdate: Boolean
)

/**
 * 「检查更新」：直接请求 GitHub Releases API 的 latest 端点。
 *
 * 用独立的 OkHttpClient，不复用挂了动态服务器地址拦截器的 PmsApi，
 * 因此检查更新与内网 NAS 是否可达无关；请求失败会原样返回给 UI 提示。
 */
object UpdateChecker {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun check(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(BuildConfig.UPDATE_CHECK_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PowerLineInspection")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("服务器返回 HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) error("返回内容为空")
                val o = JSONObject(body)
                val tag = o.optString("tag_name").ifBlank { o.optString("name") }
                if (tag.isBlank()) error("未解析到版本号")

                val apkUrl = o.optJSONArray("assets")?.let { arr ->
                    (0 until arr.length())
                        .mapNotNull { arr.optJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                        ?.optString("browser_download_url")
                        ?.takeIf { it.isNotBlank() }
                }

                UpdateInfo(
                    latestVersion = tag,
                    releaseName = o.optString("name").ifBlank { tag },
                    releaseNotes = o.optString("body").take(800),
                    htmlUrl = o.optString("html_url").ifBlank { BuildConfig.RELEASES_URL },
                    apkDownloadUrl = apkUrl,
                    hasUpdate = VersionCompare.isNewer(currentVersion, tag)
                )
            }
        }
    }
}
