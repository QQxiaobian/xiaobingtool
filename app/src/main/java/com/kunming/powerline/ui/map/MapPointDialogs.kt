package com.kunming.powerline.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.PanelLine
import com.kunming.powerline.ui.theme.PanelText
import com.kunming.powerline.ui.theme.PanelTextSub
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.MapPointKind

/**
 * 长按地图坐标后的操作菜单（1.6.0）。
 *
 * 取代原先的「目标点操作」：不再有「当前位置→目标 连线测距」，
 * 改为把该坐标直接保存成危险点 / 护线员 / 收藏点 / 视频监控等业务点位。
 */
@Composable
fun CoordinateActionMenu(
    visible: Boolean,
    lon: Double,
    lat: Double,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    onResetNorth: () -> Unit,
    onPickKind: (MapPointKind) -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFBF6EE),
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                "此坐标操作", fontWeight = FontWeight.Bold,
                color = Color(0xFF2B2B2B), fontSize = 17.sp
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    String.format("经度 %.6f，纬度 %.6f", lon, lat),
                    fontSize = 11.sp, color = HintGray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ActionRow("🧭 导航到此位置", onDismiss, onNavigate)
                ActionRow("⬆ 重置地图方向（指北）", onDismiss, onResetNorth)
                Divider(color = Color(0x28000000), thickness = 0.8.dp)
                Text(
                    "设为业务点位", fontSize = 11.5.sp, color = PanelTextSub,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                MapPointKind.entries.forEach { kind ->
                    ActionRow("${kind.emoji} 设为${kind.label}", onDismiss) { onPickKind(kind) }
                }
            }
        },
        confirmButton = {
            Text(
                "关闭", color = PowerRed, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDismiss() }.padding(10.dp)
            )
        }
    )
}

@Composable
private fun ActionRow(text: String, onDismiss: () -> Unit, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onDismiss(); onClick() }
                .padding(vertical = 10.dp)
        ) {
            Text(text, fontSize = 13.5.sp, color = Color(0xFF333333))
        }
        Divider(color = Color(0x12000000), thickness = 0.7.dp)
    }
}

/** 选定点位类型后填写名称与附加信息 */
@Composable
fun AddPointDialog(
    kind: MapPointKind,
    lon: Double,
    lat: Double,
    onDismiss: () -> Unit,
    onConfirm: (name: String, extra: String) -> Unit
) {
    var name by remember(kind) { mutableStateOf("") }
    var extra by remember(kind) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFBF6EE),
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                "设为${kind.label}", fontWeight = FontWeight.Bold,
                color = Color(0xFF2B2B2B), fontSize = 17.sp
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    String.format("经度 %.6f，纬度 %.6f", lon, lat),
                    fontSize = 11.sp, color = HintGray
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称", fontSize = 11.5.sp) },
                    placeholder = { Text(kind.defaultName, fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = pointFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text(kind.extraLabel, fontSize = 11.5.sp) },
                    placeholder = { Text(kind.extraHint, fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = pointFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Text(
                "保存", color = PowerRed, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onConfirm(name, extra); onDismiss() }
                    .padding(10.dp)
            )
        },
        dismissButton = {
            Text(
                "取消", color = PanelTextSub,
                modifier = Modifier.clickable { onDismiss() }.padding(10.dp)
            )
        }
    )
}

@Composable
private fun pointFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PanelText,
    unfocusedTextColor = PanelText,
    focusedContainerColor = Color(0xFFFFFFFF),
    unfocusedContainerColor = Color(0xFFFFFFFF),
    cursorColor = PowerRed,
    focusedBorderColor = PowerRed,
    unfocusedBorderColor = PanelLine,
    focusedLabelColor = PowerRed,
    unfocusedLabelColor = PanelTextSub
)
