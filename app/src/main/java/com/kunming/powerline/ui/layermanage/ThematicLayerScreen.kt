package com.kunming.powerline.ui.layermanage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunming.powerline.data.local.entity.ThematicFeatureEntity
import com.kunming.powerline.data.local.entity.ThematicLayerEntity
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PanelBg
import com.kunming.powerline.ui.theme.PanelCard
import com.kunming.powerline.ui.theme.PanelLine
import com.kunming.powerline.ui.theme.PanelText
import com.kunming.powerline.ui.theme.PanelTextSub
import com.kunming.powerline.ui.theme.PowerRed

/**
 * 专题图层管理：查看 / 显示隐藏 / 名称开关 / 透明度 / 重命名 / 定位 / 删除
 *
 * 这里管理的是"非线路"的地理要素 —— 巡视点位、区域范围、通道、隐患点等，
 * 数据来源为 KML / KMZ / GeoJSON / GPX 导入。
 */
@Composable
fun ThematicLayerScreen(
    vm: MapViewModel,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    onFocus: (Double, Double, Double) -> Unit,
    onImport: () -> Unit,
    onDeleteAll: () -> Unit = {}
) {
    val layers by vm.thematicLayers.collectAsStateWithLifecycle()
    val features by vm.thematicFeatures.collectAsStateWithLifecycle()

    var expandedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ThematicLayerEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editRemark by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ThematicLayerEntity?>(null) }
    var showBatchOpacity by remember { mutableStateOf(false) }
    // 转线路（整层批量）
    var converting by remember { mutableStateOf<ThematicLayerEntity?>(null) }
    // 单个要素操作
    var actionFeature by remember { mutableStateOf<ThematicFeatureEntity?>(null) }
    // 修改所属图层
    var movingFeature by remember { mutableStateOf<ThematicFeatureEntity?>(null) }

    val polygonCount = layers.count { it.hasPolygon }
    val allLabelsOn = layers.isNotEmpty() && layers.all { it.labelVisible }
    val anyVisibleOn = layers.any { it.visible }

    ManageScaffold(
        title = "专题图层",
        subtitle = "共 ${layers.size} 个图层 / ${features.size} 个要素",
        onBack = onBack,
        actions = {
            Text(
                "导入",
                fontSize = 13.sp,
                color = PowerRed,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onImport() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    ) {
        if (layers.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))
                Text("暂无专题图层", fontSize = 15.sp, color = PanelText, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "可导入巡视点位、区域范围、通道等 KML / KMZ / GeoJSON / GPX 文件，\n一个文件（或其中一个 Folder）会生成一个图层。",
                    fontSize = 12.sp,
                    color = PanelTextSub,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PowerRed)
                        .clickable { onImport() }
                        .padding(horizontal = 22.dp, vertical = 11.dp)
                ) {
                    Text("从文件导入图层", color = Color.White, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                // ---- 全局：批量名称开关 + 批量透明度 ----
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PanelCard)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("显示全部区域名称 / 备注", fontSize = 13.sp, color = PanelText)
                                Text(
                                    "关闭后地图上只保留区域轮廓与点位，密集数据更流畅",
                                    fontSize = 10.5.sp,
                                    color = PanelTextSub,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Switch(
                                checked = allLabelsOn,
                                onCheckedChange = { v ->
                                    vm.setAllThematicLabelVisible(v)
                                    onToast(if (v) "已显示全部图层名称" else "已隐藏全部图层名称")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PowerRed
                                )
                            )
                        }
                        Divider(color = PanelLine, thickness = 0.6.dp, modifier = Modifier.padding(vertical = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("批量修改区域透明度", fontSize = 13.sp, color = PanelText)
                                Text(
                                    if (polygonCount == 0) "当前没有面图层，无可调项"
                                    else "共 $polygonCount 个含面图层，一次性统一设置",
                                    fontSize = 10.5.sp,
                                    color = PanelTextSub,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Text(
                                "调整",
                                fontSize = 12.5.sp,
                                color = if (polygonCount == 0) PanelTextSub else PowerRed,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable(enabled = polygonCount > 0) { showBatchOpacity = true }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                items(layers, key = { it.id }) { layer ->
                    val layerFeatures = features.filter { it.layerId == layer.id }
                    LayerCard(
                        layer = layer,
                        features = layerFeatures,
                        expanded = expandedId == layer.id,
                        onToggleExpand = {
                            expandedId = if (expandedId == layer.id) null else layer.id
                        },
                        onToggleVisible = {
                            vm.setThematicVisible(layer.id, !layer.visible)
                            onToast(if (layer.visible) "已隐藏「${layer.name}」" else "已显示「${layer.name}」")
                        },
                        onToggleLabel = {
                            vm.setThematicLabelVisible(layer.id, !layer.labelVisible)
                            onToast(
                                if (layer.labelVisible) "已隐藏「${layer.name}」的名称"
                                else "已显示「${layer.name}」的名称"
                            )
                        },
                        onOpacityChange = { v -> vm.setThematicFillOpacity(layer.id, v) },
                        onLineWidthChange = { v -> vm.setThematicLineWidth(layer.id, v) },
                        onFocus = {
                            val c = centerOf(layerFeatures)
                            if (c != null) {
                                onFocus(c.first, c.second, 14.0)
                                onToast("已定位到「${layer.name}」")
                            } else onToast("该图层没有可定位的坐标")
                        },
                        onEdit = {
                            editing = layer
                            editName = layer.name
                            editRemark = layer.remark ?: ""
                        },
                        onDelete = { deleting = layer },
                        onConvert = { converting = layer },
                        onFeatureClick = { f ->
                            f.lon?.let { lon -> f.lat?.let { lat -> onFocus(lon, lat, 17.0) } }
                        },
                        onFeatureAction = { f -> actionFeature = f }
                    )
                    Divider(color = PanelLine, thickness = 0.7.dp)
                }
                item {
                    Text(
                        if (anyVisibleOn) "一键隐藏全部专题图层" else "一键显示全部专题图层",
                        fontSize = 12.sp,
                        color = PanelTextSub,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                layers.forEach { vm.setThematicVisible(it.id, !anyVisibleOn) }
                                onToast(if (anyVisibleOn) "已隐藏全部专题图层" else "已显示全部专题图层")
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "清空全部专题图层",
                        fontSize = 12.sp,
                        color = PowerRed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeleteAll() }
                            .padding(18.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // 批量透明度
    if (showBatchOpacity) {
        var preview by remember { mutableFloatStateOf(0.22f) }
        LightPanelTheme {
            AlertDialog(
                onDismissRequest = { showBatchOpacity = false },
                containerColor = PanelBg,
                title = { Text("批量区域透明度", fontSize = 15.sp, color = PanelText, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "同时对 $polygonCount 个面图层生效：${(preview * 100).toInt()}%",
                            fontSize = 12.5.sp,
                            color = PanelText
                        )
                        Slider(
                            value = preview,
                            onValueChange = { preview = it },
                            valueRange = 0f..0.9f,
                            colors = SliderDefaults.colors(thumbColor = PowerRed, activeTrackColor = PowerRed)
                        )
                        Text(
                            "数值越小越透明（不遮底图），越大区域范围越醒目。",
                            fontSize = 11.sp,
                            color = PanelTextSub,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Text(
                        "应用到全部",
                        fontSize = 14.sp,
                        color = PowerRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                vm.setAllThematicFillOpacity(preview)
                                showBatchOpacity = false
                            }
                            .padding(10.dp)
                    )
                },
                dismissButton = {
                    Text(
                        "取消",
                        fontSize = 14.sp,
                        color = PanelTextSub,
                        modifier = Modifier.clickable { showBatchOpacity = false }.padding(10.dp)
                    )
                }
            )
        }
    }

    // 编辑名称 / 备注
    editing?.let { layer ->
        LightPanelTheme {
            AlertDialog(
                onDismissRequest = { editing = null },
                containerColor = PanelBg,
                title = { Text("编辑图层信息", fontSize = 15.sp, color = PanelText, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            singleLine = true,
                            label = { Text("图层名称", fontSize = 12.sp) },
                            colors = panelFieldColors()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editRemark,
                            onValueChange = { editRemark = it },
                            singleLine = true,
                            label = { Text("备注（显示在名称下方）", fontSize = 12.sp) },
                            colors = panelFieldColors()
                        )
                    }
                },
                confirmButton = {
                    Text(
                        "保存",
                        fontSize = 14.sp,
                        color = PowerRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                vm.updateThematicLayer(layer.id, editName, editRemark)
                                editing = null
                            }
                            .padding(10.dp)
                    )
                },
                dismissButton = {
                    Text(
                        "取消",
                        fontSize = 14.sp,
                        color = PanelTextSub,
                        modifier = Modifier.clickable { editing = null }.padding(10.dp)
                    )
                }
            )
        }
    }

    // 删除确认
    deleting?.let { layer ->
        LightPanelTheme {
            AlertDialog(
                onDismissRequest = { deleting = null },
                containerColor = PanelBg,
                title = { Text("删除图层", fontSize = 15.sp, color = PanelText, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "确定删除「${layer.name}」及其 ${layer.featureCount} 个要素？删除后不可恢复。",
                        fontSize = 13.sp,
                        color = PanelTextSub
                    )
                },
                confirmButton = {
                    Text(
                        "删除",
                        fontSize = 14.sp,
                        color = PowerRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                vm.deleteThematicLayer(layer.id)
                                onToast("已删除「${layer.name}」")
                                deleting = null
                            }
                            .padding(10.dp)
                    )
                },
                dismissButton = {
                    Text(
                        "取消",
                        fontSize = 14.sp,
                        color = PanelTextSub,
                        modifier = Modifier.clickable { deleting = null }.padding(10.dp)
                    )
                }
            )
        }
    }

    // 整层转线路
    converting?.let { layer ->
        var voltage by remember { mutableStateOf(110) }
        val lineCount = features.count { it.layerId == layer.id && it.kind == "line" }
        LightPanelTheme {
            AlertDialog(
                onDismissRequest = { converting = null },
                containerColor = PanelBg,
                title = { Text("批量转为线路", fontSize = 15.sp, color = PanelText, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "将「${layer.name}」中的 $lineCount 条线要素各转成一条正式线路，" +
                                "线要素的每个顶点作为一基虚拟杆塔（编号 1..N）。",
                            fontSize = 12.5.sp, color = PanelText, lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("电压等级", fontSize = 12.sp, color = PanelTextSub)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(500, 220, 110, 35).forEach { v ->
                                val on = voltage == v
                                Text(
                                    "${v}kV",
                                    fontSize = 12.sp,
                                    color = if (on) Color.White else PowerRed,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (on) PowerRed else Color(0x14B71C1C))
                                        .clickable { voltage = v }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "转换不会删除原专题图层，可在「线路列表」中继续编辑。",
                            fontSize = 11.sp, color = PanelTextSub
                        )
                    }
                },
                confirmButton = {
                    Text(
                        "开始转换",
                        fontSize = 14.sp,
                        color = PowerRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                vm.convertThematicLinesToLines(layer.id, voltage)
                                converting = null
                            }
                            .padding(10.dp)
                    )
                },
                dismissButton = {
                    Text(
                        "取消",
                        fontSize = 14.sp,
                        color = PanelTextSub,
                        modifier = Modifier.clickable { converting = null }.padding(10.dp)
                    )
                }
            )
        }
    }

    // 单个要素操作
    actionFeature?.let { f ->
        LightPanelTheme {
            AlertDialog(
                onDismissRequest = { actionFeature = null },
                containerColor = PanelBg,
                title = {
                    Text(
                        f.name ?: "${kindLabel(f.kind)} ${f.seq + 1}",
                        fontSize = 15.sp, color = PanelText, fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            "${kindLabel(f.kind)} · " +
                                (f.lon?.let { lon -> f.lat?.let { lat -> "%.5f, %.5f".format(lon, lat) } } ?: "无坐标"),
                            fontSize = 11.5.sp, color = PanelTextSub
                        )
                        Spacer(Modifier.height(12.dp))
                        FeatureActionRow("定位到该要素") {
                            f.lon?.let { lon -> f.lat?.let { lat -> onFocus(lon, lat, 17.0) } }
                            actionFeature = null
                        }
                        if (f.kind == "line") {
                            FeatureActionRow("转为线路") {
                                vm.convertThematicFeatureToLine(f.id, 110)
                                actionFeature = null
                            }
                        }
                        FeatureActionRow("修改所属图层") {
                            movingFeature = f
                            actionFeature = null
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    Text(
                        "关闭",
                        fontSize = 14.sp,
                        color = PanelTextSub,
                        modifier = Modifier.clickable { actionFeature = null }.padding(10.dp)
                    )
                }
            )
        }
    }

    // 选择目标图层
    movingFeature?.let { f ->
        val targets = layers.filter { it.id != f.layerId }
        LightPanelTheme {
            AlertDialog(
                onDismissRequest = { movingFeature = null },
                containerColor = PanelBg,
                title = { Text("修改所属图层", fontSize = 15.sp, color = PanelText, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        if (targets.isEmpty()) {
                            Text("只有一个图层，无法移动", fontSize = 12.5.sp, color = PanelTextSub)
                        } else {
                            Text(
                                "把该要素移动到：",
                                fontSize = 12.5.sp, color = PanelText, modifier = Modifier.padding(bottom = 6.dp)
                            )
                            androidx.compose.foundation.lazy.LazyColumn(
                                Modifier.fillMaxWidth().height(220.dp)
                            ) {
                                items(targets, key = { it.id }) { t ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                vm.moveThematicFeature(f.id, t.id, t.name)
                                                movingFeature = null
                                            }
                                            .padding(vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(9.dp).clip(CircleShape).background(Color(t.color))
                                        )
                                        Spacer(Modifier.width(9.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(t.name, fontSize = 13.sp, color = PanelText)
                                            Text(
                                                "${kindLabel(t.geometryType)} · ${t.featureCount} 个要素",
                                                fontSize = 10.5.sp, color = PanelTextSub
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    Text(
                        "取消",
                        fontSize = 14.sp,
                        color = PanelTextSub,
                        modifier = Modifier.clickable { movingFeature = null }.padding(10.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun FeatureActionRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.5.sp,
        color = PanelText,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun LayerCard(
    layer: ThematicLayerEntity,
    features: List<ThematicFeatureEntity>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleLabel: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onLineWidthChange: (Float) -> Unit,
    onFocus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConvert: () -> Unit,
    onFeatureClick: (ThematicFeatureEntity) -> Unit,
    onFeatureAction: (ThematicFeatureEntity) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(layer.color))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    layer.name,
                    fontSize = 14.sp,
                    color = if (layer.visible) PanelText else PanelTextSub,
                    fontWeight = FontWeight.Medium
                )
                if (!layer.remark.isNullOrBlank()) {
                    Text(
                        layer.remark!!,
                        fontSize = 11.sp,
                        color = PanelTextSub,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 2
                    )
                }
                val lineCount = features.count { it.kind == "line" }
                val named = features.count {
                    it.name?.isNotBlank() == true || (layer.hasPolygon && layer.featureCount <= 50)
                }
                Text(
                    "${kindLabel(layer.geometryType)} · ${layer.featureCount} 个要素 · ${layer.sourceFormat.uppercase()}" +
                        (if (layer.visible) "" else " · 已隐藏") +
                        (if (layer.hasPolygon) " · 填充 ${layer.opacityPercent}%" else "") +
                        if (named == 0) " · 无可显示名称" else " · 名称 $named/${features.size}",
                    fontSize = 11.sp,
                    color = PanelTextSub,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                if (layer.visible) "隐藏" else "显示",
                fontSize = 12.sp,
                color = PowerRed,
                modifier = Modifier
                    .clickable { onToggleVisible() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        // ---- 名称开关 + 透明度 / 线宽 ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, end = 14.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (layer.labelVisible) "名称：开" else "名称：关",
                fontSize = 11.5.sp,
                color = if (layer.labelVisible) PowerRed else PanelTextSub,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (layer.labelVisible) Color(0x14B71C1C) else Color(0x0D000000))
                    .clickable { onToggleLabel() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
            Spacer(Modifier.width(10.dp))
            if (layer.hasPolygon) {
                OpacitySlider(
                    value = layer.fillOpacity,
                    label = "填充",
                    onChange = onOpacityChange
                )
            } else {
                OpacitySlider(
                    value = (layer.lineWidth / 8f).coerceIn(0f, 1f),
                    label = "线宽",
                    display = "${"%.1f".format(layer.lineWidth)}px",
                    onChange = { v -> onLineWidthChange((v * 8f).coerceIn(1f, 8f)) }
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, end = 12.dp, bottom = 6.dp, top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniAction("定位") { onFocus() }
            MiniAction("编辑") { onEdit() }
            if (features.any { it.kind == "line" }) MiniAction("转线路") { onConvert() }
            MiniAction("删除", danger = true) { onDelete() }
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) "收起 ▲" else "查看要素 ▼",
                fontSize = 11.5.sp,
                color = PanelTextSub,
                modifier = Modifier.clickable { onToggleExpand() }.padding(vertical = 4.dp)
            )
        }

        if (expanded) {
            Column(Modifier.fillMaxWidth().background(Color(0x14000000)).padding(vertical = 4.dp)) {
                if (features.isEmpty()) {
                    Text(
                        "该图层没有要素",
                        fontSize = 11.5.sp,
                        color = PanelTextSub,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                } else {
                    features.take(300).forEach { f ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onFeatureClick(f) }
                                .padding(horizontal = 20.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                f.name ?: "${kindLabel(f.kind)} ${f.seq + 1}",
                                fontSize = 12.sp,
                                color = PanelText,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                f.lon?.let { lon -> f.lat?.let { lat -> "%.5f, %.5f".format(lon, lat) } } ?: "",
                                fontSize = 10.5.sp,
                                color = PanelTextSub,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                "⋯",
                                fontSize = 15.sp,
                                color = PowerRed,
                                modifier = Modifier
                                    .clickable { onFeatureAction(f) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (features.size > 300) {
                        Text(
                            "… 仅显示前 300 个，共 ${features.size} 个",
                            fontSize = 10.5.sp,
                            color = PanelTextSub,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 紧凑滑块：拖动过程只改本地状态，松手才落库，避免连续写库触发整图重绘
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.OpacitySlider(
    value: Float,
    label: String,
    display: String? = null,
    onChange: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Text("$label ${display ?: "${(local * 100).toInt()}%"}",
            fontSize = 11.sp, color = PanelTextSub, modifier = Modifier.width(if (display == null) 62.dp else 62.dp))
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = PowerRed,
                activeTrackColor = PowerRed,
                inactiveTrackColor = Color(0x33000000)
            ),
            modifier = Modifier.weight(1f).height(26.dp)
        )
    }
}

@Composable
private fun MiniAction(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 11.5.sp,
        color = if (danger) PowerRed else PanelTextSub,
        modifier = Modifier.clickable { onClick() }.padding(vertical = 4.dp)
    )
}

@Composable
private fun panelFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PanelText,
    unfocusedTextColor = PanelText,
    focusedContainerColor = PanelCard,
    unfocusedContainerColor = PanelCard,
    cursorColor = PowerRed,
    focusedBorderColor = PowerRed,
    unfocusedBorderColor = PanelLine,
    focusedLabelColor = PowerRed,
    unfocusedLabelColor = PanelTextSub
)

private fun kindLabel(kind: String): String = when (kind) {
    "point" -> "点图层"
    "line" -> "线图层"
    "polygon" -> "面图层"
    "mixed" -> "混合图层"
    else -> kind
}

/** 图层中心点：所有要素坐标的均值，用于定位 */
private fun centerOf(features: List<ThematicFeatureEntity>): Pair<Double, Double>? {
    val pts = features.mapNotNull { f -> f.lon?.let { lon -> f.lat?.let { lat -> lon to lat } } }
    if (pts.isEmpty()) return null
    return pts.map { it.first }.average() to pts.map { it.second }.average()
}
