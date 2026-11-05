package com.kunming.powerline.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 相机拍照 + Exif GPS 写入
 *
 * 规范（第 11 节）：使用系统相机 Intent 拍照，照片落到 App 私有目录，
 * 拍照后把当前定位与时间写入 Exif，便于后续按位置检索与上传。
 */
object PhotoCapture {

    private val TS = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)

    /** 生成照片目标文件 */
    fun newPhotoFile(context: Context): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "photos")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "IMG_${TS.format(Date())}.jpg")
    }

    /**
     * 构造拍照 Intent。返回的 File 需保留，用于拍照后处理。
     */
    fun buildIntent(context: Context, target: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 拍照后写入 Exif：GPS 经纬度、拍摄时间、备注
     */
    fun writeExif(
        file: File,
        lon: Double?,
        lat: Double?,
        altitude: Double? = null,
        title: String? = null,
        description: String? = null
    ): Boolean = try {
        val exif = ExifInterface(file.absolutePath)
        if (lon != null && lat != null) {
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, toDms(lat))
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0) "N" else "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, toDms(lon))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (lon >= 0) "E" else "W")
            if (altitude != null) {
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "%.0f/1".format(altitude))
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "0")
            }
        }
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.CHINA).format(Date()))
        title?.let { exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, it) }
        description?.let { exif.setAttribute(ExifInterface.TAG_USER_COMMENT, description) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            exif.setAttribute(ExifInterface.TAG_MAKE, "PowerLineInspection")
        }
        exif.saveAttributes()
        true
    } catch (t: Throwable) {
        false
    }

    /** 读取 Exif 中的 GPS（用于导入外部照片时定位） */
    fun readExifLocation(file: File): Pair<Double, Double>? = try {
        val e = ExifInterface(file.absolutePath)
        val ll = FloatArray(2)
        if (e.getLatLong(ll) && ll[0] != 0f && ll[1] != 0f) ll[0].toDouble() to ll[1].toDouble() else null
    } catch (t: Throwable) {
        null
    }

    /** 把十进制度转成 Exif 需要的 度/分/秒 有理数格式 */
    private fun toDms(value: Double): String {
        val v = kotlin.math.abs(value)
        val deg = v.toInt()
        val minFloat = (v - deg) * 60
        val min = minFloat.toInt()
        val sec = (minFloat - min) * 60
        return "$deg/1,$min/1,${Math.round(sec * 10000)}/10000"
    }
}

/**
 * 相机启动器封装（Compose 侧持有 File）
 */
class PendingPhoto(val file: File)
