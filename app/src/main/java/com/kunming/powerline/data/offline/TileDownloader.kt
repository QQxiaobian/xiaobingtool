package com.kunming.powerline.data.offline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * 区域瓦片预下载（天地图 WMTS）
 *
 * 选定一个矩形范围 + 缩放级别区间，批量下载瓦片到离线包目录。
 * 用途：巡检出发前把工作区域下载到本地，现场无网络时仍可看图。
 */
@Singleton
class TileDownloader @Inject constructor(
    private val context: Context,
    private val client: OkHttpClient,
    private val store: OfflineTileStore
) {

    data class Region(
        val name: String,
        val minLon: Double, val minLat: Double,
        val maxLon: Double, val maxLat: Double,
        val minZoom: Int = 10, val maxZoom: Int = 16
    )

    data class Progress(val done: Int, val total: Int, val failed: Int) {
        val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
    }

    /**
     * 预估瓦片数量
     */
    fun estimateCount(r: Region): Int {
        var total = 0
        for (z in r.minZoom..r.maxZoom) {
            val (x0, y0) = lonLatToTile(r.minLon, r.maxLat, z)
            val (x1, y1) = lonLatToTile(r.maxLon, r.minLat, z)
            total += (abs(x1 - x0) + 1) * (abs(y1 - y0) + 1)
        }
        return total
    }

    /**
     * 下载区域瓦片；urlTemplate 里用 {z}/{x}/{y} 占位。
     *
     * @param cancel 返回 true 时中止
     */
    suspend fun download(
        region: Region,
        urlTemplate: String,
        packName: String,
        onProgress: (Progress) -> Unit,
        cancel: () -> Boolean = { false }
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val outDir = File(store.tileRoot, packName).apply { mkdirs() }
            val total = estimateCount(region)
            var done = 0
            var failed = 0

            for (z in region.minZoom..region.maxZoom) {
                val (x0, y0) = lonLatToTile(region.minLon, region.maxLat, z)
                val (x1, y1) = lonLatToTile(region.maxLon, region.minLat, z)
                for (x in minOf(x0, x1)..maxOf(x0, x1)) {
                    for (y in minOf(y0, y1)..maxOf(y0, y1)) {
                        if (cancel()) return@runCatching done
                        val f = File(outDir, "$z/$x/$y.png")
                        done++
                        if (f.exists() && f.length() > 0) {
                            if (done % 20 == 0) onProgress(Progress(done, total, failed))
                            continue
                        }
                        val url = urlTemplate.replace("{z}", z.toString())
                            .replace("{x}", x.toString()).replace("{y}", y.toString())
                        try {
                            val resp = client.newCall(Request.Builder().url(url).build()).execute()
                            resp.use {
                                val bytes = it.body?.bytes()
                                if (it.isSuccessful && bytes != null && bytes.size > 100) {
                                    f.parentFile?.mkdirs()
                                    FileOutputStream(f).use { o -> o.write(bytes) }
                                } else failed++
                            }
                        } catch (t: Throwable) {
                            failed++
                        }
                        if (done % 20 == 0 || done == total) onProgress(Progress(done, total, failed))
                    }
                }
            }
            onProgress(Progress(done, total, failed))
            done - failed
        }
    }

    companion object {
        /** 经纬度 -> 瓦片编号（Web Mercator / XYZ） */
        fun lonLatToTile(lon: Double, lat: Double, z: Int): Pair<Int, Int> {
            val n = 1 shl z
            val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val latRad = Math.toRadians(lat.coerceIn(-85.05, 85.05))
            val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
            return x to y
        }

        /** 瓦片编号 -> 经纬度（瓦片左上角） */
        fun tileToLonLat(x: Int, y: Int, z: Int): Pair<Double, Double> {
            val n = 1 shl z
            val lon = x.toDouble() / n * 360.0 - 180.0
            val latRad = atan(sinh(PI * (1 - 2.0 * y / n)))
            return lon to Math.toDegrees(latRad)
        }
    }
}
