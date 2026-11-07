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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.data.local.entity.TowerEntity
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PanelBg
import com.kunming.powerline.ui.theme.PanelText
import com.kunming.powerline.ui.theme.PanelTextSub
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.VoltagePalette

/**
 * 线路编辑页：编辑线路基本信息 + 杆塔列表管理（改号/改坐标/删除/新增）
 */
@Composable
fun LineEditScreen(
    line: LineEntity,
    towers: List<TowerEntity>,
    vm: MapViewModel,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    onFocusTower: (Double, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf(line.name) }
    var voltage by remember { mutableStateOf(line.voltageLevel) }
    var start by remember { mutableStateOf(line.startStation ?: "") }
    var end by remember { mutableStateOf(line.endStation ?: "") }
    var date by remember { mutableStateOf(line.inServiceDate ?: "") }
    var remark by remember { mutableStateOf(line.remark ?: "") }

    // 正在编辑的杆塔（null = 不在编辑）
    var editingTower by remember { mutableStateOf<TowerEntity?>(null) }
    var addingTower by remember { mutableStateOf(false) }
    var deletingTower by remember { mutableStateOf<TowerEntity?>(null) }

    val sorted = towers.sortedBy { it.seq }

    ManageScaffold(
        title = "编辑线路",
        subtitle = "${line.name} · ${towers.size} 基杆塔",
        onBack = onBack
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("线路信息")
            ManageField("线路名称", name) { name = it }
            Spacer(Modifier.height(8.dp))
            Text("电压等级", fontSize = 12.sp, color = PanelTextSub)
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VoltagePalette.levels.forEach { lv ->
                    val active = voltage == lv
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) PowerRed else Color(0xFFEFE7DC))
                            .clickable { voltage = lv }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${lv}kV",
                            fontSize = 11.5.sp,
                            color = if (active) Color.White else PanelTextSub
                        )
                    }
                }
            }
            ManageField("起始站", start) { start = it }
            ManageField("终止站", end) { end = it }
            ManageField("投运日期", date) { date = it }
            ManageField("备注", remark) { remark = it }
            Spacer(Modifier.height(10.dp))
            PrimaryAction("保存线路信息", enabled = name.isNotBlank()) {
                vm.updateLine(
                    line.copy(
                        name = name.trim(),
                        voltageLevel = voltage,
                        startStation = start.trim().ifBlank { null },
                        endStation = end.trim().ifBlank { null },
                        inServiceDate = date.trim().ifBlank { null },
                        remark = remark.trim().ifBlank { null }
                    )
                )
                onToast("已保存")
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("杆塔列表（${towers.size}）")
                Spacer(Modifier.weight(1f))
                Text(
                    "+ 添加杆塔",
                    fontSize = 12.sp,
                    color = PowerRed,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { addingTower = true }.padding(6.dp)
                )
            }
            if (sorted.isEmpty()) {
                Text("暂无杆塔，点击右上角「+ 添加杆塔」或回到地图长按添加", fontSize = 11.5.sp, color = PanelTextSub.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 10.dp))
            } else {
                sorted.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editingTower = t }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x14B71C1C))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) { Text(t.towerNumber, fontSize = 11.sp, color = Color(0xFFB71C1C), fontWeight = FontWeight.Medium) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "%.6f, %.6f".format(t.lon, t.lat),
                                fontSize = 11.sp, color = PanelTextSub
                            )
                            if (!t.type.isNullOrBlank() || !t.remark.isNullOrBlank()) {
                                Text(
                                    listOfNotNull(t.type, t.remark).joinToString(" · "),
                                    fontSize = 10.5.sp, color = PanelTextSub.copy(alpha = 0.75f)
                                )
                            }
                        }
                        Text("定位", fontSize = 11.sp, color = PowerRed, modifier = Modifier
                            .clickable { onFocusTower(t.lon, t.lat, 16.0) }.padding(horizontal = 6.dp))
                        Text("删除", fontSize = 11.sp, color = Color(0xFFB71C1C), modifier = Modifier
                            .clickable { deletingTower = t }.padding(horizontal = 6.dp))
                    }
                    Divider(color = Color(0x0F000000), thickness = 0.6.dp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------- 杆塔编辑对话框 ----------
    editingTower?.let { t ->
        TowerEditDialog(
            title = "编辑杆塔 ${t.towerNumber}",
            initial = t,
            onDismiss = { editingTower = null },
            onConfirm = { updated ->
                vm.updateTower(updated)
                vm.resequenceTowers(t.lineId)
                editingTower = null
                onToast("杆塔 ${updated.towerNumber} 已保存")
            }
        )
    }

    // ---------- 新增杆塔对话框 ----------
    if (addingTower) {
        TowerEditDialog(
            title = "添加杆塔",
            initial = null,
            onDismiss = { addingTower = false },
            onConfirm = { t ->
                vm.addTower(line.id, t.towerNumber, t.lon, t.lat)
                addingTower = false
                onToast("已添加杆塔 ${t.towerNumber}")
            }
        )
    }

    // ---------- 删除杆塔确认 ----------
    deletingTower?.let { t ->
        LightPanelTheme {
        AlertDialog(
            onDismissRequest = { deletingTower = null },
            containerColor = PanelBg,
            shape = RoundedCornerShape(16.dp),
            title = { Text("删除杆塔 ${t.towerNumber}？", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("删除后线路几何将自动重算，此操作不可恢复。", fontSize = 12.5.sp) },
            confirmButton = {
                Text(
                    "删除", color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            vm.deleteTower(t)
                            deletingTower = null
                            onToast("已删除杆塔 ${t.towerNumber}")
                        }
                        .padding(10.dp)
                )
            },
            dismissButton = {
                Text("取消", color = Color(0xFF5D4037), modifier = Modifier.clickable { deletingTower = null }.padding(10.dp))
            }
        )
        }
    }
}

