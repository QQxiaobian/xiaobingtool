package com.kunming.powerline.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.ui.theme.PowerRed

/**
 * 绘制工具类型
 */
enum class DrawTool(val label: String) {
    LINE("线段"), POLYGON("多边形"), CIRCLE("圆"), RECT("矩形"), TEXT("文字");

    val isArea: Boolean get() = this == POLYGON || this == CIRCLE || this == RECT
}

/**
 * 顶部标注绘制工具栏
 */
@Composable
fun DrawToolbar(
    tool: DrawTool,
    onToolChange: (DrawTool) -> Unit,
    vertexCount: Int,
    measureText: String,
    onUndo: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onPauseToggle: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xF5101418), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // 标签页
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DrawTool.entries.forEach { t ->
                val active = t == tool
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) PowerRed else Color(0x22FFFFFF))
                        .clickable { onToolChange(t) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        t.label, fontSize = 12.sp,
                        color = if (active) Color.White else Color(0xCCFFFFFF),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (measureText.isBlank()) "点击地图添加${tool.label}节点（$vertexCount 点）" else measureText,
                fontSize = 12.sp, color = Color(0xFF8EE0A8),
                modifier = Modifier.weight(1f), maxLines = 2
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolIcon(Icons.Default.Undo, "撤销", onUndo)
            ToolIcon(Icons.Default.Delete, "删除", onDelete)
            ToolIcon(Icons.Default.Pause, "暂停", onPauseToggle)
            ToolIcon(Icons.Default.Download, "保存", onSave)
            ToolIcon(Icons.Default.MoreVert, "更多", onMore)
            Spacer(Modifier.weight(1f))
            ToolIcon(Icons.Default.Close, "关闭", onClose)
        }
    }
}

@Composable
private fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Color(0xFFDDE3EA), modifier = Modifier.size(19.dp))
        Text(label, fontSize = 9.5.sp, color = Color(0x99FFFFFF))
    }
}

/**
 * 右侧悬浮工具栏（播放 / 相机 / 定位 / 放大 / 缩小 / 罗盘）
 */
@Composable
fun MapToolbar(
    bearing: Double,
    playing: Boolean,
    onPlayToggle: () -> Unit,
    onCamera: () -> Unit,
    onLocate: () -> Unit,
    following: Boolean = false,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onCompass: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(Color(0xE6101418), RoundedCornerShape(22.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ToolButton(if (playing) "⏸" else "▶", if (playing) "暂停漫游" else "线路漫游", PowerRed) { onPlayToggle() }
        ToolButton("📷", "截图", Color.Unspecified, onCamera)
        ToolButton(
            if (following) "🧭" else "🎯",
            if (following) "跟随中" else "定位",
            if (following) PowerRed else Color.Unspecified,
            onLocate
        )
        ToolButton("➕", "放大", Color.Unspecified, onZoomIn)
        ToolButton("➖", "缩小", Color.Unspecified, onZoomOut)
        CompassButton(bearing, onCompass)
    }
}

@Composable
private fun ToolButton(symbol: String, label: String, bg: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .size(width = 44.dp, height = 42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(symbol, fontSize = 16.sp, color = if (bg == Color.Unspecified) Color.White else Color.White)
    }
}

/** 罗盘：指针随 bearing 旋转 */
@Composable
private fun CompassButton(bearing: Double, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(30.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = size.minDimension / 2 - 1
            drawCircle(Color(0x33FFFFFF), radius = r, center = Offset(cx, cy))
            drawCircle(Color(0x66FFFFFF), radius = r, center = Offset(cx, cy), style = Stroke(1.2f))
            // 指针
            val rad = Math.toRadians(bearing - 90.0)
            val tipX = cx + (r * 0.78f * Math.cos(rad)).toFloat()
            val tipY = cy + (r * 0.78f * Math.sin(rad)).toFloat()
            val leftX = cx + (r * 0.34f * Math.cos(rad + 2.5)).toFloat()
            val leftY = cy + (r * 0.34f * Math.sin(rad + 2.5)).toFloat()
            val rightX = cx + (r * 0.34f * Math.cos(rad - 2.5)).toFloat()
            val rightY = cy + (r * 0.34f * Math.sin(rad - 2.5)).toFloat()
            val p = Path().apply {
                moveTo(tipX, tipY)
                lineTo(leftX, leftY)
                lineTo(cx, cy)
                lineTo(rightX, rightY)
                close()
            }
            drawPath(p, Color(0xFFE53935))
            drawCircle(Color.White, radius = r * 0.10f, center = Offset(cx, cy))
        }
    }
}

/**
 * 长按菜单（5 项）
 */
@Composable
fun LongPressMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    onCompassToggle: () -> Unit,
    onResetNorth: () -> Unit,
    onRouteToggle: () -> Unit,
    onCalibrate: () -> Unit
) {
    if (!visible) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFBF6EE),
        shape = RoundedCornerShape(18.dp),
        title = { Text("目标点操作", fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B), fontSize = 17.sp) },
        text = {
            Column {
                MenuRow("🧭 导航到此位置", onDismiss, onNavigate)
                MenuRow("🧲 显示 / 关闭罗盘", onDismiss, onCompassToggle)
                MenuRow("⬆ 重置地图方向（指北）", onDismiss, onResetNorth)
                MenuRow("📏 显示 / 关闭 当前位置→目标 连线测距", onDismiss, onRouteToggle)
                MenuRow("📍 将目标点设为当前所在位置（校准）", onDismiss, onCalibrate)
            }
        },
        confirmButton = {
            Text(
                "关闭", color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDismiss() }.padding(10.dp)
            )
        }
    )
}

@Composable
private fun MenuRow(text: String, onDismiss: () -> Unit, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onDismiss(); onClick() }
                .padding(vertical = 11.dp)
        ) {
            Text(text, fontSize = 13.5.sp, color = Color(0xFF333333))
        }
        androidx.compose.material3.Divider(color = Color(0x12000000), thickness = 0.7.dp)
    }
}
