package com.kunming.powerline.ui.layermanage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kunming.powerline.data.local.entity.DangerLayerEntity
import com.kunming.powerline.data.local.entity.DangerPointEntity
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.layer.RedSwitch
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PanelLine
import com.kunming.powerline.ui.theme.PanelText
import com.kunming.powerline.ui.theme.PanelTextSub
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.DangerFilter
import com.kunming.powerline.util.DangerText
import java.util.Locale

/**
 * 危险点图层（1.6.4 重写）
 *
 * 现场「实时工况净空危险点报告」是一份份 CSV 汇总表，过去只能手工一条条录点，
 * 现在的流程是：
 * 1. **导入报告**：一个文件 = 一个危险点图层，可整层显隐；
 * 2. **筛选**：按净空距离范围（如 ≤7m 的紧急点）、按植被 / 地物类型；
 * 3. **查看**：列表默认显示「地物类型 + 净空距离」，地图同口径标注。
 */
@Composable
fun DangerManageScreen(
    vm: MapViewModel,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    onFocus: (Double, Double, Double) -> Unit
) {
    val points by vm.dangers.collectAsStateWithLifecycle()
    val layers by vm.dangerLayers.collectAsStateWithLifecycle()
    val filter by vm.dangerFilter.collectAsStateWithLifecycle()

    var showFilter by remember { mutableStateOf(false) }
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
    var confirmLayer by remember { mutableStateOf<DangerLayerEntity?>(null) }

    // 外部重置筛选时同步输入框
    LaunchedEffect(filter.minClearance, filter.maxClearance) {
        minText = filter.minClearance?.let { num(it) } ?: ""
        maxText = filter.maxClearance?.let { num(it) } ?: ""
    }

    val layerVisible = remember(layers) { layers.associate { it.id to it.visible } }
    val visiblePoints = remember(points, layerVisible) {
        points.filter { d -> d.layerId?.let { layerVisible[it] } ?: true }
    }
    val shown = remember(visiblePoints, filter) {
        filter.sorted(filter.apply(visiblePoints))
    }
    val categories = remember(points) { DangerFilter.categoriesOf(points) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.importDangerCsv(uris, onToast)
    }

    val applyRange: () -> Unit = {
        val min = DangerFilter.parseBound(minText)
        val max = DangerFilter.parseBound(maxText)
        if (minText.isNotBlank() && min == null || maxText.isNotBlank() && max == null) {
            onToast("净空距离请填数字，例如 0 和 7")
        } else if (min != null && max != null && min > max) {
            onToast("下限不能大于上限")
        } else {
            vm.setDangerClearanceRange(min, max)
        }
    }

    ManageScaffold(
        title = "危险点图层",
        subtitle = "${layers.size} 个图层 / ${points.size} 个危险点" +
            if (filter.isActive) " · 筛选后显示 ${shown.size} 个" else "",
        onBack = onBack,
        actions = {
            Text(
                if (showFilter) "收起筛选" else "筛选",
                fontSize = 12.5.sp,
                color = PowerRed,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { showFilter = !showFilter }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    ) {
        // ---------------- 导入 ----------------
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .background(Color(0x14B71C1C), RoundedCornerShape(10.dp))
                .clickable {
                    csvLauncher.launch(
                        arrayOf(
                            "text/csv", "text/comma-separated-values", "text/plain",
                            "application/vnd.ms-excel", "application/octet-stream", "*/*"
                        )
                    )
                }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "+ 导入净空危险点报告（CSV，可多选）",
                fontSize = 12.5.sp, color = PowerRed, fontWeight = FontWeight.Medium
            )
        }
        Text(
            "每个文件自动成为一个图层，可单独显示 / 隐藏；重复导入同名文件会覆盖旧点位。",
            fontSize = 10.sp, color = PanelTextSub, lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // ---------------- 筛选面板 ----------------
        if (showFilter) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text("净空距离筛选（m）", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PowerRed)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        ManageField("最小（含）", minText, numeric = true) { minText = it }
                    }
                    Text("~", fontSize = 13.sp, color = PanelTextSub)
                    Box(Modifier.weight(1f)) {
                        ManageField("最大（含）", maxText, numeric = true) { maxText = it }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuickRange("≤7m") { minText = ""; maxText = "7" }
                    QuickRange("≤10m") { minText = ""; maxText = "10" }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "应用", fontSize = 12.5.sp, color = PowerRed, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { applyRange() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Divider(color = PanelLine, thickness = 0.7.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "植被 / 地物类型（可多选，不选=全部）",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PowerRed
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories, key = { it.first }) { (cat, count) ->
                            CategoryChip(
                                text = "$cat $count",
                                selected = cat in filter.categories
                            ) { vm.toggleDangerCategory(cat) }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "清除全部筛选", fontSize = 11.5.sp, color = Color(0xFF8A8078),
                        modifier = Modifier.clickable {
                            minText = ""; maxText = ""; vm.resetDangerFilter()
                        }.padding(vertical = 6.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "显示 ${shown.size} / ${visiblePoints.size}",
                        fontSize = 11.sp, color = PanelTextSub
                    )
                }
            }
        }

        // ---------------- 图层列表 ----------------
        if (layers.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "危险点图层（${layers.size}）",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PanelText
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "关闭后该报告的点不显示",
                    fontSize = 10.sp, color = PanelTextSub
                )
            }
            LazyColumn(Modifier.heightIn(max = 190.dp)) {
                items(layers, key = { it.id }) { layer ->
                    DangerLayerRow(
                        layer = layer,
                        count = points.count { it.layerId == layer.id },
                        lineName = layer.lineName?.takeIf { it.isNotBlank() },
                        onToggle = { vm.setDangerLayerVisible(layer.id, it) },
                        onDelete = { confirmLayer = layer }
                    )
                }
            }
        }

        Divider(color = PanelLine, thickness = 0.7.dp)

        // ---------------- 点位列表 ----------------
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        points.isEmpty() && layers.isEmpty() -> "暂无危险点\n点击上方按钮导入净空危险点报告 CSV"
                        points.isNotEmpty() && shown.isEmpty() -> "当前筛选条件下没有危险点\n试试放宽净空范围或清除类型筛选"
                        else -> "该图层暂无点位"
                    },
                    fontSize = 12.5.sp, color = Color(0xFF8A8078), lineHeight = 19.sp
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { d ->
                    DangerRow(d, onFocus) { vm.deleteDanger(d.id); onToast("已删除该危险点") }
                }
            }
        }
    }

    // 删除图层确认
    confirmLayer?.let { layer ->
        LightPanelTheme {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmLayer = null },
                title = { Text("删除危险点图层", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "确定删除「${layer.name}」及其中 ${points.count { it.layerId == layer.id }} 个危险点？删除后不可恢复。",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Text(
                        "删除", fontSize = 14.sp, color = PowerRed, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            vm.deleteDangerLayer(layer.id)
                            confirmLayer = null
                        }.padding(10.dp)
                    )
                },
                dismissButton = {
                    Text(
                        "取消", fontSize = 14.sp, color = Color(0xFF8A8078),
                        modifier = Modifier.clickable { confirmLayer = null }.padding(10.dp)
                    )
                }
            )
        }
    }
}

