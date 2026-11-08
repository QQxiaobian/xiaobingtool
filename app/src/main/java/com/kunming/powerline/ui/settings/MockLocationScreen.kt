package com.kunming.powerline.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PowerRed

/**
 * App 内自用的「模拟定位」。
 *
 * 仅用于演示、离线测试、无 GPS 信号时手动补录点位：改的是本 App 的蓝点与
 * 「当前位置」取值。不写入系统 LocationProvider，不影响任何其他应用，
 * 不需要 root，位置共享与巡检轨迹仍使用真实 GPS。
 *
 * 明确不提供：root 权限的系统级虚拟定位 / 向第三方应用注入伪造位置。
 */
@Composable
fun MockLocationScreen(
    vm: MapViewModel,
    onBack: () -> Unit,
    onPickOnMap: () -> Unit = {}
) {
    val state by vm.mockState.collectAsStateWithLifecycle()
    var lon by remember(state.lon) { mutableStateOf(state.lon?.toString() ?: "") }
    var lat by remember(state.lat) { mutableStateOf(state.lat?.toString() ?: "") }
    var status by remember { mutableStateOf<String?>(null) }

    fun apply(enabled: Boolean) {
        vm.setMockLocation(
            enabled,
            lon.trim().toDoubleOrNull(),
            lat.trim().toDoubleOrNull()
        ) { status = it }
    }

    LightPanelTheme {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFAFBF6EE))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color(0xFF333333))
                }
                Text(
                    "模拟定位（仅本 App）", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF2B2B2B), modifier = Modifier.weight(1f)
                )
            }
            Divider(color = Color(0x14000000), thickness = 0.8.dp)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3E0), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        "这是 App 内部的演示 / 测试定位，不是系统级虚拟定位。" +
                            "本 App 不提供 root 权限的全局位置伪造，也不会向其他应用注入位置。",
                        fontSize = 12.sp, color = Color(0xFF8A4B00), lineHeight = 18.sp
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用模拟定位", fontSize = 13.5.sp, color = Color(0xFF2B2B2B))
                        Text(
                            if (state.valid) "当前：模拟坐标已生效" else "当前：使用真实 GPS",
                            fontSize = 11.sp,
                            color = if (state.valid) PowerRed else HintGray
                        )
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { apply(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PowerRed
                        )
                    )
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text("经度（WGS84）", fontSize = 11.sp) },
                    placeholder = { Text("102.71832", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("纬度（WGS84）", fontSize = 11.sp) },
                    placeholder = { Text("25.03871", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                )
                Text(
                    "可粘贴度分秒转换后的十进制坐标；也可点击下方按钮，直接在地图上点选目标位置。",
                    fontSize = 10.5.sp, color = HintGray, lineHeight = 16.sp
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { lon = "102.71832"; lat = "25.03871"; status = "已填入示例坐标（昆明）" },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("填入示例坐标", fontSize = 12.sp) }
                    Button(
                        onClick = { apply(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PowerRed, contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("保存并开启", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPickOnMap,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) { Text("在地图上点选位置", fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { apply(false) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) { Text("关闭模拟定位，恢复真实 GPS", fontSize = 12.sp) }

                if (status != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(status!!, fontSize = 11.5.sp, color = Color(0xFF2E7D32))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = Color(0x14000000), thickness = 0.7.dp)
                Spacer(Modifier.height(10.dp))
                Text("功能边界", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PowerRed)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "只影响本 App 的地图蓝点与「当前位置」补录，不修改系统位置。",
                    "不影响微信、导航、考勤、游戏等任何其他应用，也不需要 root 或系统级权限。",
                    "位置共享与巡检轨迹仍读取真实 GPS，模拟坐标不会被上报给同事或服务器。",
                    "开启后主界面顶部会显示「模拟定位」标记，避免误当成真实位置。",
                    "模拟状态下新建的收藏点 / 起飞点仅用于演示与测试，请勿作为正式巡检记录。"
                ).forEach { t ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("· ", fontSize = 11.5.sp, color = PowerRed)
                        Text(t, fontSize = 11.5.sp, color = Color(0xFF5C544A), lineHeight = 17.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
