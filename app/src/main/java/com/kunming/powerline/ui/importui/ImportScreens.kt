package com.kunming.powerline.ui.importui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.data.local.entity.ImportBatchEntity
import com.kunming.powerline.data.parser.GeoFileParser
import com.kunming.powerline.data.parser.ParseResult
import com.kunming.powerline.data.parser.TemplateColumns
import com.kunming.powerline.data.parser.TemplateGenerator
import com.kunming.powerline.data.remote.PmsRepository
import com.kunming.powerline.data.remote.model.PmsLineDto
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.PowerRed
import kotlinx.coroutines.launch

/**
 * 10.1 查看导入模板
 */
@Composable
fun TemplateScreen(
    generator: TemplateGenerator,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            message = if (generator.writeTemplateTo(context, uri)) "模板已保存" else "保存失败"
        }
    }
    ImportScaffold(title = "查看导入模板", subtitle = "标准台账 Excel 模板（含填写说明）", onBack = onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("模板字段", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed)
            Spacer(Modifier.height(8.dp))
            val required = setOf(
                TemplateColumns.LINE_NAME, TemplateColumns.VOLTAGE,
                TemplateColumns.TOWER_NO, TemplateColumns.LON, TemplateColumns.LAT
            )
            TemplateColumns.all.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(c, fontSize = 12.5.sp, color = Color(0xFF333333), modifier = Modifier.weight(1f))
                    if (c in required) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x1FBF360C))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) { Text("必填", fontSize = 10.sp, color = Color(0xFFBF360C)) }
                    } else {
                        Text("选填", fontSize = 10.sp, color = HintGray)
                    }
                }
                Divider(color = Color(0x0F000000), thickness = 0.7.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text("填写说明", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed)
            Spacer(Modifier.height(6.dp))
            Text(
                "· 坐标格式：十进制度，WGS84 / CGCS2000，例如 102.7123, 25.0401\n" +
                    "· 同一线路的杆塔需填写相同「线路名称」\n" +
                    "· 「杆塔号」在同线路内不可重复，支持 1 / 1-1 / N12 等格式\n" +
                    "· 电压等级仅支持 500 / 220 / 110 / 35（kV）\n" +
                    "· 同塔多回线路可在「同塔多回标识」填「同塔」，系统会自动优化显示",
                fontSize = 12.sp, color = Color(0xFF5A4E44), lineHeight = 20.sp
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton("保存模板到本地") { saver.launch("输电线路导入模板.xlsx") }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = Color(0xFF2E7D32))
            }
        }
    }
}

/**
 * 10.6 地理信息文件导入
 */
@Composable
fun GeoImportScreen(
    parser: GeoFileParser,
    onBack: () -> Unit,
    onImported: (ParseResult, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ParseResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            loading = true
            progress = 0.05f
            scope.launch {
                val r = parser.parse(uri) { pct -> progress = pct }
                loading = false
                progress = null
                r.onSuccess {
                    result = it
                    message = if (it.isEmpty) "未解析到有效要素，请检查文件内容" else null
                }.onFailure { message = "解析失败：${it.message}" }
            }
        }
    }

    ImportScaffold(
        title = "从地理信息文件导入",
        subtitle = "支持 KML / KMZ / GeoJSON / GPX / Shapefile（zip）",
        onBack = onBack
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1EDE4), RoundedCornerShape(12.dp))
                        .clickable { picker.launch(arrayOf("*/*")) }
                        .padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🌐", fontSize = 32.sp)
                    Text("点击选择地理信息文件", fontSize = 14.sp, color = Color(0xFF5D4037), fontWeight = FontWeight.Medium)
                    Text(".kml / .kmz / .geojson / .gpx / .shp(zip)", fontSize = 11.sp, color = Color(0xFF8A8078))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "· KML：LineString→线路，Point→杆塔；Placemark 的 name/description 映射名称与备注\n" +
                        "· KMZ 自动解压读取内部 .kml\n" +
                        "· GeoJSON：按 properties.voltage / name / tower_number 映射\n" +
                        "· Shapefile：需将 .shp .dbf .shx .prj 打成一个 zip；自动做 Web Mercator / 高斯投影转换\n" +
                        "· 大文件在后台分片解析并显示进度",
                    fontSize = 11.5.sp, color = Color(0xFF7A6F66), lineHeight = 19.sp
                )

                result?.let { res ->
                    Spacer(Modifier.height(14.dp))
                    ParseSummary(res)
                    if (res.errors.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("错误（${res.errors.size}）", fontSize = 12.sp, color = PowerRed, fontWeight = FontWeight.Bold)
                        ErrorList(res.errors)
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("确认入库（${res.lines.size} 条线路）", enabled = res.lines.isNotEmpty()) {
                        onImported(res, "geo")
                    }
                }
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, color = PowerRed)
                }
                Spacer(Modifier.height(20.dp))
            }
            if (loading) LoadingOverlay("正在解析地理信息文件…", progress)
        }
    }
}

