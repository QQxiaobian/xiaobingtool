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
                // 主界面统一浅米色，激活时用品牌红
                .background(if (active) Color(0xFFB71C1C) else Color(0xF2FFFDF8))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, label,
                tint = if (active) Color.White else Color(0xFF3B3B3B),
                modifier = Modifier.size(19.dp)
            )
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
 * 真北模式：根据手机朝向自动旋转地图
 */
object TrueNorthController {
    @Volatile
    var enabled: Boolean = false
}
