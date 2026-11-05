package com.kunming.powerline.ui.importui

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
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
import com.kunming.powerline.data.parser.ColumnMapping
import com.kunming.powerline.data.parser.ExcelParser
import com.kunming.powerline.data.parser.ParseResult
import com.kunming.powerline.data.parser.SheetPreview
import com.kunming.powerline.data.parser.TemplateGenerator
import com.kunming.powerline.data.repository.LineRepository
import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.FileSaver
import kotlinx.coroutines.launch
import java.io.File

/**
 * 表格导入：SAF 选文件 -> 预览/列映射 -> 解析校验 -> 错误导出 -> 写库
 */
@Composable
fun ExcelImportScreen(
    excelParser: ExcelParser,
    lineRepo: LineRepository,
    onBack: () -> Unit,
    onImported: (ParseResult, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var preview by remember { mutableStateOf<SheetPreview?>(null) }
    var mapping by remember { mutableStateOf(ColumnMapping()) }
    var result by remember { mutableStateOf<ParseResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var fileName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var pickUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            pickUri = uri
            loading = true
            scope.launch {
                val r = excelParser.preview(uri)
                loading = false
                r.onSuccess {
                    preview = it
                    mapping = it.mapping
                    fileName = it.fileName
                    result = null
                    message = if (it.mapping.valid) null else "未能自动识别「杆塔号/经度/纬度」列，请手动调整映射"
                }.onFailure { message = "读取失败：${it.message}" }
            }
        }
    }

    ImportScaffold(title = "从表格文件导入", subtitle = "所选表格格式需与导入模板一致", onBack = onBack) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (preview == null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1EDE4), RoundedCornerShape(12.dp))
                            .clickable {
                                picker.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "*/*"
                                    )
                                )
                            }
                            .padding(vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📄", fontSize = 34.sp)
                        Text("点击选择表格文件", fontSize = 14.sp, color = Color(0xFF5D4037), fontWeight = FontWeight.Medium)
                        Text("支持 .xlsx / .xls / .csv", fontSize = 11.5.sp, color = Color(0xFF8A8078))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "· 建议先下载「导入模板」，按列名填写\n· 坐标使用十进制度（WGS84 / CGCS2000）\n· 经纬度缺失或越界的行会被标记错误且不导入",
                        fontSize = 11.5.sp, color = Color(0xFF7A6F66), lineHeight = 19.sp
                    )
                } else {
                    val p = preview!!
                    Text("文件：${p.fileName} · 工作表：${p.sheetName}（${p.rows.size} 行）", fontSize = 12.sp, color = Color(0xFF444444))
                    Spacer(Modifier.height(10.dp))
                    Text("列映射（可手动调整）", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PowerRed)
                    MappingRow("线路名称", p.headers, mapping.lineName) { mapping = mapping.copy(lineName = it) }
                    MappingRow("电压等级", p.headers, mapping.voltage) { mapping = mapping.copy(voltage = it) }
                    MappingRow("杆塔号*", p.headers, mapping.towerNo) { mapping = mapping.copy(towerNo = it) }
                    MappingRow("经度*", p.headers, mapping.lon) { mapping = mapping.copy(lon = it) }
                    MappingRow("纬度*", p.headers, mapping.lat) { mapping = mapping.copy(lat = it) }
                    MappingRow("海拔", p.headers, mapping.elevation) { mapping = mapping.copy(elevation = it) }
                    MappingRow("杆塔类型", p.headers, mapping.towerType) { mapping = mapping.copy(towerType = it) }
                    MappingRow("回路数", p.headers, mapping.circuitCount) { mapping = mapping.copy(circuitCount = it) }

                    Spacer(Modifier.height(14.dp))
                    Text("数据预览", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PowerRed)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .heightIn(max = 250.dp)
                            .verticalScroll(rememberScrollState())
                    ) { PreviewTable(p) }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            PrimaryButton("校验并解析", enabled = mapping.valid) {
                                val u = pickUri ?: return@PrimaryButton
                                loading = true
                                progress = 0.05f
                                scope.launch {
                                    val r = excelParser.parse(u, mapping) { pct -> progress = pct }
                                    loading = false
                                    progress = null
                                    r.onSuccess { result = it; message = null }
                                        .onFailure { message = "解析失败：${it.message}" }
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            SecondaryButton("重新选择") {
                                preview = null; result = null; pickUri = null
                            }
                        }
                    }
                }

                result?.let { res ->
                    Spacer(Modifier.height(14.dp))
                    ParseSummary(res)
                    if (res.errors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("错误行（${res.errors.size}）", fontSize = 12.sp, color = PowerRed, fontWeight = FontWeight.Bold)
                        ErrorList(res.errors)
                        Spacer(Modifier.height(6.dp))
                        SecondaryButton("导出错误行（CSV，选择保存位置）") {
                            exportErrors(context, res) { msg -> message = msg }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        "确认写入台账（${res.lines.size} 条线路 / ${res.towerCount} 基杆塔）",
                        enabled = res.lines.isNotEmpty()
                    ) { onImported(res, fileName) }
                }

                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, color = PowerRed)
                }
                Spacer(Modifier.height(24.dp))
            }
            if (loading) LoadingOverlay("正在解析表格…", progress)
        }
    }
}

