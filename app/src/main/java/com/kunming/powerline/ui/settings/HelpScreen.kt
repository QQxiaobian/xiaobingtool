package com.kunming.powerline.ui.settings

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.ui.search.SearchHelpDialog
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PowerRed

private data class HelpSection(val title: String, val lines: List<String>)

/**
 * App 内置使用帮助（1.5.0 起，1.6.0 更新）。
 *
 * 替代原先跳转到外部网址的「帮助文档」入口：说明随 APK 打包，现场无外网也能看；
 * 搜索语法沿用 [SearchHelpDialog]，点底部按钮弹出。
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    var showSearchHelp by remember { mutableStateOf(false) }
    val sections = remember { helpSections() }

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
                    "使用帮助", fontSize = 18.sp, fontWeight = FontWeight.Bold,
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
                    "本文档内置在 App 中，离线可查看。当前版本已移除位置共享功能，定位仅使用本机真实 GPS。",
                    fontSize = 11.sp, color = HintGray, lineHeight = 17.sp
                )
                Spacer(Modifier.height(10.dp))
                sections.forEach { HelpCard(it) }

                Button(
                    onClick = { showSearchHelp = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PowerRed, contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) { Text("查看搜索语法说明", fontSize = 13.sp) }

                Spacer(Modifier.height(10.dp))
                Text(
                    "更多部署与排障说明见交付包内 README.md 与 nas-server/README.md。",
                    fontSize = 10.5.sp, color = HintGray, lineHeight = 16.sp
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSearchHelp) SearchHelpDialog { showSearchHelp = false }
}

@Composable
private fun HelpCard(sec: HelpSection) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(sec.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PowerRed)
        Spacer(Modifier.height(6.dp))
        sec.lines.forEach { line ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("· ", fontSize = 11.5.sp, color = HintGray)
                Text(
                    line, fontSize = 11.5.sp, color = Color(0xFF333333),
                    lineHeight = 17.sp, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun helpSections(): List<HelpSection> = listOf(
    HelpSection(
        "一、快速开始",
        listOf(
            "安装后授予定位权限，App 会用本机 GPS 在地图上显示当前位置（不上传、不共享）。",
            "打包默认服务器地址为 192.168.1.12:8902；现场不一致时进「设置 → 服务器（自建 NAS）」改一次即可，无需重新打包。",
            "手机与 NAS 需在同一内网；NAS 放行对应端口，App 端已允许 HTTP 明文。"
        )
    ),
    HelpSection(
        "二、导入线路与杆塔",
        listOf(
            "「图层 → 架空线路」进入导入流程：支持 Excel 台账（可先在 App 里下载导入模板）、KML / KMZ / GeoJSON / GPX / SHP(zip)。",
            "行政区划等面状 KML 建议导入为「专题图层」，可在图层管理里改名称、透明度、线宽，或整层转成线路。",
            "导入后可编辑线路名称、电压等级、起止站、投运日期、备注，以及逐基杆塔的编号与坐标。"
        )
    ),
    HelpSection(
        "三、服务器文件与批量导入",
        listOf(
            "把 KML / KMZ / GeoJSON / GPX / SHP(zip) 直接拷进 NAS 的 files 目录，进「设置 → 服务器文件」点「刷新」即可列出。",
            "点「批量选择」（顶部或列表标题右侧均可）→ 勾选文件（整行都能点）→「批量导入为线路 / 图层」；单文件失败不影响其余。",
            "服务器导入线路时，线路名默认取文件名（去扩展名），并自动从文件名识别电压等级，如 220kV、500千伏、110_某某线。",
            "同一页还可批量删除服务器文件，或把手机里的文件上传到服务器。"
        )
    ),
    HelpSection(
        "四、图层与线路管理",
        listOf(
            "左侧图层面板可逐个控制内置图层与自定义专题图层的显示；左下角电压图例显示各电压等级的线路条数，点色块可过滤，被关闭等级的线路与杆塔点会一起隐藏。",
            "线路列表与专题图层列表都支持「批量选择」→ 勾选 → 删除选中（带二次确认），删除线路会连带删除其杆塔。",
            "「设置 → 自定义地图与数据」可添加 XYZ / TMS 自定义地图源，或新建空白专题图层。"
        )
    ),
    HelpSection(
        "五、地图操作",
        listOf(
            "顶部：搜索栏、底图切换、图层面板、绘制标注、设置；右侧工具栏：截图、定位、缩放、罗盘（线路自动播放已取消）。",
            "「定位」为单次回到当前位置，不会自动跟随抢镜头；缩小到「名称显示缩放级别」以下时，杆塔点自动隐藏，只保留线路。",
            "长按地图空白处，可把该坐标设为危险点 / 护线员 / 收藏点 / 视频监控 / 起飞点 / 交叉跨越，也可直接导航到此位置（单击地图不做任何响应）；绘制页可画点 / 线 / 面并导出 GeoJSON。"
        )
    ),
    HelpSection(
        "六、数据备份与导出",
        listOf(
            "「设置 → 数据同步」可一键导出本机全部图层、线路与杆塔：备份 JSON 可无损还原，GeoJSON 供 QGIS / Google Earth 交换。",
            "「全部导入」会自动识别备份包，或按线路 / 图层解析；也可上传数据集到 NAS、从 NAS 下载合并。",
            "建议定期导出备份 JSON 存到 NAS 或电脑，换机时直接导入即可。"
        )
    ),
    HelpSection(
        "七、关于与更新",
        listOf(
            "「设置 → 帮助与关于 → 关于应用」可查看版本号、当前服务器地址，并一键「检查更新」。",
            "检查更新直接查询 GitHub 最新 Release，与内网 NAS 是否可达无关；查出新版本可跳转下载页覆盖安装。",
            "Release 里的 APK 为 v1/v2/v3 完整签名、全系列同一证书，可直接覆盖升级，无需卸载。"
        )
    ),
    HelpSection(
        "八、常见问题",
        listOf(
            "安装失败：核对交付包内 CHECKSUMS.txt 的大小与 SHA-256（下载中断最常见），或运行 verify-apk.sh 一键校验。",
            "NAS 部署失败：飞牛 fnOS 自带镜像加速源不可达时，按 nas-server/README.md「2.5 镜像拉取失败」换源或走完全离线部署。",
            "服务器文件为空：确认 NAS 已更新服务端、/health 里可见 files 字段，且文件确实放在 data/files/ 目录。",
            "顶部按钮点不到：1.4.1 起所有全屏页面已做状态栏避让；若仍异常请确认已升级到最新版本。"
        )
    )
)
