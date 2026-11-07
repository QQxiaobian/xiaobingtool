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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.data.repository.ValidationIssue
import com.kunming.powerline.data.repository.SectionLengthResult
import com.kunming.powerline.ui.theme.CreamBackground
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PowerRed

/**
 * 设置面板（统一浅米色 + 深色文字，工业风）
 */
@Composable
fun SettingsPanel(
    roomId: String,
    onRoomIdChange: (String) -> Unit,
    sharing: Boolean,
    shareConnected: Boolean,
    peerCount: Int,
    onStartShare: () -> Unit,
    onStopShare: () -> Unit,
    textSize: Float,
    onTextSizeChange: (Float) -> Unit,
    labelZoom: Float,
    onLabelZoomChange: (Float) -> Unit,
    trueNorth: Boolean,
    onTrueNorthChange: (Boolean) -> Unit,
    onValidateLedger: () -> Unit,
    validationIssues: List<ValidationIssue>,
    onOpenHelp: () -> Unit,
    onOpenMapDataSettings: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onOpenGroupSettings: () -> Unit,
    onBack: () -> Unit,
    sectionResult: SectionLengthResult?,
    onCalcSection: (String, String?, String?) -> Unit,
    onExportOverlays: () -> Unit
) {
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
                "设置", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF2B2B2B), modifier = Modifier.weight(1f)
            )
        }
        Divider(color = Color(0x14000000), thickness = 0.8.dp)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("位置共享")
            OutlinedTextField(
                value = roomId,
                onValueChange = onRoomIdChange,
                label = { Text("房间号", fontSize = 12.sp) },
                placeholder = { Text("输入房间号，如 YN2024", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            )
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (sharing) {
                        if (shareConnected) "共享中 · 在线成员 $peerCount" else "共享中 · 正在连接…"
                    } else "未开启位置共享",
                    fontSize = 12.5.sp, color = if (sharing) Color(0xFF2E7D32) else HintGray,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = if (sharing) onStopShare else onStartShare,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sharing) Color(0xFF757575) else PowerRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(if (sharing) "停止共享" else "开启共享", fontSize = 12.5.sp)
                }
            }
            Text(
                "开启后将在通知栏常驻前台服务，每 1 秒上报一次位置；Android 12+ 需授予「始终允许」定位权限。",
                fontSize = 11.sp, color = HintGray, lineHeight = 16.sp
            )

            SectionTitle("地图标签绘制设置")
            Text("杆塔号 / 电缆节点名 文字大小：${textSize.toInt()}sp", fontSize = 12.5.sp, color = Color(0xFF444444))
            Slider(
                value = textSize,
                onValueChange = { onTextSizeChange(it) },
                valueRange = 8f..20f,
                steps = 11,
                colors = SliderDefaults.colors(
                    thumbColor = PowerRed,
                    activeTrackColor = PowerRed
                )
            )

            Divider(color = Color(0x14000000), thickness = 0.7.dp, modifier = Modifier.padding(vertical = 6.dp))
            Text(
                "名称显示缩放级别：${"%.0f".format(labelZoom)} 级",
                fontSize = 12.5.sp, color = Color(0xFF444444)
            )
            Text(
                "控制线路名 / 专题点名 / 杆塔号；区域名始终显示，业务点位名称 12 级起显示",
                fontSize = 10.5.sp, color = Color(0xFF8D857B)
            )
            Slider(
                value = labelZoom,
                onValueChange = { onLabelZoomChange(it) },
                valueRange = 6f..17f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor = PowerRed,
                    activeTrackColor = PowerRed
                )
            )
            Text(
                "地图缩小到该级别以下时，线路名、区域名、杆塔号都不再绘制，" +
                    "只保留线 / 面 / 点位轮廓，可显著减少数据量大时的卡顿。" +
                    "杆塔号会在该级别基础上再推迟 2.5 级显示。",
                fontSize = 11.sp, color = HintGray, lineHeight = 16.sp
            )

            SectionTitle("传感器")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("真北模式", fontSize = 13.5.sp, color = Color(0xFF333333))
                    Text("地图根据手机朝向自动旋转（磁力计 + 加速度计）", fontSize = 11.sp, color = HintGray)
                }
                Switch(
                    checked = trueNorth,
                    onCheckedChange = onTrueNorthChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PowerRed
                    )
                )
            }

            SectionTitle("服务器（自建 NAS）")
            SettingRow("地图 / 线路台账 / 位置共享地址，可随时修改", "设置", onOpenServerSettings)

            SectionTitle("基础地图数据设置")
            SettingRow("在线地图管理 / 自定义 Style / 导入离线 mbtiles", "›", onOpenMapDataSettings)
            SettingRow("地图组设置（按区域 / 单位分组）", "›", onOpenGroupSettings)

            SectionTitle("台账校验")
            SettingRow("校验坐标范围、杆塔号重复、档距异常", "执行", onValidateLedger)
            if (validationIssues.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text("共 ${validationIssues.size} 项问题", fontSize = 12.sp, color = PowerRed, fontWeight = FontWeight.Bold)
                    validationIssues.take(50).forEach { issue ->
                        Column(Modifier.padding(vertical = 3.dp)) {
                            Text("· ${issue.type}｜${issue.target}", fontSize = 11.5.sp, color = Color(0xFF333333))
                            Text(issue.detail, fontSize = 10.5.sp, color = HintGray)
                        }
                    }
                    if (validationIssues.size > 50) {
                        Text("（仅显示前 50 项）", fontSize = 10.5.sp, color = HintGray)
                    }
                }
            }

            SectionTitle("杆塔长度计算")
            TowerLengthCalculator(sectionResult, onCalcSection)

            SectionTitle("标注导出")
            SettingRow("导出全部绘制结果为 GeoJSON", "导出", onExportOverlays)

            SectionTitle("帮助")
            SettingRow("帮助文档", "打开", onOpenHelp)

            Spacer(Modifier.height(28.dp))
        }
    }
        }
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t, fontSize = 12.sp, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingRow(title: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 13.sp, color = Color(0xFF333333), modifier = Modifier.weight(1f))
        Text(action, fontSize = 12.5.sp, color = Color(0xFF8D6E63))
    }
    Divider(color = Color(0x0F000000), thickness = 0.7.dp)
}

@Composable
private fun TowerLengthCalculator(
    result: SectionLengthResult?,
    onCalc: (String, String?, String?) -> Unit
) {
    var line by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = line, onValueChange = { line = it },
            label = { Text("线路名称", fontSize = 12.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = from, onValueChange = { from = it },
                label = { Text("起始杆塔", fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            )
            OutlinedTextField(
                value = to, onValueChange = { to = it },
                label = { Text("终止杆塔", fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            )
        }
        Button(
            onClick = {
                onCalc(line, from.ifBlank { null }, to.ifBlank { null })
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PowerRed),
            shape = RoundedCornerShape(10.dp)
        ) { Text("计算档距与总长度", fontSize = 13.sp) }

        if (result != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    result.message, fontSize = 12.sp, color = if (result.success) Color(0xFF2E7D32) else PowerRed,
                    fontWeight = FontWeight.Medium
                )
                if (result.spans.isNotEmpty()) {
                    LazyColumn(Modifier.height(150.dp)) {
                        items(result.spans) { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text("${s.from} → ${s.to}", fontSize = 11.sp, color = Color(0xFF333333), modifier = Modifier.weight(1f))
                                Text(
                                    com.kunming.powerline.util.GeoUtils.formatDistance(s.distance),
                                    fontSize = 11.sp, color = Color(0xFF555555)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
