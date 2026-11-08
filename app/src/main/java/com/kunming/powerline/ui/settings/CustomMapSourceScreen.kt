package com.kunming.powerline.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.kunming.powerline.data.local.entity.OnlineMapEntity
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PowerRed

/**
 * 自定义地图源 / 自定义地图服务器管理。
 *
 * 可添加任意 XYZ / TMS 瓦片服务（含自建 NAS 地图服务器）；
 * 启用的源作为叠加层，其中一个可设为「主底图」替代内置底图。
 */
@Composable
fun CustomMapSourceScreen(vm: MapViewModel, onBack: () -> Unit) {
    val sources by vm.customMapSources.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(true) }

    var fName by remember { mutableStateOf("") }
    var fUrl by remember { mutableStateOf("") }
    var fSubs by remember { mutableStateOf("") }
    var fTms by remember { mutableStateOf(false) }
    var fSize by remember { mutableStateOf("256") }
    var fMinZ by remember { mutableStateOf("0") }
    var fMaxZ by remember { mutableStateOf("18") }
    var fOpacity by remember { mutableStateOf("100") }

    fun resetForm() {
        editId = null
        fName = ""
        fUrl = ""
        fSubs = ""
        fTms = false
        fSize = "256"
        fMinZ = "0"
        fMaxZ = "18"
        fOpacity = "100"
    }

    fun startEdit(e: OnlineMapEntity) {
        editId = e.id
        fName = e.name
        fUrl = e.tileUrl
        fSubs = e.subdomains
        fTms = e.isTms
        fSize = e.tileSize.toString()
        fMinZ = e.minZoom.toString()
        fMaxZ = e.maxZoom.toString()
        fOpacity = (e.opacity * 100).toInt().toString()
        showForm = true
    }

    fun tell(ok: Boolean, msg: String) { statusOk = ok; status = msg }

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
                    "自定义地图源", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF2B2B2B), modifier = Modifier.weight(1f)
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
                    "添加任意 XYZ / TMS 瓦片服务作为自定义地图源，也可以直接指向自建 NAS 地图服务器。" +
                        "启用的源叠加在底图上，另有其中一个可设为「主底图」替代内置底图。",
                    fontSize = 12.sp, color = Color(0xFF5C544A), lineHeight = 18.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "示例：http://192.168.1.10:8080/tiles/{z}/{x}/{y}.png\n" +
                        "多子域：https://{s}.example.com/{z}/{x}/{y}.png（子域填 a,b,c）",
                    fontSize = 10.5.sp, color = HintGray, lineHeight = 16.sp
                )
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { resetForm(); showForm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PowerRed, contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("+ 新增地图源", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { vm.setCustomMapBase(null) { tell(true, it) } },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("恢复内置底图", fontSize = 12.sp) }
                }

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
                            color = if (statusOk) Color(0xFF2E7D32) else Color(0xFFB71C1C)
                        )
                    }
                }

                // ---------------- 新增 / 编辑表单 ----------------
                if (showForm) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            if (editId == null) "新增地图源" else "编辑地图源",
                            fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PowerRed
                        )
                        Field("名称", fName) { fName = it }
                        Field("瓦片地址（含 {z}/{x}/{y}）", fUrl) { fUrl = it }
                        Field("子域名（可选，逗号分隔）", fSubs) { fSubs = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { Field("最小层级", fMinZ) { fMinZ = it } }
                            Box(Modifier.weight(1f)) { Field("最大层级", fMaxZ) { fMaxZ = it } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { Field("瓦片尺寸 256/512", fSize) { fSize = it } }
                            Box(Modifier.weight(1f)) { Field("不透明度 %", fOpacity) { fOpacity = it } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceButton("XYZ 方案", !fTms, Modifier.weight(1f)) { fTms = false }
                            ChoiceButton("TMS 方案", fTms, Modifier.weight(1f)) { fTms = true }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceButton("256px", fSize == "256", Modifier.weight(1f)) { fSize = "256" }
                            ChoiceButton("512px", fSize == "512", Modifier.weight(1f)) { fSize = "512" }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showForm = false; resetForm() },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) { Text("取消", fontSize = 12.sp) }
                            Button(
                                onClick = {
                                    val old = sources.firstOrNull { it.id == editId }
                                    val e = OnlineMapEntity(
                                        id = editId ?: vm.newCustomMapId(),
                                        name = fName.trim().ifBlank { "自定义地图源" },
                                        tileUrl = fUrl.trim(),
                                        subdomains = fSubs.trim(),
                                        minZoom = fMinZ.toIntOrNull() ?: 0,
                                        maxZoom = fMaxZ.toIntOrNull() ?: 18,
                                        enabled = old?.enabled ?: true,
                                        tileSize = fSize.toIntOrNull() ?: 256,
                                        scheme = if (fTms) "tms" else "xyz",
                                        opacity = (fOpacity.toFloatOrNull() ?: 100f)
                                            .coerceIn(0f, 100f) / 100f,
                                        asBase = old?.asBase ?: false,
                                        sortOrder = old?.sortOrder ?: sources.size
                                    )
                                    vm.saveCustomMapSource(e) { msg ->
                                        val ok = !msg.startsWith("地址不合法")
                                        tell(ok, msg)
                                        if (ok) { showForm = false; resetForm() }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PowerRed, contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) { Text("保存", fontSize = 12.sp) }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "已添加的地图源（${sources.size}）",
                    fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B)
                )
                Spacer(Modifier.height(6.dp))

                if (sources.isEmpty()) {
                    Text(
                        "暂无自定义地图源。新增后开启开关即可叠加显示。",
                        fontSize = 11.5.sp, color = HintGray,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    sources.forEach { s ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(Color.White, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.name + if (s.asBase) "  · 主底图" else "",
                                        fontSize = 12.5.sp,
                                        color = if (s.asBase) PowerRed else Color(0xFF2B2B2B),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        s.tileUrl, fontSize = 10.sp, color = HintGray,
                                        maxLines = 2
                                    )
                                    Text(
                                        "${if (s.isTms) "TMS" else "XYZ"} · ${s.tileSize}px · " +
                                            "z${s.minZoom}~${s.maxZoom} · ${(s.opacity * 100).toInt()}%",
                                        fontSize = 10.sp, color = HintGray
                                    )
                                }
                                Switch(
                                    checked = s.enabled,
                                    onCheckedChange = { v ->
                                        vm.toggleCustomMapSource(s.id, v) { tell(true, it) }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = PowerRed
                                    )
                                )
                            }
                            Divider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = Color(0x14000000),
                                thickness = 0.6.dp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ActionText(
                                    if (s.asBase) "取消主底图" else "设为主底图"
                                ) {
                                    vm.setCustomMapBase(if (s.asBase) null else s.id) { tell(true, it) }
                                }
                                ActionText("编辑") { startEdit(s) }
                                ActionText("删除", color = PowerRed) {
                                    vm.deleteCustomMapSource(s.id) { tell(true, it) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 10.5.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp)
    )
}

@Composable
private fun ChoiceButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(
                if (selected) Color(0x1AB71C1C) else Color(0xFFF1EDE4),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 11.5.sp,
            color = if (selected) PowerRed else Color(0xFF7A6F66),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ActionText(text: String, color: Color = Color(0xFF8D6E63), onClick: () -> Unit) {
    Text(text, fontSize = 11.5.sp, color = color, modifier = Modifier.clickable { onClick() })
}
