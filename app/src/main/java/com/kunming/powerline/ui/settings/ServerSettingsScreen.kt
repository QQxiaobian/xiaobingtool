package com.kunming.powerline.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.kunming.powerline.data.prefs.ServerAddresses
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PowerRed

/**
 * 服务器地址设置（运行时可改，改完立即生效，无需重新打包 APK）。
 *
 * 常规用法：只填 NAS 主地址（如 http://192.168.1.10:8080），点「测试连接」，
 * NAS 会在 /health 里返回推荐的三件套地址并自动填入；点「保存」即可。
 */
@Composable
fun ServerSettingsScreen(vm: MapViewModel, onBack: () -> Unit) {
    val current by vm.serverAddresses.collectAsStateWithLifecycle()

    var base by remember(current.pmsBase) {
        mutableStateOf(current.pmsBase.trimEnd('/'))
    }
    var pms by remember(current.pmsBase) { mutableStateOf(current.pmsBase) }
    var ws by remember(current.wsLocation) { mutableStateOf(current.wsLocation) }
    var tiles by remember(current.tileServer) { mutableStateOf(current.tileServer) }
    var advanced by remember { mutableStateOf(true) }
    var testing by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LightPanelTheme {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFAFBF6EE))
        ) {
            // ---------------- 顶栏 ----------------
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color(0xFF333333))
                }
                Text(
                    "服务器地址", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF2B2B2B), modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        vm.saveServerAddresses(
                            ServerAddresses(pms.trim(), ws.trim(), tiles.trim())
                        ) { msg -> status = msg; success = true }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PowerRed, contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(36.dp)
                ) { Text("保存", fontSize = 12.5.sp) }
            }
            Divider(color = Color(0x14000000), thickness = 0.8.dp)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "连接自建 NAS 的地图 / 线路服务器。只填一个主地址即可，App 会自动推导台账、位置共享与瓦片三个地址。",
                    fontSize = 12.sp, color = Color(0xFF5C544A), lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))

                // ---------------- 主地址 ----------------
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text("NAS 主地址", fontSize = 12.sp) },
                    placeholder = { Text("192.168.1.10:8080", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Text(
                    "可只填 IP:端口，会自动补 http://；例如 http://192.168.1.10:8080",
                    fontSize = 10.5.sp, color = HintGray,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val d = ServerAddresses.deriveFrom(base)
                            pms = d.pmsBase
                            ws = d.wsLocation
                            tiles = d.tileServer
                            success = true
                            status = "已按主地址推导三个地址，可直接保存"
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("一键填充", fontSize = 12.sp) }
                    Button(
                        onClick = {
                            testing = true
                            status = null
                            vm.testServer(base) { ok, msg, recommended ->
                                testing = false
                                success = ok
                                status = msg
                                if (recommended != null) {
                                    pms = recommended.pmsBase
                                    ws = recommended.wsLocation
                                    tiles = recommended.tileServer
                                    status = "$msg\n已采用服务端推荐地址"
                                }
                            }
                        },
                        enabled = !testing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PowerRed, contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text(if (testing) "连接中…" else "测试连接", fontSize = 12.sp) }
                }

                status?.let { s ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (success) Color(0xFFE8F5E9) else Color(0xFFFDECEC),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            s,
                            color = if (success) Color(0xFF2E7D32) else Color(0xFFB71C1C),
                            fontSize = 12.sp, lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // ---------------- 高级：三个地址 ----------------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "高级 · 单独覆盖三个地址",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = PowerRed, modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { advanced = !advanced },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(30.dp)
                    ) { Text(if (advanced) "收起" else "展开", fontSize = 11.sp) }
                }
                if (advanced) {
                    ServerField("PMS 台账服务", "http://192.168.1.10:8080/", pms) { pms = it }
                    ServerField("位置共享 WebSocket", "ws://192.168.1.10:8080/ws/location", ws) { ws = it }
                    ServerField(
                        "瓦片服务（XYZ）",
                        "http://192.168.1.10:8080/tiles/{z}/{x}/{y}.png",
                        tiles
                    ) { tiles = it }
                    Text(
                        "瓦片地址可改成 NAS 上 nginx 的 8090 端口，性能更好；\n" +
                            "位置共享地址不要带 ?room=，App 会自动追加房间号。",
                        fontSize = 10.5.sp, color = HintGray, lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Divider(color = Color(0x14000000), thickness = 0.7.dp)
                Spacer(Modifier.height(10.dp))
                Text("当前生效", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
                Text(
                    "台账：${current.pmsBase}\n共享：${current.wsLocation}\n瓦片：${current.tileServer}",
                    fontSize = 10.5.sp, color = HintGray, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { vm.resetServerAddresses { msg -> status = msg; success = true } },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) { Text("恢复默认（打包时的地址）", fontSize = 12.sp) }

                Spacer(Modifier.height(10.dp))
                Text(
                    "说明：保存后立即生效，PMS 台账与瓦片无需重启 App；\n" +
                        "若位置共享正在运行，请停止后重新开启才会连到新地址。\n" +
                        "瓦片地址变化会自动重建底图，镜头位置保持不变。",
                    fontSize = 10.5.sp, color = HintGray, lineHeight = 17.sp
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ServerField(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { Text(hint, fontSize = 10.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp)
    )
}