// ----------------------------------------------------------------------------
// 组件
// ----------------------------------------------------------------------------

@Composable
private fun DangerLayerRow(
    layer: DangerLayerEntity,
    count: Int,
    lineName: String?,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp)
                .background(if (layer.visible) PowerRed else Color(0xFFC9BDB2), RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                layer.name, fontSize = 12.5.sp, color = PanelText,
                fontWeight = FontWeight.Medium, maxLines = 1
            )
            Text(
                buildString {
                    append("$count 个点")
                    lineName?.let { append(" · $it") }
                    if (!layer.visible) append(" · 已隐藏")
                },
                fontSize = 10.5.sp, color = PanelTextSub, maxLines = 1
            )
        }
        RedSwitch(checked = layer.visible, onCheckedChange = onToggle)
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete, "删除图层",
                tint = Color(0xFFB0A197), modifier = Modifier.size(16.dp)
            )
        }
    }
    Divider(color = PanelLine, thickness = 0.5.dp, modifier = Modifier.padding(start = 34.dp))
}

@Composable
private fun DangerRow(
    d: DangerPointEntity,
    onFocus: (Double, Double, Double) -> Unit,
    onDelete: () -> Unit
) {
    val c = Color(android.graphics.Color.parseColor(d.levelColor))
    ManageRow(
        title = DangerText.title(d),
        subtitle = DangerText.subtitle(d),
        leadingColor = c,
        trailing = "定位 ›",
        onClick = { onFocus(d.lon, d.lat, 17.0) },
        onDelete = onDelete
    )
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(
                if (selected) PowerRed.copy(alpha = 0.14f) else Color(0xFFF1EDE4),
                RoundedCornerShape(9.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text, fontSize = 11.sp,
            color = if (selected) PowerRed else Color(0xFF7A6F66),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun QuickRange(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 6.dp)
            .background(Color(0xFFF1EDE4), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 10.5.sp, color = Color(0xFF6B6157))
    }
}

private fun num(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(Locale.US, "%.2f", v)
