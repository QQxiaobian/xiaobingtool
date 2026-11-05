package com.kunming.powerline.ui.importui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.PowerRed

/**
 * 杆塔显示优化 —— 同塔多回线路的显示规则说明与一键处理
 *
 * 规范（第 10 节）：同一位置（2~5m 内）存在多基不同回路的杆塔时，
 * 符号按回路顺号做微小视觉偏移（每回 3m，最大 25m），
 * 并在线路右侧标注 Ⅰ / Ⅱ / Ⅲ 回路号，避免符号完全重叠。
 */
@Composable
fun TowerDisplayOptimizerScreen(
    vm: MapViewModel,
    onBack: () -> Unit,
    onToast: (String) -> Unit
) {
    var detectResult by remember { mutableStateOf<String?>(null) }

    ImportScaffold(
        title = "杆塔显示优化",
        subtitle = "同塔多回线路杆塔符号重叠的处理方式",
        onBack = onBack
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SectionCard("规则说明") {
                RuleRow("① 同址判定", "两基杆塔间距 ≤ 5m 视为同址（同塔架设多回线路）")
                RuleRow("② 视觉偏移", "按回路顺号依次向右下方偏移，每回 3m，最大 25m")
                RuleRow("③ 回路标注", "杆塔号右侧追加 Ⅰ / Ⅱ / Ⅲ … 表示第几回")
                RuleRow("④ 真实坐标", "偏移只作用于绘制，台账坐标与测距仍用真实经纬度")
                RuleRow("⑤ 缩放联动", "缩放 ≥ 15 级时自动展开偏移，低缩放自动合并显示")
            }

            Spacer(Modifier.height(12.dp))

            SectionCard("当前数据") {
                StatLine("线路总数", "${vm.stat.value.first}")
                StatLine("杆塔总数", "${vm.towers.value.size}")
                StatLine("已应用偏移", "${vm.towers.value.count { it.visualOffsetLon != 0.0 || it.visualOffsetLat != 0.0 }}")
            }

            Spacer(Modifier.height(12.dp))

            SectionCard("操作") {
                ActionRow("检测同址杆塔", "统计当前数据中位置重叠的杆塔数量") {
                    val g = vm.towers.value.groupBy {
                        "%.5f,%.5f".format(it.lon, it.lat)
                    }
                    val dup = g.values.count { it.size > 1 }
                    detectResult = if (dup == 0) "未发现同址杆塔" else "发现 $dup 处同址杆塔，涉及 ${g.values.filter { it.size > 1 }.sumOf { it.size }} 基"
                    detectResult?.let(onToast)
                }
                ActionRow("恢复默认偏移", "清除所有手动偏移，回到自动计算") {
                    vm.resetVisualOffsets()
                }
            }

            detectResult?.let {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x1FB71C1C), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(it, fontSize = 12.5.sp, color = Color(0xFF7A1410))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "提示：自动偏移在「导入线路」及每次图层重绘时按上述规则实时计算，无需手动执行。",
                fontSize = 11.5.sp, color = Color(0xFF8A8078), lineHeight = 18.sp
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun RuleRow(title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(title, fontSize = 12.sp, color = Color(0xFF5D4037), modifier = Modifier.width(76.dp))
        Text(body, fontSize = 12.sp, color = Color(0xFF444444), lineHeight = 18.sp)
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color(0xFF5D4037))
        Text(value, fontSize = 12.sp, color = Color(0xFF2B2B2B), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 12.5.sp, color = Color(0xFF2B2B2B), fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("执行 ›", fontSize = 11.5.sp, color = PowerRed)
        }
        Text(subtitle, fontSize = 11.sp, color = Color(0xFF8A8078))
    }
}