/**
 * 10.5 从输电管理所设备台帐导入
 */
@Composable
fun PmsImportScreen(
    pmsRepository: PmsRepository,
    onBack: () -> Unit,
    onImported: (ImportBatchEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    var unit by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var voltage by remember { mutableStateOf<Int?>(null) }
    var lines by remember { mutableStateOf<List<PmsLineDto>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf("") }

    fun load() {
        loading = true
        message = null
        scope.launch {
            val r = pmsRepository.fetchLines(unit.ifBlank { null }, voltage, keyword.ifBlank { null })
            loading = false
            r.onSuccess {
                lines = it
                message = if (it.isEmpty()) "未查询到线路（请检查 BASE_URL 与内网连接）" else null
            }.onFailure {
                message = "接口调用失败：${it.message}\n请确认已配置 PMS_BASE_URL 并处于内网环境"
            }
        }
    }

    ImportScaffold(
        title = "从输电管理所设备台帐导入",
        subtitle = "从输电管理所输电线路设备台帐导入线路信息",
        onBack = onBack
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("接口地址：${pmsRepository.baseUrl()}", fontSize = 11.sp, color = HintGray)
                Spacer(Modifier.height(10.dp))
                LabeledField("单位", unit, { unit = it }, "如：昆明输电管理所（可留空）")
                Spacer(Modifier.height(10.dp))
                LabeledField("线路名关键字", keyword, { keyword = it }, "模糊匹配，可留空")
                Spacer(Modifier.height(10.dp))
                Text("电压等级", fontSize = 12.sp, color = Color(0xFF555555))
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null, 500, 220, 110, 35).forEach { lv ->
                        val active = voltage == lv
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) PowerRed else Color.White)
                                .clickable { voltage = lv }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                lv?.let { "${it}kV" } ?: "全部",
                                fontSize = 11.5.sp,
                                color = if (active) Color.White else Color(0xFF5D4037)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("查询线路台账") { load() }

                if (lines.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("查询到 ${lines.size} 条线路", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PowerRed)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (selected.size == lines.size) "取消全选" else "全选",
                            fontSize = 12.sp, color = Color(0xFF5D4037),
                            modifier = Modifier.clickable {
                                selected = if (selected.size == lines.size) emptySet()
                                else lines.mapNotNull { it.id ?: it.lineName }.toSet()
                            }
                        )
                    }
                    Divider(color = Color(0x14000000), thickness = 0.7.dp, modifier = Modifier.padding(vertical = 6.dp))
                    lines.forEach { l ->
                        val key = l.id ?: l.lineName
                        val on = selected.contains(key)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (on) selected - key else selected + key }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (on) PowerRed else Color(0x22000000))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text(if (on) "✓" else " ", fontSize = 11.sp, color = Color.White) }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(l.lineName, fontSize = 13.sp, color = Color(0xFF2B2B2B))
                                Text(
                                    "${l.voltageLevel}  ${l.startStation ?: "—"} → ${l.endStation ?: "—"}",
                                    fontSize = 10.5.sp, color = HintGray
                                )
                            }
                        }
                        Divider(color = Color(0x0D000000), thickness = 0.6.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("导入选中线路（${selected.size}）", enabled = selected.isNotEmpty()) {
                        val chosen = lines.filter { selected.contains(it.id ?: it.lineName) }
                        loading = true
                        scope.launch {
                            val r = pmsRepository.importLines(chosen) { idx, name ->
                                progressText = "正在导入 $idx/${chosen.size}：$name"
                            }
                            loading = false
                            r.onSuccess { onImported(it) }
                                .onFailure { message = "导入失败：${it.message}" }
                        }
                    }
                }
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, color = PowerRed, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(20.dp))
            }
            if (loading) LoadingOverlay(progressText.ifBlank { "正在请求内网台账…" })
        }
    }
}

