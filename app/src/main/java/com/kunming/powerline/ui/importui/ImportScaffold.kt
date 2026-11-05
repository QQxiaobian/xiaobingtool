package com.kunming.powerline.ui.importui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.data.parser.ColumnMapping
import com.kunming.powerline.data.parser.ImportError
import com.kunming.powerline.data.parser.ParseResult
import com.kunming.powerline.data.parser.SheetPreview
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.PowerRed

/**
 * 创建 / 导入线路 - 各子页面容器
 * 顶部居中标题 + 左侧返回，浅米色背景，选项间细分割线（10 节规范）
 */
@Composable
fun ImportScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFBF6EE))
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = Color(0xFF333333))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(title, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
            }
            Spacer(Modifier.width(44.dp))
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle, fontSize = 12.sp, color = Color(0xFF8A8078),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        Divider(color = Color(0x14000000), thickness = 0.8.dp)
        content()
    }
}

/** 入口菜单项 */
@Composable
fun ImportMenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, color = Color(0xFF2B2B2B), fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, fontSize = 11.5.sp, color = Color(0xFF8A8078), modifier = Modifier.padding(top = 2.dp))
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB0A89E), modifier = Modifier.size(18.dp))
        }
        Divider(color = Color(0x12000000), thickness = 0.7.dp, modifier = Modifier.padding(start = 20.dp))
    }
}

/** 主入口页：8 个选项 */
@Composable
fun ImportHomeScreen(onBack: () -> Unit, onPick: (String) -> Unit) {
    ImportScaffold(title = "创建或导入线路", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(6.dp))
            ImportMenuRow("查看导入模板", "下载标准 Excel 模板并查看字段填写说明") { onPick("template") }
            ImportMenuRow("创建线路", "创建空线路，稍后在其中添加数据") { onPick("create") }
            ImportMenuRow("杆塔显示优化", "优化同塔多回线路杆塔的显示效果") { onPick("towerDisplay") }
            ImportMenuRow("从表格文件导入", "所选表格格式需与导入模板一致（.xlsx / .xls / .csv）") { onPick("excel") }
            ImportMenuRow("从输电管理所设备台帐导入", "从输电管理所输电线路设备台帐导入线路信息") { onPick("pms") }
            ImportMenuRow("从地理信息文件导入（KML 等多格式）", "支持 KML / KMZ / GeoJSON / GPX / Shapefile") { onPick("geo") }
            ImportMenuRow("删除全部线路", "清空线路与关联杆塔数据（需二次确认）") { onPick("delete") }
            ImportMenuRow("导入记录与撤销", "查看最近导入批次，5 分钟内可撤销") { onPick("batches") }
        }
    }
}

/** 字段映射调整行 */
@Composable
fun MappingRow(label: String, headers: List<String>, index: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF555555), modifier = Modifier.width(78.dp))
        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(0.8.dp, Color(0x22000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    if (index in headers.indices) headers[index] else "（未映射）",
                    fontSize = 12.sp,
                    color = if (index in headers.indices) Color(0xFF333333) else Color(0xFFC62828)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Row {
            listOf("-" to -1, "▾" to 1).forEach { (sym, dir) ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x14000000))
                        .clickable {
                            val next = (index + dir).coerceIn(-1, headers.size - 1)
                            onChange(next)
                        },
                    contentAlignment = Alignment.Center
                ) { Text(sym, fontSize = 12.sp, color = Color(0xFF555555)) }
            }
        }
    }
}

/** 预览表格 */
@Composable
fun PreviewTable(preview: SheetPreview, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFEFE7DC))
                .padding(vertical = 6.dp)
        ) {
            preview.headers.take(8).forEach { h ->
                Text(
                    h, fontSize = 10.sp, color = Color(0xFF555555), fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(88.dp).padding(horizontal = 4.dp), maxLines = 1
                )
            }
        }
        preview.rows.take(40).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                r.take(8).forEach { cell ->
                    Text(
                        cell, fontSize = 10.sp, color = Color(0xFF333333),
                        modifier = Modifier.width(88.dp).padding(horizontal = 4.dp), maxLines = 1
                    )
                }
            }
            Divider(color = Color(0x0D000000), thickness = 0.6.dp)
        }
    }
}

/** 解析错误列表 */
@Composable
fun ErrorList(errors: List<ImportError>, modifier: Modifier = Modifier) {
    Column(modifier) {
        errors.take(60).forEach { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("第${e.row}行", fontSize = 10.5.sp, color = PowerRed, modifier = Modifier.width(56.dp))
                Text(e.message, fontSize = 11.sp, color = Color(0xFF554444), modifier = Modifier.weight(1f))
            }
        }
        if (errors.size > 60) Text("… 共 ${errors.size} 条错误", fontSize = 10.5.sp, color = HintGray)
    }
}

/** 解析结果摘要卡片 */
@Composable
fun ParseSummary(result: ParseResult) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1EDE4), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text("解析完成（${result.format.uppercase()}）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
        Text(
            "线路 ${result.lines.size} 条 · 杆塔 ${result.towerCount} 基 · 错误 ${result.errors.size} 条",
            fontSize = 12.sp, color = Color(0xFF444444), modifier = Modifier.padding(top = 3.dp)
        )
        if (result.warnings.isNotEmpty()) {
            Text(
                result.warnings.take(3).joinToString("\n"),
                fontSize = 10.5.sp, color = Color(0xFF8D6E00), modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PowerRed, contentColor = Color.White,
            disabledContainerColor = Color(0x33B71C1C)
        )
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(42.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFEDE5D9), contentColor = Color(0xFF5D4037)
        )
    ) { Text(text, fontSize = 13.sp) }
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    number: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.5.sp) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, fontSize = 12.sp, color = Color(0x66A09080)) }
        } else null,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
    )
}

@Composable
fun LoadingOverlay(text: String, progress: Float? = null) {
    Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.background(Color(0xFFFBF6EE), RoundedCornerShape(14.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (progress == null) {
                CircularProgressIndicator(color = PowerRed, modifier = Modifier.size(28.dp))
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    color = PowerRed,
                    modifier = Modifier.width(180.dp).height(5.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(text, fontSize = 12.5.sp, color = Color(0xFF444444))
        }
    }
}