/**
 * 杆塔编辑/新增对话框
 */
@Composable
private fun TowerEditDialog(
    title: String,
    initial: TowerEntity?,
    onDismiss: () -> Unit,
    onConfirm: (TowerEntity) -> Unit
) {
    var no by remember { mutableStateOf(initial?.towerNumber ?: "") }
    var lon by remember { mutableStateOf(initial?.lon?.toString() ?: "") }
    var lat by remember { mutableStateOf(initial?.lat?.toString() ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "") }
    var remark by remember { mutableStateOf(initial?.remark ?: "") }

    val lonV = lon.toDoubleOrNull()
    val latV = lat.toDoubleOrNull()
    val valid = no.isNotBlank() && lonV != null && latV != null &&
        lonV in 73.0..136.0 && latV in 3.0..54.0

    LightPanelTheme {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelBg,
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ManageField("杆塔号（如 12 / N12 / 12-1）", no) { no = it }
                ManageField("经度 (73~136)", lon, numeric = true) { lon = it }
                ManageField("纬度 (3~54)", lat, numeric = true) { lat = it }
                ManageField("杆塔类型（耐张/直线/转角）", type) { type = it }
                ManageField("备注", remark) { remark = it }
                if (!valid && (lon.isNotBlank() || lat.isNotBlank())) {
                    Text("请检查杆塔号与坐标范围", fontSize = 11.sp, color = Color(0xFFB71C1C))
                }
            }
        },
        confirmButton = {
            Text(
                "保存", color = if (valid) Color(0xFFB71C1C) else Color(0x44000000),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = valid) {
                        onConfirm(
                            (initial ?: TowerEntity(id = "", lineId = "", towerNumber = "", lon = 0.0, lat = 0.0))
                                .copy(
                                    towerNumber = no.trim(),
                                    lon = lonV!!, lat = latV!!,
                                    type = type.trim().ifBlank { null },
                                    remark = remark.trim().ifBlank { null }
                                )
                        )
                    }
                    .padding(10.dp)
            )
        },
        dismissButton = {
            Text("取消", color = Color(0xFF5D4037), modifier = Modifier.clickable { onDismiss() }.padding(10.dp))
        }
    )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed, modifier = Modifier.padding(vertical = 6.dp))
}
