package com.kunming.powerline.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.kunming.powerline.data.remote.model.DatasetBundleDto
import com.kunming.powerline.data.remote.model.DatasetMetaDto
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.FileSaver

/**
 * 数据同步：NAS / 服务器 / 本地 JSON 三路。
 *
 * - 上传：把本机专题图层 + 线路台账打包上传
 * - 下载：从服务器拉取数据集并按 id / 名称合并进本地库
 * - 本地文件：导出 / 导入同一份 JSON，便于离线流转与备份
 */
@Composable
fun DataSyncScreen(vm: MapViewModel, onBack: () -> Unit) {
    val server by vm.serverAddresses.collectAsStateWithLifecycle()

    var datasets by remember { mutableStateOf<List<DatasetMetaDto>>(emptyList()) }
    var name by remember { mutableStateOf("巡检数据包") }
    var kind by remember { mutableStateOf(DatasetBundleDto.KIND_MIXED) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(true) }

    fun tell(ok: Boolean, msg: String) { statusOk = ok; status = msg }
    fun refresh() {
        vm.listDatasets { ok, msg, list ->
            tell(ok, msg)
            if (ok) datasets = list
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            vm.exportDataset(uri, name, kind) { ok, msg -> tell(ok, msg) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.importDataset(uri) { ok, msg -> tell(ok, msg); if (ok) refresh() }
        }
    }

    // 1.3.0：一键全量导出 / 导入（无需填名称、无需选类型）
    val exportAllLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) vm.exportAllDataset(uri) { ok, msg -> tell(ok, msg) }
    }
    val exportGeoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) vm.exportAllGeoJson(uri) { ok, msg -> tell(ok, msg) }
    }
    val importAllLineLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.importAny(uri, false) { ok, msg -> tell(ok, msg); if (ok) refresh() }
    }
    val importAllLayerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.importAny(uri, true) { ok, msg -> tell(ok, msg); if (ok) refresh() }
    }

    LaunchedEffect(Unit) { refresh() }

    LightPanelTheme {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFAFBF6EE))
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color(0xFF333333))
                }
                Text(
                    "数据同步", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF2B2B2B), modifier = Modifier.weight(1f)
                )
                Text(
                    "刷新", fontSize = 12.5.sp, color = PowerRed,
                    modifier = Modifier.clickable { refresh() }.padding(horizontal = 12.dp)
                )
            }
            Divider(color = Color(0x14000000), thickness = 0.8.dp)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "当前服务器：${server.pmsBase}",
                    fontSize = 11.5.sp, color = Color(0xFF5C544A)
                )
                Text(
                    "上传 / 下载都走这个地址，可在「设置 → 服务器（自建 NAS）」里修改。",
                    fontSize = 10.5.sp, color = HintGray, lineHeight = 16.sp
                )

                // ---------------- 全部线路 / 图层 导出导入（1.3.0） ----------------
                Spacer(Modifier.height(14.dp))
                SectionTitle("全部线路 / 图层 导出与导入")
                Text(
                    "一键导出本机全部图层、线路与杆塔，无需填名称或选类型。" +
                        "「备份 JSON」可无损导回本 App；「GeoJSON」用于 QGIS / Google Earth 等外部系统。",
                    fontSize = 11.sp, color = Color(0xFF5C544A), lineHeight = 17.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportAllLauncher.launch("全量备份-${System.currentTimeMillis()}.json") },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("全量导出 备份JSON", fontSize = 11.5.sp) }
                    OutlinedButton(
                        onClick = { exportGeoLauncher.launch("全量数据-${System.currentTimeMillis()}.geojson") },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("全量导出 GeoJSON", fontSize = 11.5.sp) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { importAllLineLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("全量导入 · 线路", fontSize = 11.5.sp) }
                    OutlinedButton(
                        onClick = { importAllLayerLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("全量导入 · 图层", fontSize = 11.5.sp) }
                }
                Text(
                    "导入会自动识别 App 全量备份 JSON 并无损还原；其它格式按所选「线路」或「图层」解析。",
                    fontSize = 10.sp, color = HintGray, lineHeight = 16.sp
                )

                // ---------------- 上传到服务器 ----------------
                Spacer(Modifier.height(16.dp))
                SectionTitle("上传本机数据到服务器")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("数据集名称", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                )
                Text("选择要上传的内容", fontSize = 11.sp, color = HintGray)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DatasetBundleDto.KIND_MIXED to "图层 + 线路",
                        DatasetBundleDto.KIND_LAYERS to "仅图层",
                        DatasetBundleDto.KIND_LINES to "仅线路"
                    ).forEach { (k, label) ->
                        Box(
                            Modifier
                                .weight(1f)
                                .background(
                                    if (kind == k) Color(0x1AB71C1C) else Color(0xFFF1EDE4),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { kind = k }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label, fontSize = 11.5.sp,
                                color = if (kind == k) PowerRed else Color(0xFF7A6F66),
                                fontWeight = if (kind == k) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.uploadDataset(name, kind) { ok, msg -> tell(ok, msg); if (ok) refresh() } },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PowerRed, contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) { Text("上传到服务器", fontSize = 13.sp) }

                if (status != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (statusOk) Color(0xFFE8F5E9) else Color(0xFFFDECEC),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            status!!, fontSize = 11.5.sp,
                            color = if (statusOk) Color(0xFF2E7D32) else Color(0xFFB71C1C),
                            lineHeight = 17.sp
                        )
                    }
                }

                // ---------------- 服务器数据集 ----------------
                Spacer(Modifier.height(16.dp))
                SectionTitle("服务器上的数据集（${datasets.size}）")
                if (datasets.isEmpty()) {
                    Text(
                        "服务器上暂无数据集，或无法连接。请检查 NAS 地址与 /health 是否可达。",
                        fontSize = 11.5.sp, color = HintGray,
                        modifier = Modifier.padding(vertical = 10.dp), lineHeight = 17.sp
                    )
                } else {
                    datasets.forEach { d ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .background(Color.White, RoundedCornerShape(10.dp))
                                .padding(11.dp)
                        ) {
                            Text(d.name, fontSize = 12.5.sp, color = Color(0xFF2B2B2B), fontWeight = FontWeight.Medium)
                            Text(
                                "${kindLabel(d.kind)} · ${d.layerCount} 图层 / ${d.featureCount} 要素 / " +
                                    "${d.lineCount} 线路 / ${d.towerCount} 杆塔",
                                fontSize = 10.5.sp, color = HintGray, lineHeight = 16.sp
                            )
                            Text(
                                "v${d.version} · ${d.updatedAt} · ${FileSaver.formatSize(d.sizeBytes)}",
                                fontSize = 10.sp, color = HintGray
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    "下载到本机", fontSize = 11.5.sp, color = PowerRed,
                                    modifier = Modifier.clickable {
                                        vm.downloadDataset(d.id) { ok, msg -> tell(ok, msg) }
                                    }
                                )
                                Text(
                                    "删除", fontSize = 11.5.sp, color = Color(0xFF8D6E63),
                                    modifier = Modifier.clickable {
                                        vm.deleteDataset(d.id) { ok, msg -> tell(ok, msg); if (ok) refresh() }
                                    }
                                )
                            }
                        }
                    }
                }

                // ---------------- 本地 JSON ----------------
                Spacer(Modifier.height(16.dp))
                SectionTitle("本地文件（离线流转 / 备份）")
                Text(
                    "导出与导入使用同一份 JSON 格式（powerline-dataset/1），" +
                        "和 NAS 上的数据集内容完全一致，可拷贝到电脑、U 盘或另一台手机。",
                    fontSize = 11.sp, color = Color(0xFF5C544A), lineHeight = 17.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("巡检数据-${System.currentTimeMillis()}.json") },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("导出本地 JSON", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("导入本地 JSON", fontSize = 12.sp) }
                }

                Spacer(Modifier.height(14.dp))
                Divider(color = Color(0x14000000), thickness = 0.7.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "下载 / 导入按图层 id、线路名称、杆塔号 UPSERT 合并，重复操作不会产生重复数据；" +
                        "同名图层以服务器版本为准，会覆盖本地的显示开关设置。",
                    fontSize = 10.5.sp, color = HintGray, lineHeight = 17.sp
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t, fontSize = 12.sp, color = PowerRed, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

private fun kindLabel(k: String): String = when (k) {
    DatasetBundleDto.KIND_LAYERS -> "图层数据"
    DatasetBundleDto.KIND_LINES -> "线路数据"
    else -> "图层 + 线路"
}
