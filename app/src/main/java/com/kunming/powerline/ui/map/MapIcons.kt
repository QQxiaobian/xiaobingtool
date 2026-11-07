package com.kunming.powerline.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import com.kunming.powerline.ui.layer.LayerIds
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * 图标绘制工具：用 Canvas 现画矢量图标，避免依赖 PNG 资源
 */
object MapIcons {

    fun towerIcon(color: Int, sizePx: Int = 96, selected: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val w = sizePx.toFloat()
        val cx = w / 2f
        // 塔身三角
        val path = Path().apply {
            moveTo(cx, w * 0.12f)
            lineTo(w * 0.78f, w * 0.78f)
            lineTo(w * 0.22f, w * 0.78f)
            close()
        }
        c.drawPath(path, p)
        // 横担
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = w * 0.07f }
        c.drawLine(w * 0.18f, w * 0.46f, w * 0.82f, w * 0.46f, bar)
        c.drawLine(w * 0.26f, w * 0.62f, w * 0.74f, w * 0.62f, bar)
        // 白色描边（选中时黄色外圈）
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.07f
            this.color = if (selected) Color.YELLOW else Color.WHITE
        }
        c.drawPath(path, ring)
        return bmp
    }

    fun circleIcon(color: Int, sizePx: Int = 72, stroke: Int = Color.WHITE): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f * 0.78f, p)
        val s = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = sizePx * 0.10f; this.color = stroke
        }
        c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f * 0.78f, s)
        return bmp
    }

    /** 三角形警示图标 */
    fun triangleIcon(color: Int, sizePx: Int = 84): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = sizePx.toFloat()
        val path = Path().apply {
            moveTo(w / 2f, w * 0.08f)
            lineTo(w * 0.94f, w * 0.88f)
            lineTo(w * 0.06f, w * 0.88f)
            close()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = w * 0.07f
        })
        val ex = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; strokeWidth = w * 0.09f; strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        c.drawLine(w * 0.5f, w * 0.36f, w * 0.5f, w * 0.64f, ex)
        c.drawCircle(w * 0.5f, w * 0.74f, w * 0.045f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
        return bmp
    }

    /** 相机 / 视频图标 */
    fun cameraIcon(color: Int, sizePx: Int = 80): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = sizePx.toFloat()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        c.drawRoundRect(w * 0.08f, w * 0.28f, w * 0.72f, w * 0.80f, w * 0.10f, w * 0.10f, body)
        val lens = Path().apply {
            moveTo(w * 0.74f, w * 0.44f)
            lineTo(w * 0.94f, w * 0.30f)
            lineTo(w * 0.94f, w * 0.78f)
            lineTo(w * 0.74f, w * 0.64f)
            close()
        }
        c.drawPath(lens, body)
        c.drawCircle(w * 0.40f, w * 0.54f, w * 0.14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
        })
        return bmp
    }

    /** 电力塔（交叉跨越） */
    fun crossIcon(color: Int, sizePx: Int = 92): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = sizePx.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = w * 0.08f; strokeCap = Paint.Cap.ROUND }
        c.drawLine(w * 0.15f, w * 0.25f, w * 0.85f, w * 0.75f, p)
        c.drawLine(w * 0.85f, w * 0.25f, w * 0.15f, w * 0.75f, p)
        c.drawCircle(w * 0.5f, w * 0.5f, w * 0.10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
        return bmp
    }

    fun personIcon(color: Int, sizePx: Int = 80): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = sizePx.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawCircle(w * 0.5f, w * 0.30f, w * 0.18f, p)
        val body = Path().apply {
            moveTo(w * 0.5f, w * 0.48f)
            cubicTo(w * 0.80f, w * 0.50f, w * 0.82f, w * 0.92f, w * 0.50f, w * 0.92f)
            cubicTo(w * 0.18f, w * 0.92f, w * 0.20f, w * 0.50f, w * 0.5f, w * 0.48f)
            close()
        }
        c.drawPath(body, p)
        return bmp
    }

    fun starIcon(color: Int, sizePx: Int = 80): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = sizePx.toFloat()
        val path = Path()
        val cx = w / 2f
        val cy = w / 2f
        val outer = w * 0.46f
        val inner = outer * 0.45f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (r * Math.cos(angle)).toFloat()
            val y = cy + (r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = w * 0.05f
        })
        return bmp
    }

    fun droneIcon(color: Int, sizePx: Int = 92): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = sizePx.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = w * 0.07f }
        c.drawCircle(w * 0.5f, w * 0.5f, w * 0.16f, p)
        val arms = listOf(
            w * 0.5f to w * 0.5f to (w * 0.22f to w * 0.22f),
            w * 0.5f to w * 0.5f to (w * 0.78f to w * 0.22f),
            w * 0.5f to w * 0.5f to (w * 0.22f to w * 0.78f),
            w * 0.5f to w * 0.5f to (w * 0.78f to w * 0.78f)
        )
        arms.forEach { (a, b) -> c.drawLine(a.first, a.second, b.first, b.second, p) }
        listOf(
            w * 0.22f to w * 0.22f, w * 0.78f to w * 0.22f,
            w * 0.22f to w * 0.78f, w * 0.78f to w * 0.78f
        ).forEach { (x, y) ->
            c.drawCircle(x, y, w * 0.11f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
            })
        }
        return bmp
    }

    /** 截图 / 圆环标记 */
    fun ringIcon(color: Int, sizePx: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = sizePx.toFloat()
        c.drawCircle(w / 2f, w / 2f, w * 0.34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w * 0.16f; this.color = color
        })
        c.drawCircle(w / 2f, w / 2f, w * 0.08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return bmp
    }

    /**
     * 方向箭头：预渲染成 N 个固定角度的位图（白填充 + 深蓝描边）
     *
     * 不使用 icon-rotate 表达式，改为按方位角挑选对应角度的图标，
     * 由 iconImage(Expression.get("icon")) 驱动，兼容性与杆塔图标一致，
     * 避免某些设备上 rotate 表达式不生效、箭头一直指向正北的问题。
     */
    fun headingIcons(sizePx: Int = 96, stepDeg: Int = 10): List<Pair<String, Bitmap>> {
        val out = ArrayList<Pair<String, Bitmap>>(360 / stepDeg)
        var deg = 0
        while (deg < 360) {
            out.add(headingIconName(deg.toFloat()) to headingIcon(deg, sizePx))
            deg += stepDeg
        }
        return out
    }

    /** 方位角 -> 预渲染图标名（就近取整到 stepDeg） */
    fun headingIconName(deg: Float, stepDeg: Int = 10): String {
        val n = 360 / stepDeg
        val d = ((deg % 360f) + 360f) % 360f
        val idx = ((d / stepDeg) + 0.5f).toInt() % n
        return "icon-heading-${idx * stepDeg}"
    }

    /**
     * 单个角度的方向箭头（0° = 箭头朝上 = 正北，顺时针）
     */
    fun headingIcon(bearingDeg: Int, sizePx: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = sizePx.toFloat()
        val path = Path().apply {
            moveTo(w * 0.50f, w * 0.14f)          // 尖端（指向行进方向）
            lineTo(w * 0.82f, w * 0.86f)         // 右后
            lineTo(w * 0.50f, w * 0.68f)         // 底部凹口
            lineTo(w * 0.18f, w * 0.86f)         // 左后
            close()
        }
        c.save()
        c.rotate(bearingDeg.toFloat(), w / 2f, w / 2f)
        // 由外到内三层：深蓝细边 -> 白色宽边 -> 蓝色填充
        // 深蓝细边保证浅色底图上也有轮廓，白边保证卫星图上不发黑
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.parseColor("#0D47A1")
            style = Paint.Style.STROKE
            strokeWidth = w * 0.22f
            strokeJoin = Paint.Join.ROUND
        })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = w * 0.16f
            strokeJoin = Paint.Join.ROUND
        })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.parseColor("#1E88E5")
            style = Paint.Style.FILL
        })
        c.restore()
        return bmp
    }
}
