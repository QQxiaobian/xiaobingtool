package com.kunming.powerline.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.data.offline.OfflineTileStore
import com.kunming.powerline.data.offline.TileDownloader
import com.kunming.powerline.ui.map.BasemapType
import com.kunming.powerline.ui.map.TileSourceFactory
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PanelCard
import com.kunming.powerline.ui.theme.PanelLine
import com.kunming.powerline.ui.theme.PanelText
import com.kunming.powerline.ui.theme.PanelTextSub
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.FileSaver
import kotlinx.coroutines.launch
import java.io.File

/**
 * 基础地图数据设置
 *
 * 三个部分：
 * 1. 在线地图：自定义 XYZ 瓦片源（可增删，选择后作为当前底图）
 * 2. Style JSON：导入自定义 MapLibre 样式文件
 * 3. 离线瓦片包：导入 mbtiles / 瓦片 zip，按区域预下载
 */
@Composable
fun MapDataSettingsScreen(
    store: OfflineTileStore,
    downloader: TileDownloader,
    onBack: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }   // 0 在线地图 1 样式 2 离线包
    var packs by remember { mutableStateOf(store.packs()) }
    var styleJson by remember { mutableStateOf<String?>(null) }

    // 区域下载参数
    var rName by remember { mutableStateOf("工作区域") }
    var minLon by remember { mutableStateOf("102.60") }
    var minLat by remember { mutableStateOf("24.95") }
    var maxLon by remember { mutableStateOf("102.85") }
    var maxLat by remember { mutableStateOf("25.12") }
    var minZ by remember { mutableStateOf("10") }
    var maxZ by remember { mutableStateOf("15") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<TileDownloader.Progress?>(null) }

    val mbtilesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val name = "pack_" + System.currentTimeMillis().toString().takeLast(6)
                val r = store.importMbtiles(uri, name) { p -> progress = TileDownloader.Progress((p * 100).toInt(), 100, 0) }
                busy = false; progress = null
                packs = store.packs()
                onToast(r.fold({ "已导入 $it 张瓦片" }, { "导入失败：${it.message}" }))
            }
        }
    }

    val stylePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                onToast("读取样式文件失败")
            } else {
                val f = FileSaver.appFilesDir(context, "style")
                File(f, "custom_style.json").writeText(text)
                styleJson = text
                onToast("样式已导入，下次切换底图时生效")
            }
        }
    }

    LightPanelTheme {
    Column(Modifier.fillMaxSize().background(Color(0xFFFBF6EE)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Color(0xFF333333)) }
            Text("基础地图数据设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
        }
        Divider(color = Color(0x14000000), thickness = 0.8.dp)

        // 分段
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("在线地图", "Style JSON", "离线瓦片包").forEachIndexed { i, t ->
                Box(
                    Modifier
                        .background(if (tab == i) Color(0x1FB71C1C) else Color(0x0F000000), RoundedCornerShape(9.dp))
                        .clickable { tab = i }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(t, fontSize = 12.sp, color = if (tab == i) PowerRed else Color(0xFF6D6259))
                }
            }
        }

        when (tab) {
            0 -> OnlineMapTab(onToast)
            1 -> StyleTab(styleJson) { stylePicker.launch(arrayOf("application/json", "text/plain", "*/*")) }
            else -> OfflineTab(
                packs = packs,
                busy = busy,
                progress = progress,
                rName = rName, onRName = { rName = it },
                minLon = minLon, onMinLon = { minLon = it },
                minLat = minLat, onMinLat = { minLat = it },
                maxLon = maxLon, onMaxLon = { maxLon = it },
                maxLat = maxLat, onMaxLat = { maxLat = it },
                minZ = minZ, onMinZ = { minZ = it },
                maxZ = maxZ, onMaxZ = { maxZ = it },
                estimate = {
                    val r = buildRegion(rName, minLon, minLat, maxLon, maxLat, minZ, maxZ)
                    if (r == null) "参数不合法" else "预计 ${downloader.estimateCount(r)} 张瓦片"
                },
                onImportMbtiles = { mbtilesPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                onStartDownload = {
                    val r = buildRegion(rName, minLon, minLat, maxLon, maxLat, minZ, maxZ)
                    if (r == null) {
                        onToast("请检查经纬度与缩放级别")
                    } else {
                        busy = true
                        val url = TileSourceFactory.tiandituUrl("img", com.kunming.powerline.BuildConfig.TIANDITU_KEY)
                        scope.launch {
                            val res = downloader.download(r, url, "region_${r.name}", { p -> progress = p })
                            busy = false; progress = null
                            packs = store.packs()
                            onToast(res.fold({ "区域下载完成，共 $it 张瓦片" }, { "下载失败：${it.message}" }))
                        }
                    }
                },
                onDelete = { name ->
                    store.deletePack(name)
                    packs = store.packs()
                    onToast("已删除离线包 $name")
                }
            )
        }
    }
    }
    }

private fun buildRegion(
    name: String, minLon: String, minLat: String, maxLon: String, maxLat: String, minZ: String, maxZ: String
): TileDownloader.Region? {
    val a = minLon.toDoubleOrNull() ?: return null
    val b = minLat.toDoubleOrNull() ?: return null
    val c = maxLon.toDoubleOrNull() ?: return null
    val d = maxLat.toDoubleOrNull() ?: return null
    val z0 = minZ.toIntOrNull() ?: return null
    val z1 = maxZ.toIntOrNull() ?: return null
    if (a >= c || b >= d || z0 > z1) return null
    return TileDownloader.Region(name.ifBlank { "区域" }, a, b, c, d, z0, z1)
}

