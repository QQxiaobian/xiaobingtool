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
import androidx.compose.foundation.layout.width
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
import com.kunming.powerline.data.parser.ThematicParseResult
import com.kunming.powerline.data.parser.TemplateGenerator
import com.kunming.powerline.data.remote.PmsRepository
import com.kunming.powerline.data.remote.model.PmsLineDto
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PanelBg
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
                                .background(Color(0x1FB71C1C))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) { Text("必填", fontSize = 10.sp, color = Color(0xFFB71C1C)) }
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
    onImported: (ParseResult, String) -> Unit,
    onImportedThematic: (ThematicParseResult, String, String) -> Unit = { _, _, _ -> },
    startThematic: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var thematicMode by remember { mutableStateOf(startThematic) }
    var result by remember { mutableStateOf<ParseResult?>(null) }
    var thematicResult by remember { mutableStateOf<ThematicParseResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var fileCount by remember { mutableStateOf(0) }
    var fileName by remember { mutableStateOf("") }

    fun reset() {
        result = null
        thematicResult = null
        message = null
        fileCount = 0
    }

    // 多选文件启动器（批量导入）
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            reset()
            fileCount = uris.size
            loading = true
            progress = 0.05f
            scope.launch {
                if (thematicMode) {
                    val r = if (uris.size == 1) {
                        parser.parseThematic(uris.first())
                    } else {
                        parser.parseThematicMultiple(uris) { pct -> progress = pct }
                    }
                    loading = false
                    progress = null
                    r.onSuccess {
                        thematicResult = it
                        fileName = it.layers.firstOrNull()?.name ?: ""
                        message = if (it.isEmpty) "未解析到有效要素，请检查文件内容" else null
                    }.onFailure { message = "解析失败：${it.message}" }
                } else {
                    val r = if (uris.size == 1) {
                        parser.parse(uris.first()) { pct -> progress = pct }
                    } else {
                        parser.parseMultiple(uris) { pct -> progress = pct }
                    }
                    loading = false
                    progress = null
                    r.onSuccess {
                        result = it
                        message = if (it.isEmpty) "未解析到有效要素，请检查文件内容" else null
                    }.onFailure { message = "解析失败：${it.message}" }
                }
            }
        }
    }

    ImportScaffold(
        title = if (thematicMode) "导入专题图层" else "从地理信息文件导入",
        subtitle = if (thematicMode) "支持 KML / KMZ / GeoJSON / GPX / SHP（点、线、面）"
        else "支持 KML / KMZ / GeoJSON / GPX / Shapefile（.zip 打包或单个 .shp）",
        onBack = onBack
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 导入类型切换
                Row(Modifier.fillMaxWidth()) {
                    ModeChip("导入为线路", !thematicMode, Modifier.weight(1f)) {
                        if (thematicMode) { thematicMode = false; reset() }
                    }
                    Spacer(Modifier.width(10.dp))
                    ModeChip("导入为专题图层", thematicMode, Modifier.weight(1f)) {
                        if (!thematicMode) { thematicMode = true; reset() }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (thematicMode)
                        "点、线、面要素原样保留，一个文件（或其中的 Folder）生成一个图层，可在「专题图层」中管理。"
                    else
                        "把线要素当作输电线路、点要素当作杆塔导入，可在「线路列表」中管理。",
                    fontSize = 11.5.sp, color = Color(0xFF7A6F66), lineHeight = 18.sp
                )

                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1EDE4), RoundedCornerShape(12.dp))
                        .clickable { multiPicker.launch(arrayOf("*/*")) }
                        .padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (thematicMode) "🗺️" else "🌐", fontSize = 32.sp)
                    Text("点击选择地理信息文件（支持多选）", fontSize = 14.sp, color = Color(0xFF5D4037), fontWeight = FontWeight.Medium)
                    Text(
                        if (thematicMode) ".kml / .kmz / .geojson / .gpx" else ".kml / .kmz / .geojson / .gpx / .shp(zip)",
                        fontSize = 11.sp, color = Color(0xFF8A8078)
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (thematicMode) {
                    Text(
                        "· 一个 Folder = 一个图层，图层名取 Folder 名；无 Folder 时取 Document 名，再退回文件名\n" +
                            "· 要素名（Placemark name）原样保留，作为点位名称\n" +
                            "· 支持点、线、面，以及 MultiGeometry 混合要素\n" +
                            "· KMZ 自动解压读取内部 .kml；支持批量导入多个文件",
                        fontSize = 11.5.sp, color = Color(0xFF7A6F66), lineHeight = 19.sp
                    )
                } else {
                    Text(
                        "· 线路名取文件内名称：Folder → Document → Placemark，均无才用文件名\n" +
                            "· 杆塔编号原样保留（0012 / N35 / 12号塔），不做重排\n" +
                            "· LineString → 线路，Point → 杆塔；同一 Folder 下的点自动按沿线顺序排列\n" +
                            "· KMZ 自动解压读取内部 .kml；支持批量导入",
                        fontSize = 11.5.sp, color = Color(0xFF7A6F66), lineHeight = 19.sp
                    )
                }

                if (fileCount > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text("已选择 $fileCount 个文件", fontSize = 11.sp, color = PowerRed, fontWeight = FontWeight.Medium)
                }

                thematicResult?.let { res ->
                    Spacer(Modifier.height(14.dp))
                    ThematicSummary(res)
                    if (res.errors.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("问题（${res.errors.size}）", fontSize = 12.sp, color = PowerRed, fontWeight = FontWeight.Bold)
                        ErrorList(res.errors)
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        "确认入库（${res.layers.size} 个图层 / ${res.featureCount} 个要素）",
                        enabled = !res.isEmpty
                    ) {
                        onImportedThematic(res, "geo", fileName)
                    }
                }

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

/** 导入类型切换按钮 */
@Composable
private fun ModeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) PowerRed else Color(0xFFEFE7DC))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            color = if (selected) Color.White else Color(0xFF5D4037),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/** 专题图层解析结果摘要 */
@Composable
private fun ThematicSummary(res: ThematicParseResult) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1EDE4), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(
            "解析完成（${res.format.uppercase()}）",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)
        )
        Text(
            "图层 ${res.layers.size} 个 · 要素 ${res.featureCount} 个 · 问题 ${res.errors.size} 条",
            fontSize = 12.sp, color = Color(0xFF444444), modifier = Modifier.padding(top = 3.dp)
        )
        res.layers.take(6).forEach { l ->
            Text(
                "· ${l.name}（${l.features.size} 个，${kindName(l.geometryType)}）",
                fontSize = 11.sp, color = Color(0xFF5D4037), modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (res.layers.size > 6) {
            Text("… 共 ${res.layers.size} 个图层", fontSize = 10.5.sp, color = Color(0xFF8A8078))
        }
        if (res.warnings.isNotEmpty()) {
            Text(
                res.warnings.take(3).joinToString("\n"),
                fontSize = 10.5.sp, color = Color(0xFF8D6E00), modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun kindName(kind: String): String = when (kind) {
    "point" -> "点"
    "line" -> "线"
    "polygon" -> "面"
    "mixed" -> "点线面混合"
    else -> kind
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
        LightPanelTheme {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = PanelBg,
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
