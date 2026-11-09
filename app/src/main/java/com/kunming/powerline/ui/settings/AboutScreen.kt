package com.kunming.powerline.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunming.powerline.BuildConfig
import com.kunming.powerline.data.update.UpdateChecker
import com.kunming.powerline.data.update.UpdateInfo
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PowerRed
import kotlinx.coroutines.launch

/**
 * 关于应用 + 检查更新（1.5.0）。
 *
 * 检查更新走 GitHub Releases API，与内网 NAS 无关；网络不可用时给出失败原因
 * 并保留「手动打开下载页」入口。
 */
@Composable
fun AboutScreen(
    vm: MapViewModel,
    onBack: () -> Unit,
    onOpenHelp: () -> Unit
) {
    val context = LocalContext.current
    val server by vm.serverAddresses.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result<UpdateInfo>?>(null) }

    LightPanelTheme {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFFFBF6EE))
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "返回", tint = Color(0xFF333333))
                }
                Text(
                    "关于应用", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF2B2B2B), modifier = Modifier.weight(1f)
                )
            }
            Divider(color = Color(0x14000000), thickness = 0.8.dp)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // ---------- 品牌区（1.6.3 猫头塔图标） ----------
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            com.kunming.powerline.R.drawable.ic_brand
                        ),
                        contentDescription = "应用图标",
                        modifier = Modifier.size(86.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "输电线路工具箱", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF2B2B2B)
                    )
                    Text(
                        "猫头塔 · 输电线路巡检助手", fontSize = 11.sp, color = HintGray,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                // ---------- 应用信息 ----------
                InfoCard("应用信息") {
                    InfoLine("名称", context.getString(com.kunming.powerline.R.string.app_name))
                    InfoLine("版本", "${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）")
                    InfoLine("包名", BuildConfig.APPLICATION_ID)
                    InfoLine("构建类型", BuildConfig.BUILD_TYPE)
                    InfoLine("编译 SDK", "Android 14（API 34）")
                    InfoLine("最低系统", "Android 8.0（API 26）")
                }

                InfoCard("当前服务器") {
                    InfoLine("台账 REST", server.pmsBase)
                    InfoLine("瓦片", server.tileServer)
                    Text(
                        "在「设置 → 服务器（自建 NAS）」里可随时修改，无需重新打包。",
                        fontSize = 10.5.sp, color = HintGray, lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                // ---------- 检查更新 ----------
                InfoCard("检查更新") {
                    Text(
                        "联网查询 GitHub 上的最新 Release，与本机版本比对。",
                        fontSize = 11.sp, color = HintGray, lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            checking = true
                            result = null
                            scope.launch {
                                result = UpdateChecker.check(BuildConfig.VERSION_NAME)
                                checking = false
                            }
                        },
                        enabled = !checking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PowerRed, contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Text(if (checking) "正在检查…" else "检查更新", fontSize = 13.sp)
                    }

                    result?.let { r ->
                        Spacer(Modifier.height(12.dp))
                        // 注意：Result.fold 的 lambda 不是 Composable，不能直接在其内部调用
                        // Composable，这里用 getOrNull / exceptionOrNull 在本 Composable 上下文分支。
                        val info = r.getOrNull()
                        val err = r.exceptionOrNull()
                        when {
                            info != null -> UpdateResultOk(context, info)
                            err != null -> UpdateResultError(context, err.message ?: "未知错误")
                        }
                    }
                }

                // ---------- 其它入口 ----------
                InfoCard("更多") {
                    LinkRow("使用帮助（离线）") { onOpenHelp() }
                    LinkRow("项目主页（GitHub）") { openUrl(context, BuildConfig.PROJECT_URL) }
                    LinkRow("版本发布页 / 手动下载") { openUrl(context, BuildConfig.RELEASES_URL) }
                }

                Text(
                    "输电线路工具箱\n" +
                        "本应用仅在内网使用台账与地图数据；定位只在本机使用，不上传、不共享。",
                    fontSize = 10.5.sp, color = HintGray, lineHeight = 17.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun UpdateResultOk(context: Context, info: UpdateInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (info.hasUpdate) Color(0xFFFFF3E0) else Color(0xFFE8F5E9),
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            if (info.hasUpdate) "发现新版本 ${info.latestVersion}" else "已是最新版本 ${info.latestVersion}",
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (info.hasUpdate) Color(0xFFE65100) else Color(0xFF2E7D32)
        )
        if (info.releaseName.isNotBlank() && info.releaseName != info.latestVersion) {
            Text(info.releaseName, fontSize = 11.5.sp, color = Color(0xFF444444),
                modifier = Modifier.padding(top = 4.dp))
        }
        if (info.hasUpdate && info.releaseNotes.isNotBlank()) {
            Text(info.releaseNotes.take(300), fontSize = 11.sp, color = Color(0xFF555555),
                lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (info.hasUpdate) "打开下载页获取新版 APK" else "查看版本发布页",
            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PowerRed,
            modifier = Modifier.clickable {
                openUrl(context, if (info.hasUpdate) (info.apkDownloadUrl ?: info.htmlUrl) else info.htmlUrl)
            }.padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun UpdateResultError(context: Context, message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFFDECEC), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text("检查失败：$message", fontSize = 12.sp, color = Color(0xFFB71C1C), lineHeight = 17.sp)
        Text(
            "可能是网络不通或 GitHub 访问受限，可手动打开版本发布页。",
            fontSize = 10.5.sp, color = HintGray, lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "手动打开下载页", fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = PowerRed,
            modifier = Modifier.clickable { openUrl(context, BuildConfig.RELEASES_URL) }
                .padding(vertical = 6.dp)
        )
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, fontSize = 11.5.sp, color = HintGray, modifier = Modifier.weight(0.32f))
        Text(value, fontSize = 11.5.sp, color = Color(0xFF333333),
            lineHeight = 16.sp, modifier = Modifier.weight(0.68f))
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.5.sp, color = PowerRed, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 9.dp)
    )
    Divider(color = Color(0x14000000), thickness = 0.6.dp)
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) {
    }
}