@Composable
private fun OnlineMapTab(onToast: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("内置底图", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed)
        Spacer(Modifier.height(6.dp))
        BasemapType.entries.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Color(0xFFB71C1C), RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontSize = 12.5.sp, color = Color(0xFF2B2B2B))
                    Text(t.desc, fontSize = 11.sp, color = Color(0xFF8A8078))
                }
                Text(if (t.isServer) "服务端" else "在线", fontSize = 11.sp, color = Color(0xFF9A8F86))
            }
        }
        Spacer(Modifier.height(14.dp))
        Divider(color = Color(0x14000000))
        Spacer(Modifier.height(10.dp))
        Text("自定义 XYZ 瓦片源", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed)
        Spacer(Modifier.height(4.dp))
        Text(
            "在「本地配置」中为 TILE_SERVER_URL 指定自建瓦片服务地址（形如\nhttp://10.0.0.1:8090/tiles/{z}/{x}/{y}.png），\n或使用下方离线包以 file:// 方式加载。",
            fontSize = 11.5.sp, color = Color(0xFF7A6F66), lineHeight = 18.sp
        )
    }
}

@Composable
private fun StyleTab(styleJson: String?, onPick: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("自定义 MapLibre Style JSON", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed)
        Spacer(Modifier.height(6.dp))
        Text(
            "导入一份 MapLibre 样式文件后，切换底图时会优先使用它（需包含 sources / layers 定义）。\n文件中可用 {z}/{x}/{y} 占位符指定瓦片地址。",
            fontSize = 11.5.sp, color = Color(0xFF7A6F66), lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().background(Color(0x14B71C1C), RoundedCornerShape(10.dp)).clickable { onPick() }.padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) { Text("选择 Style JSON 文件", fontSize = 12.5.sp, color = PowerRed, fontWeight = FontWeight.Medium) }
        Spacer(Modifier.height(12.dp))
        if (styleJson != null) {
            Text("已导入样式预览（前 1200 字符）", fontSize = 11.5.sp, color = Color(0xFF6D6259))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().background(Color(0xFFF1EDE4), RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text(styleJson.take(1200), fontSize = 10.5.sp, color = Color(0xFF444444), lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun OfflineTab(
    packs: List<File>,
    busy: Boolean,
    progress: TileDownloader.Progress?,
    rName: String, onRName: (String) -> Unit,
    minLon: String, onMinLon: (String) -> Unit,
    minLat: String, onMinLat: (String) -> Unit,
    maxLon: String, onMaxLon: (String) -> Unit,
    maxLat: String, onMaxLat: (String) -> Unit,
    minZ: String, onMinZ: (String) -> Unit,
    maxZ: String, onMaxZ: (String) -> Unit,
    estimate: () -> String,
    onImportMbtiles: () -> Unit,
    onStartDownload: () -> Unit,
    onDelete: (String) -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).background(Color(0x14B71C1C), RoundedCornerShape(10.dp)).clickable { onImportMbtiles() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text("导入 mbtiles", fontSize = 12.5.sp, color = PowerRed, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("按区域预下载（天地图）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed)
        Spacer(Modifier.height(6.dp))
        Field("区域名称", rName, onRName)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("最小经度", minLon, onMinLon) }
            Box(Modifier.weight(1f)) { Field("最小纬度", minLat, onMinLat) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("最大经度", maxLon, onMaxLon) }
            Box(Modifier.weight(1f)) { Field("最大纬度", maxLat, onMaxLat) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("最小层级", minZ, onMinZ) }
            Box(Modifier.weight(1f)) { Field("最大层级", maxZ, onMaxZ) }
        }
        Text(estimate(), fontSize = 11.5.sp, color = Color(0xFF7A6F66))
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().background(if (busy) Color(0x33B71C1C) else PowerRed, RoundedCornerShape(10.dp))
                .clickable(enabled = !busy) { onStartDownload() }.padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) { Text(if (busy) "下载中…" else "开始下载", fontSize = 12.5.sp, color = Color.White, fontWeight = FontWeight.Medium) }

        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                "${progress.done} / ${progress.total}  失败 ${progress.failed}",
                fontSize = 11.sp, color = Color(0xFF7A6F66)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("已导入离线包（${packs.size}）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed)
        if (packs.isEmpty()) {
            Text("暂无离线包", fontSize = 11.5.sp, color = Color(0xFF8A8078), modifier = Modifier.padding(vertical = 8.dp))
        } else {
            packs.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 12.5.sp, color = Color(0xFF2B2B2B))
                        Text(FileSaver.formatSize(packSizeOf(p)), fontSize = 11.sp, color = Color(0xFF8A8078))
                    }
                    Text("删除", fontSize = 11.5.sp, color = PowerRed, modifier = Modifier.clickable { onDelete(p.name) }.padding(6.dp))
                }
                Divider(color = Color(0x0F000000), thickness = 0.6.dp)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun packSizeOf(f: File): Long = runCatching { f.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PanelText,
            unfocusedTextColor = PanelText,
            focusedContainerColor = PanelCard,
            unfocusedContainerColor = PanelCard,
            cursorColor = PowerRed,
            focusedBorderColor = PowerRed,
            unfocusedBorderColor = PanelLine,
            focusedLabelColor = PowerRed,
            unfocusedLabelColor = PanelTextSub
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    )
}
