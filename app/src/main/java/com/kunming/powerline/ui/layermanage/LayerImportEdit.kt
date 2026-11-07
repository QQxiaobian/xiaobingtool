package com.kunming.powerline.ui.layermanage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.data.parser.ImportFeature
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PanelBg
import com.kunming.powerline.ui.theme.PanelCard
import com.kunming.powerline.ui.theme.PanelLine
import com.kunming.powerline.ui.theme.PanelText
import com.kunming.powerline.ui.theme.PanelTextSub
import com.kunming.powerline.ui.theme.PowerRed
import kotlinx.coroutines.launch

/**
 * 通用「从地理文件导入到本图层」按钮
 *
 * 各图层管理页复用：解析 KML/KMZ/GeoJSON/GPX/SHP 后，只挑出与目标图层
 * 匹配的几何类型（点图层只取点、电缆图层只取线/面），其余忽略。
 */
/** 把选中的文件解析成要素列表（多个文件合并），返回 (要素, 提示语) */
private suspend fun loadGeoFeatures(
    vm: MapViewModel,
    uris: List<android.net.Uri>,
    acceptKinds: Set<String>
): Pair<List<ImportFeature>, String> {
    val all = mutableListOf<ImportFeature>()
    val errors = mutableListOf<String>()
    uris.forEach { u ->
        vm.geoParser.parseThematic(u).onSuccess { r ->
            r.layers.forEach { l -> all.addAll(l.features) }
            errors.addAll(r.errors.map { it.message })
        }.onFailure { errors.add("解析失败：${it.message}") }
    }
    val picked = all.filter { it.kind in acceptKinds }
    val msg = when {
        picked.isNotEmpty() -> ""
        all.isEmpty() -> errors.firstOrNull() ?: "文件中没有可用要素"
        else -> "文件中没有符合该图层的要素类型"
    }
    return picked to msg
}

/**
 * 头部小字导入按钮（管理页标题栏右侧）
 */
@Composable
fun GeoLayerImport(
    vm: MapViewModel,
    acceptKinds: Set<String>,
    buttonText: String = "导入",
    onFeatures: (List<ImportFeature>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val (picked, msg) = loadGeoFeatures(vm, uris, acceptKinds)
            busy = false
            if (picked.isEmpty()) vm.postMessage(msg) else onFeatures(picked)
        }
    }

    Text(
        if (busy) "导入中…" else buttonText,
        fontSize = 13.sp,
        color = if (busy) PanelTextSub else PowerRed,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(enabled = !busy) { picker.launch(arrayOf("*/*")) }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

/**
 * 全宽导入条：放在列表最上方，比标题栏的小字更容易发现
 */
@Composable
fun GeoImportBar(
    vm: MapViewModel,
    acceptKinds: Set<String>,
    hint: String = "支持 KML / KMZ / GeoJSON / GPX / SHP",
    onFeatures: (List<ImportFeature>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val (picked, msg) = loadGeoFeatures(vm, uris, acceptKinds)
            busy = false
            if (picked.isEmpty()) vm.postMessage(msg) else onFeatures(picked)
        }
    }

    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(PanelCard)
            .border(0.8.dp, PanelLine, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(enabled = !busy) { picker.launch(arrayOf("*/*")) }
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                if (busy) "正在解析文件…" else "从文件导入",
                fontSize = 13.5.sp,
                color = PanelText,
                fontWeight = FontWeight.Medium
            )
            Text(hint, fontSize = 10.5.sp, color = PanelTextSub)
        }
        Text(
            if (busy) "…" else "选择文件",
            fontSize = 12.5.sp,
            color = PowerRed,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 通用编辑弹窗：名称 / 经度 / 纬度 / 备注
 *
 * 各图层实体的核心字段一致，用一个弹窗覆盖，避免为每个图层写一套表单。
 */
@Composable
fun EntityEditDialog(
    title: String,
    initialName: String,
    initialLon: Double?,
    initialLat: Double?,
    initialNote: String?,
    nameLabel: String = "名称",
    noteLabel: String = "备注",
    onDismiss: () -> Unit,
    onConfirm: (name: String, lon: Double, lat: Double, note: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var lon by remember { mutableStateOf(initialLon?.toString() ?: "") }
    var lat by remember { mutableStateOf(initialLat?.toString() ?: "") }
    var note by remember { mutableStateOf(initialNote ?: "") }
    val lonV = lon.toDoubleOrNull()
    val latV = lat.toDoubleOrNull()
    val valid = name.isNotBlank() && lonV != null && latV != null &&
        lonV in -180.0..180.0 && latV in -90.0..90.0

    LightPanelTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = PanelBg,
            title = { Text(title, fontSize = 15.sp, color = PanelText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(nameLabel, fontSize = 12.sp) },
                        colors = editFieldColors()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = lon,
                        onValueChange = { lon = it },
                        singleLine = true,
                        label = { Text("经度", fontSize = 12.sp) },
                        colors = editFieldColors()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        singleLine = true,
                        label = { Text("纬度", fontSize = 12.sp) },
                        colors = editFieldColors()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        singleLine = false,
                        label = { Text(noteLabel, fontSize = 12.sp) },
                        colors = editFieldColors()
                    )
                    if (!valid) {
                        Text(
                            "名称不能为空，经纬度需在有效范围内",
                            fontSize = 11.sp,
                            color = PowerRed,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Text(
                    "保存",
                    fontSize = 14.sp,
                    color = if (valid) PowerRed else PanelTextSub,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(enabled = valid) {
                            onConfirm(name.trim(), lonV!!, latV!!, note.trim().ifBlank { null })
                        }
                        .padding(10.dp)
                )
            },
            dismissButton = {
                Text(
                    "取消",
                    fontSize = 14.sp,
                    color = PanelTextSub,
                    modifier = Modifier.clickable { onDismiss() }.padding(10.dp)
                )
            }
        )
    }
}

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
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

/** 简单文本输入弹窗（电缆名称/电压等单值编辑用） */
@Composable
fun SingleFieldDialog(
    title: String,
    label: String,
    initial: String,
    numeric: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    LightPanelTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = PanelBg,
            title = { Text(title, fontSize = 15.sp, color = PanelText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(label, fontSize = 12.sp) },
                    colors = editFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Text(
                    "保存",
                    fontSize = 14.sp,
                    color = if (value.isBlank() || (numeric && value.toIntOrNull() == null)) PanelTextSub else PowerRed,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(enabled = value.isNotBlank() && (!numeric || value.toIntOrNull() != null)) {
                            onConfirm(value.trim())
                        }
                        .padding(10.dp)
                )
            },
            dismissButton = {
                Text(
                    "取消",
                    fontSize = 14.sp,
                    color = PanelTextSub,
                    modifier = Modifier.clickable { onDismiss() }.padding(10.dp)
                )
            }
        )
    }
}