/** 导出错误 CSV（使用系统「新建文档」由用户选择路径） */
fun exportErrors(context: Context, result: ParseResult, onDone: (String) -> Unit) {
    val csv = TemplateGenerator(context).errorRowsCsv(result.errors)
    val dir = FileSaver.appFilesDir(context, "exports")
    val f = File(dir, "导入错误_" + System.currentTimeMillis() + ".csv")
    try {
        f.writeText(csv, Charsets.UTF_8)
        onDone("错误行已导出：${f.absolutePath}")
    } catch (t: Throwable) {
        onDone("导出失败：${t.message}")
    }
}

/**
 * 创建空线路页面
 */
@Composable
fun CreateLineScreen(
    lineRepo: LineRepository,
    onBack: () -> Unit,
    onCreated: (LineEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var voltage by remember { mutableStateOf(110) }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ImportScaffold(title = "创建线路", subtitle = "创建空线路，稍后在其中添加数据", onBack = onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            LabeledField("线路名称 *", name, { name = it }, "如：220kV 昆明甲线")
            Spacer(Modifier.height(10.dp))
            Text("电压等级 *", fontSize = 12.sp, color = Color(0xFF555555))
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.kunming.powerline.util.VoltagePalette.levels.forEach { lv ->
                    val active = voltage == lv
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) PowerRed else Color.White)
                            .clickable { voltage = lv }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${lv}kV", fontSize = 12.5.sp,
                            color = if (active) Color.White else Color(0xFF5D4037),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            LabeledField("起始变电站", start, { start = it }, "选填")
            Spacer(Modifier.height(10.dp))
            LabeledField("终止变电站", end, { end = it }, "选填")
            Spacer(Modifier.height(10.dp))
            LabeledField("投运日期", date, { date = it }, "如 2020-06-01")
            Spacer(Modifier.height(10.dp))
            LabeledField("备注", remark, { remark = it }, "选填")
            Spacer(Modifier.height(18.dp))
            PrimaryButton("创建空线路") {
                if (name.isBlank()) {
                    error = "线路名称不能为空"
                    return@PrimaryButton
                }
                scope.launch {
                    val line = LineEntity(
                        id = LineRepository.newId("L"),
                        name = name.trim(),
                        voltageLevel = voltage,
                        startStation = start.ifBlank { null },
                        endStation = end.ifBlank { null },
                        inServiceDate = date.ifBlank { null },
                        remark = remark.ifBlank { null },
                        isEmptyLine = true
                    )
                    lineRepo.createLine(line)
                    onCreated(line)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "创建后可在地图上长按「添加杆塔」，或通过表格 / KML 补充数据。",
                fontSize = 11.sp, color = Color(0xFF8A8078)
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = PowerRed)
            }
        }
    }
}