/**
 * 10.7 删除全部线路
 */
@Composable
fun DeleteAllScreen(
    lineCount: Int,
    towerCount: Int,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    ImportScaffold(title = "删除全部线路", onBack = onBack) {
        Column(Modifier.fillMaxWidth().weight(1f).padding(20.dp)) {
            Text("⚠", fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            Text(
                "当前共 $lineCount 条线路、$towerCount 基杆塔。",
                fontSize = 14.sp, color = Color(0xFF2B2B2B), modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "删除将同时清空关联杆塔、电缆数据，此操作不可恢复。",
                fontSize = 12.sp, color = Color(0xFFB71C1C), modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.weight(1f))
            if (done) {
                Text("已清空全部线路数据", fontSize = 13.sp, color = Color(0xFF2E7D32), modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
            }
            PrimaryButton("删除全部线路", enabled = lineCount > 0) { showDialog = true }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFFFBF6EE),
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    "确认删除全部 $lineCount 条线路及关联杆塔数据？",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B)
                )
            },
            text = {
                Column {
                    Text("此操作不可恢复。请输入「删除」二字确认：", fontSize = 12.5.sp, color = Color(0xFF555555))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Text(
                    "确认删除",
                    color = if (input == "删除") Color(0xFFB71C1C) else Color(0x44000000),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(enabled = input == "删除") {
                            showDialog = false
                            onConfirm()
                            done = true
                        }
                        .padding(10.dp)
                )
            },
            dismissButton = {
                Text(
                    "取消", color = Color(0xFF5D4037), fontSize = 14.sp,
                    modifier = Modifier.clickable { showDialog = false }.padding(10.dp)
                )
            }
        )
    }
}

/** 导入批次记录 */
@Composable
fun BatchListScreen(
    batches: List<ImportBatchEntity>,
    onBack: () -> Unit,
    onRollback: (String) -> Unit
) {
    ImportScaffold(title = "导入记录与撤销", subtitle = "5 分钟内可按批次撤销本次导入", onBack = onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (batches.isEmpty()) {
                Text("暂无导入记录", fontSize = 12.5.sp, color = HintGray, modifier = Modifier.padding(top = 20.dp))
            }
            batches.forEach { b ->
                val canRollback = b.status != "rolled_back" &&
                    System.currentTimeMillis() - b.importedAt <= 5 * 60 * 1000L
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.sourceType.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PowerRed)
                        Spacer(Modifier.weight(1f))
                        Text(
                            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                                .format(java.util.Date(b.importedAt)),
                            fontSize = 10.5.sp, color = HintGray
                        )
                    }
                    Text(
                        b.fileName ?: "—", fontSize = 12.sp, color = Color(0xFF333333),
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Text(
                        "杆塔 ${b.towerCount} 基 · 错误 ${b.errorRows} 行 · 状态 ${statusText(b.status)}",
                        fontSize = 11.sp, color = Color(0xFF666666)
                    )
                    if (canRollback) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "撤销本次导入", fontSize = 12.sp, color = Color(0xFFB71C1C),
                            modifier = Modifier.clickable { onRollback(b.id) }
                        )
                    }
                }
            }
        }
    }
}

private fun statusText(s: String) = when (s) {
    "success" -> "成功"
    "partial" -> "部分成功"
    "failed" -> "失败"
    "rolled_back" -> "已撤销"
    else -> s
}
