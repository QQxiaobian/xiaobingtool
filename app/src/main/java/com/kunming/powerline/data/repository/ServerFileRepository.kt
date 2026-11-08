package com.kunming.powerline.data.repository

import android.content.Context
import android.net.Uri
import com.kunming.powerline.data.remote.PmsApi
import com.kunming.powerline.data.remote.model.ServerFileDto
import com.kunming.powerline.data.remote.model.ServerFileKind
import com.kunming.powerline.util.FileSaver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NAS 原始地理文件（KML / KMZ / GeoJSON / GPX / SHP）的列出、下载、上传、删除。
 *
 * 下载只负责把字节落到 App 私有缓存目录并返回 file:// Uri 与原始文件名；
 * 真正的解析与入库由 MapViewModel 交给 GeoFileParser / ThematicRepository / LineRepository，
 * 与本地文件导入走完全相同的路径。
 */
@Singleton
class ServerFileRepository @Inject constructor(
    private val api: PmsApi,
    @ApplicationContext private val context: Context
) {
    suspend fun list(): Result<List<ServerFileDto>> = withContext(Dispatchers.IO) {
        runCatching { api.listServerFiles().sortedBy { it.name.lowercase() } }
    }

    /** @return (本地 Uri, 原始文件名) —— 文件名必须显式传给解析器，KMZ/SHP 靠扩展名分派 */
    suspend fun download(f: ServerFileDto): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = api.downloadServerFile(f.id)
            val name = ServerFileKind.safeLocalName(f.name)
            val file = File(FileSaver.appFilesDir(context, "server_files"), name)
            body.byteStream().use { input ->
                FileOutputStream(file).use { out -> input.copyTo(out) }
            }
            require(file.length() > 0) { "下载的文件为空" }
            Uri.fromFile(file) to name
        }
    }

    suspend fun upload(uri: Uri, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = ServerFileKind.safeLocalName(fileName)
            require(ServerFileKind.isSupported(name)) {
                "不支持的文件类型（仅支持 KML/KMZ/GeoJSON/JSON/GPX/SHP/ZIP）"
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取所选文件")
            require(bytes.isNotEmpty()) { "文件内容为空" }

            // multipart 的 filename 用 ASCII 占位，真实中文名走 X-File-Name（UTF-8 URL 编码）
            val asciiName = "upload." + ServerFileKind.extOf(name)
            val part = MultipartBody.Part.createFormData(
                "file", asciiName,
                bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
            val ack = api.uploadServerFile(part, encoded)
            if (!ack.success) error(ack.message ?: "服务器拒绝上传")
            "已上传「$name」到服务器（${FileSaver.formatSize(bytes.size.toLong())}）"
        }
    }

    suspend fun delete(id: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val ack = api.deleteServerFile(id)
            if (!ack.success) error(ack.message ?: "删除失败")
            "已从服务器删除"
        }
    }
}
