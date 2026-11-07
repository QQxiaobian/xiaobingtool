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
    // 当前正在编辑的线路（非空时显示编辑页）
    var editingLineId by remember { mutableStateOf<String?>(null) }
    // filter 为空 = 全部；否则只显示选中等级（注意：还要兼容文件里识别出的非标准等级，
    // 这类线路在"全部"下必须可见，不能被静默过滤掉）
    val shown = lines
        .filter { filter.isEmpty() || filter.contains(it.voltageLevel) }
        .filter { keyword.isBlank() || it.name.contains(keyword, true) || (it.startStation ?: "").contains(keyword, true) }

    // ---------- 线路编辑页 ----------
    val editId = editingLineId
    if (editId != null) {
        val editLine = lines.firstOrNull { it.id == editId }
        if (editLine != null) {
            val lineTowers = towers.filter { it.lineId == editId }
            LineEditScreen(
                line = editLine,
                towers = lineTowers,
                vm = vm,
                onBack = { editingLineId = null },
                onToast = onToast,
                onFocusTower = onFocus
            )
            return
        } else {
            editingLineId = null
        }
    }

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
                    Text(
                        if (lines.isEmpty()) "暂无线路数据" else "当前筛选条件下没有线路",
                        fontSize = 13.sp, color = Color(0xFF8A8078)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (lines.isEmpty()) "点击右上角 + 创建或导入线路"
                        else "共 ${lines.size} 条线路，请点击「全部」或清空搜索词",
                        fontSize = 11.5.sp, color = Color(0xFFA99E94)
                    )
                    if (lines.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "查看全部",
                            fontSize = 12.sp, color = PowerRed, fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { vm.setVoltageFilter(emptySet()); keyword = "" }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(shown, key = { it.id }) { line ->
                    val cnt = towers.count { it.lineId == line.id }
                    val color = Color(android.graphics.Color.parseColor(VoltagePalette.hexColorOf(line.voltageLevel)))
                    // 注意：外层不要用 Row + weight(1f)，否则 ManageRow 内部的 fillMaxWidth
                    // 会被水平权重压成 0 宽，整行"消失"（v1.0.6 列表空白的根因）
                    Box(Modifier.fillMaxWidth()) {
                        ManageRow(
                            modifier = Modifier.padding(end = 56.dp),
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
                        Text(
                            "编辑",
                            fontSize = 12.sp,
                            color = PowerRed,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .clickable { editingLineId = line.id }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
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
            .background(if (selected) color.copy(alpha = 0.14f) else Color(0xFFEFE7DC), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.5.sp, color = if (selected) color else Color(0xFF7A6F66))
    }
}
