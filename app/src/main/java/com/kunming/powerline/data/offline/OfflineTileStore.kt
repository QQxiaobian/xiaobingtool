package com.kunming.powerline.data.offline

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 离线瓦片包管理
 *
 * 规范（第 11 节）：mbtiles 导入到 App 私有目录，供 MapLibre 离线渲染使用。
 * 说明：MapLibre Native 的 mbtiles 支持需要通过 offline manager 或自定义 source，
 * 本实现把 mbtiles 解包为标准 XYZ 目录结构（{z}/{x}/{y}.png），
 * 这样只需把图片源指向 file:// 路径即可离线显示，兼容性最好。
 */
@Singleton
class OfflineTileStore @Inject constructor(private val context: Context) {

    /** 离线瓦片根目录：/Android/data/<pkg>/files/tiles */
    val tileRoot: File
        get() {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            return File(base, "tiles").apply { if (!exists()) mkdirs() }
        }

    /** 已导入的瓦片包 */
    fun packs(): List<File> = tileRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()

    /** 统计某包瓦片数 */
    fun tileCount(pack: File): Int = with0 { pack.walkTopDown().count { it.isFile && it.extension in setOf("png", "jpg", "jpeg", "webp") } }

    private fun <T> with0(block: () -> T): T = runCatching(block).getOrDefault(0 as T)

    /**
     * 导入 mbtiles：读取 tiles 表并导出为 XYZ 目录
     *
     * @param onProgress 0..1
     * @return 导入成功的瓦片数
     */
    suspend fun importMbtiles(
        uri: Uri,
        packName: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            // 先复制到临时文件，SQLite 需要真实路径
            val tmp = File(context.cacheDir, "import_${System.currentTimeMillis()}.mbtiles")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { out -> input.copyTo(out, 64 * 1024) }
            } ?: error("无法读取所选文件")

            val outDir = File(tileRoot, packName).apply { mkdirs() }
            val db = SQLiteDatabase.openDatabase(tmp.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            var total = 0
            var done = 0
            db.rawQuery("SELECT COUNT(*) FROM tiles", null).use { c -> if (c.moveToFirst()) total = c.getInt(0) }
            if (total == 0) {
                db.close(); tmp.delete()
                error("该 mbtiles 无瓦片数据（可能使用了 tile_data 视口表，请先转换）")
            }

            // mbtiles 采用 TMS 方案（y 轴向上），需翻转到 XYZ（y 轴向下）
            db.rawQuery("SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles", null).use { c ->
                val buf = ByteArray(8192)
                while (c.moveToNext()) {
                    val z = c.getInt(0)
                    val x = c.getInt(1)
                    val tmsY = c.getInt(2)
                    val y = (1 shl z) - 1 - tmsY
                    val data = c.getBlob(3) ?: continue
                    val dir = File(outDir, "$z/$x").apply { mkdirs() }
                    val ext = sniffExt(data)
                    File(dir, "$y.$ext").writeBytes(data)
                    done++
                    if (done % 200 == 0) onProgress(done.toFloat() / total)
                    // 避免一次性占用过多内存
                    if (buf.isEmpty()) break
                }
            }
            db.close()
            tmp.delete()
            onProgress(1f)
            saveMeta(outDir, total)
            done
        }
    }

    /** 导入一组已解包的瓦片 zip（结构需为 z/x/y.png） */
    suspend fun importTileZip(uri: Uri, packName: String, onProgress: (Float) -> Unit = {}): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val outDir = File(tileRoot, packName).apply { mkdirs() }
                var count = 0
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.util.zip.ZipInputStream(input).use { zip ->
                        var e = zip.nextEntry
                        while (e != null) {
                            val name = e.name
                            if (!e.isDirectory && Regex("""(\d+)/(\d+)/(\d+)\.(png|jpg|jpeg|webp)""").containsMatchIn(name)) {
                                val f = File(outDir, name)
                                f.parentFile?.mkdirs()
                                FileOutputStream(f).use { zip.copyTo(it, 32 * 1024) }
                                count++
                                if (count % 200 == 0) onProgress(0f)
                            }
                            zip.closeEntry()
                            e = zip.nextEntry
                        }
                    }
                } ?: error("无法读取所选文件")
                onProgress(1f)
                saveMeta(outDir, count)
                count
            }
        }

    /** 删除离线包 */
    fun deletePack(name: String): Boolean = runCatching {
        File(tileRoot, name).deleteRecursively()
    }.getOrDefault(false)

    /** 离线包大小 */
    fun packSize(pack: File): Long = runCatching { pack.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    private fun sniffExt(data: ByteArray): String = when {
        data.size > 8 && data[0] == 0x89.toByte() && data[1] == 'P'.code.toByte() -> "png"
        data.size > 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> "jpg"
        else -> "png"
    }

    private fun saveMeta(dir: File, count: Int) {
        runCatching {
            File(dir, "pack.json").writeText(
                """{"tileCount":$count,"createdAt":${System.currentTimeMillis()},"scheme":"xyz"}"""
            )
        }
    }

    /** 供 MapLibre 使用的本地瓦片模板（file:// 形式） */
    fun localTileUrl(packName: String): String = "file://${File(tileRoot, packName).absolutePath}/{z}/{x}/{y}.png"

    companion object {
        const val DEFAULT_PACK = "offline"
    }
}
