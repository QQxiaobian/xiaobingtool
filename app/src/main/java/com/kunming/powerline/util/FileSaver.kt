package com.kunming.powerline.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件与图片保存（Scoped Storage：用 MediaStore / SAF，不申请 WRITE_EXTERNAL_STORAGE）
 */
object FileSaver {

    /**
     * 保存地图截图到 公共 Pictures/输电巡检/
     * 返回可分享的 Uri
     */
    fun saveSnapshot(context: Context, bitmap: Bitmap, prefix: String = "巡检截图"): Uri? {
        val name = "$prefix-${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())}.png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/输电巡检")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "输电巡检")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, name)
            FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Uri.fromFile(f)
        }
    }

    /** 保存到 App 私有 pictures 目录（现场照片） */
    fun saveToAppPictures(context: Context, bitmap: Bitmap, prefix: String = "现场照片"): File? {
        return try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photos")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "$prefix-${System.currentTimeMillis()}.jpg")
            FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            f
        } catch (t: Throwable) {
            null
        }
    }

    /** 通过 SAF 写入文本（GeoJSON / 错误行 CSV） */
    fun writeText(context: Context, uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        true
    } catch (t: Throwable) {
        false
    }

    /** 通过 SAF 写入二进制（模板 xlsx） */
    fun writeBytes(context: Context, uri: Uri, bytes: ByteArray): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> out.write(bytes) }
        true
    } catch (t: Throwable) {
        false
    }

    fun readAsset(context: Context, assetName: String): ByteArray? = try {
        context.assets.open(assetName).use { it.readBytes() }
    } catch (t: Throwable) {
        null
    }

    fun appFilesDir(context: Context, sub: String): File {
        val dir = File(context.filesDir, sub)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun copyUriToFile(context: Context, uri: Uri, dest: File): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { out -> input.copyTo(out) }
        }
        true
    } catch (t: Throwable) {
        false
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        else -> String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    }
}
