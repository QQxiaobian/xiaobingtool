package com.kunming.powerline.ui.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kunming.powerline.util.GeoUtils

/**
 * 导航（geo: 协议，按已安装包优先高德 / 百度）
 */
object NavigationHelper {

    data class NavTarget(val name: String, val lon: Double, val lat: Double)

    fun open(context: Context, target: NavTarget) {
        // 高德用 GCJ02
        val (gLon, gLat) = GeoUtils.wgs84ToGcj02(target.lon, target.lat)
        val name = Uri.encode(target.name.replacingOccurrences())

        val amapUri = Uri.parse("androidamap://route/plan/?sourceApplication=powerline&dlat=$gLat&dlon=$gLon&dname=$name&dev=0&t=0")
        val baiduUri = Uri.parse("baidumap://map/direction?destination=latlng:$gLat,$gLon|name:$name&coord_type=gcj02&mode=driving")
        val geoUri = Uri.parse("geo:$gLat,$gLon?q=$gLat,$gLon($name)")

        val candidates = listOf(
            "com.autonavi.minimap" to amapUri,
            "com.baidu.BaiduMap" to baiduUri
        )
        for ((pkg, uri) in candidates) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        }
        // 退化为系统地图
        val fallback = Intent(Intent.ACTION_VIEW, geoUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(fallback)
        } catch (t: Throwable) {
            // 无可用地图应用
        }
    }

    private fun String.replacingOccurrences(): String = this.replace(" ", "")
}
