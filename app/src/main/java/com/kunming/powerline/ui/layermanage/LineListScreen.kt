package com.kunming.powerline.ui.layermanage

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.GeoUtils
import com.kunming.powerline.util.VoltagePalette

/**
 * 架空线路管理：线路列表 + 电压筛选 + 创建/导入入口
 */
@Composable
fun LineListScreen(
    vm: MapViewModel,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    onFocus: (Double, Double, Double) -> Unit,
    onOpenImport: () -> Unit
) {
    val lines = vm.lines.value
    val towers = vm.towers.value
    val filter = vm.voltageFilter.value

    var keyword by remember { mutableStateOf("") }
    val shown = lines
        .filter { filter.isEmpty() || filter.contains(it.voltageLevel) }
        .filter { keyword.isBlank() || it.name.contains(keyword, true) || (it.startStation ?: "").contains(keyword, true) }

    ManageScaffold(
        title = "架空线路图层",
        subtitle = "共 ${lines.size} 条线路 / ${towers.size} 基杆塔",
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenImport) {
                Icon(Icons.Default.Add, "创建或导入", tint = PowerRed)
            }
        }
    ) {
        // 电压筛选
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VoltageChip("全部", filter.isEmpty()) { vm.setVoltageFilter(emptySet()) }
            VoltagePalette.levels.forEach { lv ->
                VoltageChip(
                    "${lv}kV",
                    filter.contains(lv),
                    color = Color(android.graphics.Color.parseColor(VoltagePalette.hexColorOf(lv)))
                ) { vm.setVoltageFilter(if (filter.contains(lv)) filter - lv else filter + lv) }
            }
        }

        androidx.compose.material3.OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = { Text("搜索线路名称 / 起始站", fontSize = 12.sp) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PowerRed, unfocusedBorderColor = Color(0x22000000)
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        )

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无线路数据", fontSize = 13.sp, color = Color(0xFF8A8078))
                    Spacer(Modifier.height(6.dp))
                    Text("点击右上角 + 创建或导入线路", fontSize = 11.5.sp, color = Color(0xFFA99E94))
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { line ->
                    val cnt = towers.count { it.lineId == line.id }
                    val color = Color(android.graphics.Color.parseColor(VoltagePalette.hexColorOf(line.voltageLevel)))
                    ManageRow(
                        title = line.name,
                        subtitle = "${VoltagePalette.labelOf(line.voltageLevel)} · ${cnt} 基杆塔" +
                            if (line.isEmptyLine) " · 空线路" else "",
                        leadingColor = color,
                        trailing = "定位 ›",
                        onClick = {
                            val t0 = towers.filter { it.lineId == line.id }.minByOrNull { it.seq }
                            if (t0 != null) {
                                onFocus(t0.lon, t0.lat, 14.0)
                                onToast("已定位到 ${line.name} 首基杆塔")
                            } else {
                                onToast("该线路暂无杆塔坐标")
                            }
                        },
                        onDelete = {
                            vm.deleteLine(line.id)
                            onToast("已删除线路「${line.name}」")
                        }
                    )
                }
            }
        }

        PrimaryAction("创建或导入线路", onClick = onOpenImport)
    }
}

@Composable
private fun VoltageChip(text: String, selected: Boolean, color: Color = PowerRed, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) color.copy(alpha = 0.14f) else Color(0x0F000000), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.5.sp, color = if (selected) color else Color(0xFF6D6259))
    }
}
