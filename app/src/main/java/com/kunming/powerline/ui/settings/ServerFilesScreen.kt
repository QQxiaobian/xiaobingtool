package com.kunming.powerline.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunming.powerline.data.remote.model.ServerFileDto
import com.kunming.powerline.data.remote.model.ServerFileKind
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.FileSaver

/**
 * 服务器原始文件库（1.3.0）。
 *
 * 服务端扫描 NAS 的 files 目录，这里列出 KML / KMZ / GeoJSON / GPX / SHP(zip) 等文件，
 * 用户可直接把文件拷进 NAS 共享目录（不必先在 App 里上传），下载后按选择导入为
 * 「线路 + 杆塔」或「专题图层」，与本地文件导入走同一套解析器。
 */
@Composable
fun ServerFilesScreen(vm: MapViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val server by vm.serverAddresses.collectAsStateWithLifecycle()

    var files by remember { mutableStateOf<List<ServerFileDto>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(true) }

    fun tell(ok: Boolean, msg: String) { statusOk = ok; status = msg }
    fun refresh() {
        vm.listServerFiles { ok, msg, list ->
            tell(ok, msg)
            if (ok) files = list
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(context, uri)
            vm.uploadServerFile(uri, name) { ok, msg ->
                tell(ok, msg)
                if (ok) refresh()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LightPanelTheme {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFAFBF6EE))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color(0xFF333333))
                }
                Text(
                    "服务器文件", fontSize = 18.sp, fontWeight = FontWeight.Bold,
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
                Text("当前服务器：${server.pmsBase}", fontSize = 11.5.sp, color = Color(0xFF5C544A))
                Text(
                    "把 KML / KMZ / GeoJSON / GPX / SHP(zip) 拷进 NAS 的 files 目录即可自动出现；" +
                        "也可用下面的按钮从手机上传。",
                    fontSize = 10.5.sp, color = HintGray, lineHeight = 16.sp
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = PowerRed, contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) { Text("上传本地文件到服务器", fontSize = 13.sp) }

                if (status != null) {
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.Box(
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

                Spacer(Modifier.height(16.dp))
                SectionTitle("服务器上的文件（${files.size}）")
                if (files.isEmpty()) {
                    Text(
                        "暂无文件。请确认 NAS 已更新到 1.3 服务端、/health 里 files 字段可见，" +
                            "并把文件放进 nas-server/data/files/ 目录。",
                        fontSize = 11.5.sp, color = HintGray,
                        modifier = Modifier.padding(vertical = 10.dp), lineHeight = 17.sp
                    )
                } else {
                    files.forEach { f ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .background(Color.White, RoundedCornerShape(10.dp))
                                .padding(11.dp)
                        ) {
                            Text(
                                f.name, fontSize = 12.5.sp, color = Color(0xFF2B2B2B),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${ServerFileKind.label(f.name)} · ${FileSaver.formatSize(f.sizeBytes)} · ${f.updatedAt}",
                                fontSize = 10.5.sp, color = HintGray, lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    "导入为线路", fontSize = 11.5.sp, color = PowerRed,
                                    modifier = Modifier.clickable {
                                        vm.importServerFile(f, false) { ok, msg -> tell(ok, msg) }
                                    }
                                )
                                Text(
                                    "导入为图层", fontSize = 11.5.sp, color = PowerRed,
                                    modifier = Modifier.clickable {
                                        vm.importServerFile(f, true) { ok, msg -> tell(ok, msg) }
                                    }
                                )
                                Text(
                                    "删除", fontSize = 11.5.sp, color = Color(0xFF8D6E63),
                                    modifier = Modifier.clickable {
                                        vm.deleteServerFile(f.id) { ok, msg -> tell(ok, msg); if (ok) refresh() }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Divider(color = Color(0x14000000), thickness = 0.7.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "下载后按扩展名分派解析：KML/KMZ/GPX/GeoJSON → 线路或图层，ZIP/SHP → Shapefile。" +
                        "文件保持在服务器不动，可重复导入；导入结果按既有规则入库，同名线路不自动去重。",
                    fontSize = 10.5.sp, color = HintGray, lineHeight = 17.sp
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    } ?: uri.lastPathSegment.orEmpty()
} catch (t: Throwable) {
    uri.lastPathSegment.orEmpty()
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t, fontSize = 12.sp, color = PowerRed, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
