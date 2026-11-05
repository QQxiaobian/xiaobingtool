package com.kunming.powerline.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.data.local.entity.TowerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng

/**
 * 小尺寸圆形图标按钮（顶部工具区）
 */
@Composable
fun RoundIconButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (active) Color(0xE6B71C1C) else Color(0xDD101418))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
}

/**
 * 小操作按钮（详情卡片内）
 */
@Composable
fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFB71C1C))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.5.sp)
    }
}

/**
 * 沿线路杆塔坐标序列做 CameraPosition 动画漫游
 */
fun startLineTour(
    scope: CoroutineScope,
    controller: MapController,
    lineId: String?,
    allTowers: List<TowerEntity>,
    onFinish: () -> Unit
) {
    val towers = allTowers
        .filter { lineId == null || it.lineId == lineId }
        .sortedBy { it.seq }
    if (towers.size < 2) {
        onFinish()
        return
    }
    scope.launch {
        var lastJob: Job? = null
        towers.forEach { t ->
            lastJob?.cancel()
            val map = controller.map ?: return@launch
            val pos = CameraPosition.Builder()
                .target(LatLng(t.lat, t.lon))
                .zoom(15.5)
                .bearing(map.cameraPosition?.bearing ?: 0.0)
                .tilt(30.0)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 1400)
            delay(1600)
        }
        delay(400)
        onFinish()
    }
}

/**
 * 真北模式：根据手机朝向自动旋转地图
 */
object TrueNorthController {
    @Volatile
    var enabled: Boolean = false
}
