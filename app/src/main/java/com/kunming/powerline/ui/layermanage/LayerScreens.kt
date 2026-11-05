package com.kunming.powerline.ui.layermanage

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.GeoUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

/** 新增表单浮层 */
@Composable
private fun AddSheet(
    title: String,
    canAdd: Boolean,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PowerRed)
            Spacer(Modifier.height(6.dp))
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("取消", fontSize = 12.5.sp, color = Color(0xFF8A8078), modifier = Modifier.clickable { onClose() }.padding(8.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "添加", fontSize = 12.5.sp,
                    color = if (canAdd) PowerRed else Color(0xFFC9BDB2),
                    modifier = Modifier.clickable(enabled = canAdd) { onAdd() }.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun AddBar(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .background(Color(0x14B71C1C), RoundedCornerShape(10.dp))
            .clickable { onClick() }.padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text("+ $text", fontSize = 12.5.sp, color = PowerRed, fontWeight = FontWeight.Medium) }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.5.sp, color = Color(0xFF8A8078), lineHeight = 19.sp)
    }
}

// ----------------------------------------------------------------------------
// 电缆
// ----------------------------------------------------------------------------
@Composable
fun CableManageScreen(vm: MapViewModel, onBack: () -> Unit, onToast: (String) -> Unit, onFocus: (Double, Double, Double) -> Unit) {
    val cables = vm.cables.value
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("110") }
    var lon by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }

    ManageScaffold("输电电缆图层", "共 ${cables.size} 段电缆", onBack) {
        if (adding) {
            Box(Modifier.padding(12.dp)) {
                AddSheet("新增电缆段", name.isNotBlank() && lon.toDoubleOrNull() != null && lat.toDoubleOrNull() != null, { adding = false }, {
                    vm.addCable(name, level.toIntOrNull() ?: 110, lon.toDouble(), lat.toDouble())
                    onToast("已新增电缆段「$name」")
                    adding = false; name = ""; lon = ""; lat = ""
                }) {
                    ManageField("电缆名称", name) { name = it }
                    ManageField("电压等级 kV", level, numeric = true) { level = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { ManageField("经度", lon, numeric = true) { lon = it } }
                        Box(Modifier.weight(1f)) { ManageField("纬度", lat, numeric = true) { lat = it } }
                    }
                    Text("也可在设置中按「PMS 台账」同步电缆路径", fontSize = 11.sp, color = Color(0xFF8A8078))
                }
            }
        }
        AddBar("新增电缆段", { adding = true })
        if (cables.isEmpty()) EmptyHint("暂无电缆数据") else
            LazyColumn(Modifier.weight(1f)) {
                items(cables, key = { it.id }) { c ->
                    ManageRow(
                        title = c.name,
                        subtitle = "${c.voltageLevel}kV · 长度 ${GeoUtils.formatDistance(c.lengthMeter)}",
                        trailing = "定位 ›",
                        onClick = {
                            val p = firstPointOf(c.geometryGeojson)
                            if (p != null) onFocus(p.first, p.second, 15.0) else onToast("该电缆无坐标")
                        },
                        onDelete = { vm.deleteCable(c.id); onToast("已删除电缆「${c.name}」") }
                    )
                }
            }
    }
}

private fun firstPointOf(geojson: String): Pair<Double, Double>? {
    return runCatching {
        val o = org.json.JSONObject(geojson)
        val type = o.optString("type")
        val arr: org.json.JSONArray = when (type) {
            "LineString" -> o.getJSONArray("coordinates")
            "MultiLineString" -> o.getJSONArray("coordinates").getJSONArray(0)
            "Point" -> org.json.JSONArray().put(o.getJSONArray("coordinates"))
            else -> return@runCatching null
        }
        if (arr.length() == 0) return@runCatching null
        val p = arr.getJSONArray(0)
        p.getDouble(0) to p.getDouble(1)
    }.getOrNull()
}

// ----------------------------------------------------------------------------
// 收藏点
// ----------------------------------------------------------------------------
@Composable
fun FavoriteManageScreen(vm: MapViewModel, onBack: () -> Unit, onToast: (String) -> Unit, onFocus: (Double, Double, Double) -> Unit) {
    val favs = vm.favorites.value
    var name by remember { mutableStateOf("") }
    ManageScaffold("收藏点图层", "共 ${favs.size} 个收藏点（按当前定位添加）", onBack) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ManageField("收藏点名称", name) { name = it } }
            Spacer(Modifier.width(8.dp))
            Text(
                "添加", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (name.isBlank()) Color(0xFFC9BDB2) else PowerRed,
                modifier = Modifier.clickable(enabled = name.isNotBlank()) {
                    vm.addFavoriteHere(name) { ok -> onToast(if (ok) "已收藏「$name」" else "请先点击定位获取当前位置") }
                    name = ""
                }.padding(8.dp)
            )
        }
        if (favs.isEmpty()) EmptyHint("暂无收藏点\n定位成功后输入名称即可添加") else
            LazyColumn(Modifier.weight(1f)) {
                items(favs, key = { it.id }) { f ->
                    ManageRow(
                        title = f.name,
                        subtitle = String.format("%.5f, %.5f   %s", f.lon, f.lat, f.note ?: f.category),
                        trailing = "定位 ›",
                        onClick = { onFocus(f.lon, f.lat, 17.0) },
                        onDelete = { vm.deleteFavorite(f.id); onToast("已删除「${f.name}」") }
                    )
                }
            }
    }
}

// ----------------------------------------------------------------------------
// 无人机起飞点
// ----------------------------------------------------------------------------
@Composable
fun TakeoffManageScreen(vm: MapViewModel, onBack: () -> Unit, onToast: (String) -> Unit, onFocus: (Double, Double, Double) -> Unit) {
    val pts = vm.takeoffs.value
    var name by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("") }
    ManageScaffold("无人机起飞点图层", "共 ${pts.size} 个起飞点", onBack) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ManageField("起飞点名称", name) { name = it } }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(88.dp)) { ManageField("海拔 m", alt, numeric = true) { alt = it } }
            Spacer(Modifier.width(6.dp))
            Text(
                "添加", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (name.isBlank()) Color(0xFFC9BDB2) else PowerRed,
                modifier = Modifier.clickable(enabled = name.isNotBlank()) {
                    vm.addTakeoffHere(name, alt.toDoubleOrNull()) { ok -> onToast(if (ok) "已添加起飞点「$name」" else "请先点击定位获取当前位置") }
                    name = ""; alt = ""
                }.padding(8.dp)
            )
        }
        if (pts.isEmpty()) EmptyHint("暂无起飞点") else
            LazyColumn(Modifier.weight(1f)) {
                items(pts, key = { it.id }) { p ->
                    ManageRow(
                        title = p.name,
                        subtitle = String.format("%.5f, %.5f  海拔 %s", p.lon, p.lat, p.altitude?.let { "%.0fm".format(it) } ?: "—"),
                        trailing = "定位 ›",
                        onClick = { onFocus(p.lon, p.lat, 17.0) },
                        onDelete = { vm.deleteTakeoff(p.id); onToast("已删除起飞点「${p.name}」") }
                    )
                }
            }
    }
}

// ----------------------------------------------------------------------------
// 影像（照片）
// ----------------------------------------------------------------------------
@Composable
fun PhotoManageScreen(vm: MapViewModel, onBack: () -> Unit, onToast: (String) -> Unit, onFocus: (Double, Double, Double) -> Unit) {
    val photos = vm.photos.value
    var viewer by remember { mutableStateOf<String?>(null) }
    ManageScaffold("影像图层", "共 ${photos.size} 张现场照片", onBack) {
        if (photos.isEmpty()) {
            EmptyHint("暂无现场照片\n在杆塔详情页点击「拍照」后会自动写入 EXIF 并显示在地图上")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(photos, key = { it.id }) { p ->
                    ManageRow(
                        title = p.keywords.ifBlank { "现场照片" },
                        subtitle = "${FMT.format(Date(p.takenAt))}  ${p.remark ?: ""}",
                        trailing = "查看 ›",
                        onClick = {
                            viewer = p.filePath
                            if (p.lon != null && p.lat != null) onFocus(p.lon, p.lat, 17.0)
                        },
                        onDelete = { vm.deletePhoto(p.id); onToast("已删除照片记录") }
                    )
                }
            }
        }
        viewer?.let { PhotoViewerOverlay(it) { viewer = null } }
    }
}

// ----------------------------------------------------------------------------
// 交叉跨越
// ----------------------------------------------------------------------------
@Composable
fun CrossingManageScreen(vm: MapViewModel, onBack: () -> Unit, onToast: (String) -> Unit, onFocus: (Double, Double, Double) -> Unit) {
    val list = vm.crossings.value
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("公路") }
    var lon by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var dist by remember { mutableStateOf("") }

    ManageScaffold("交叉跨越图层", "共 ${list.size} 处交叉（跨越距离规范值 ≥ 6m）", onBack) {
        if (adding) {
            Box(Modifier.padding(12.dp)) {
                AddSheet("新增交叉跨越", name.isNotBlank() && lon.toDoubleOrNull() != null && lat.toDoubleOrNull() != null, { adding = false }, {
                    val d = dist.toDoubleOrNull()
                    vm.addCrossing(name, type, lon.toDouble(), lat.toDouble(), d)
                    onToast(if (d != null && d < 6.0) "已添加（跨越距离 ${d}m < 6m，请复核）" else "已添加交叉跨越「$name」")
                    adding = false; name = ""; lon = ""; lat = ""; dist = ""
                }) {
                    ManageField("交叉对象名称", name) { name = it }
                    ManageField("跨越类型（电力线/通信线/公路/铁路/河流）", type) { type = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { ManageField("经度", lon, numeric = true) { lon = it } }
                        Box(Modifier.weight(1f)) { ManageField("纬度", lat, numeric = true) { lat = it } }
                        Box(Modifier.weight(0.8f)) { ManageField("距离 m", dist, numeric = true) { dist = it } }
                    }
                }
            }
        }
        AddBar("新增交叉跨越", { adding = true })
        if (list.isEmpty()) EmptyHint("暂无交叉跨越记录") else
            LazyColumn(Modifier.weight(1f)) {
                items(list, key = { it.id }) { c ->
                    val warn = (c.distance ?: 99.0) < 6.0
                    ManageRow(
                        title = c.crossObject ?: c.crossType,
                        subtitle = "${c.crossType} · 跨越距离 ${c.distance?.let { "%.1fm".format(it) } ?: "—"}" +
                            if (warn) " · 不满足规范" else "",
                        leadingColor = if (warn) Color(0xFFD32F2F) else Color(0xFF388E3C),
                        trailing = "定位 ›",
                        onClick = { if (c.lon != null && c.lat != null) onFocus(c.lon, c.lat, 16.0) else onToast("该记录无坐标") },
                        onDelete = { vm.deleteCrossing(c.id); onToast("已删除") }
                    )
                }
            }
    }
}

// ----------------------------------------------------------------------------
// 视频监控
// ----------------------------------------------------------------------------
@Composable
fun CameraManageScreen(vm: MapViewModel, onBack: () -> Unit, onToast: (String) -> Unit) {
    val cams = vm.cameras.value
    ManageScaffold("视频监控图层", "共 ${cams.size} 路设备", onBack) {
        if (cams.isEmpty()) EmptyHint("暂无视频监控点\n可从 PMS 台账同步或在台账页添加") else
            LazyColumn(Modifier.weight(1f)) {
                items(cams, key = { it.id }) { c ->
                    ManageRow(
                        title = c.name.ifBlank { "未命名监控" },
                        subtitle = "RTSP：${c.rtspUrl.ifBlank { "未配置" }}",
                        trailing = "播放 ›",
                        onClick = { onToast(if (c.rtspUrl.isBlank()) "该监控未配置 RTSP 地址" else "正在打开播放器：${c.name}") },
                        onDelete = { vm.deleteCamera(c.id); onToast("已删除监控") }
                    )
                }
            }
    }
}

// ----------------------------------------------------------------------------
// 护线员
// ----------------------------------------------------------------------------
@Composable
fun GuardianManageScreen(vm: MapViewModel, onBack: () -> Unit, onToast: (String) -> Unit, onFocus: (Double, Double, Double) -> Unit) {
    val list = vm.guardians.value
    ManageScaffold("护线员图层", "共 ${list.size} 位护线员", onBack) {
        if (list.isEmpty()) EmptyHint("暂无护线员数据\n可从 PMS 台账同步") else
            LazyColumn(Modifier.weight(1f)) {
                items(list, key = { it.id }) { g ->
                    ManageRow(
                        title = g.name,
                        subtitle = "责任段：${g.responsibilitySection ?: "—"}   电话：${g.phone ?: "—"}",
                        trailing = "定位 ›",
                        onClick = { if (g.lastLon != null && g.lastLat != null) onFocus(g.lastLon, g.lastLat, 15.0) else onToast("该护线员暂无上报位置") },
                        onDelete = { vm.deleteGuardian(g.id); onToast("已删除护线员「${g.name}」") }
                    )
                }
            }
    }
}

// ----------------------------------------------------------------------------
// 危险点
// ----------------------------------------------------------------------------
@Composable
fun DangerManageScreen(vm: MapViewModel, onBack: () -> Unit, onToast: (String) -> Unit, onFocus: (Double, Double, Double) -> Unit) {
    val list = vm.dangers.value
    var adding by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("Ⅱ") }
    var lon by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var measure by remember { mutableStateOf("") }

    ManageScaffold("危险点图层", "共 ${list.size} 处危险点", onBack) {
        if (adding) {
            Box(Modifier.padding(12.dp)) {
                AddSheet("新增危险点", type.isNotBlank() && lon.toDoubleOrNull() != null && lat.toDoubleOrNull() != null, { adding = false }, {
                    vm.addDanger(type, level.ifBlank { "Ⅱ" }, lon.toDouble(), lat.toDouble(), measure.ifBlank { null })
                    onToast("已添加危险点「$type」")
                    adding = false; type = ""; lon = ""; lat = ""; measure = ""
                }) {
                    ManageField("类型（施工/树障/交叉/外破）", type) { type = it }
                    ManageField("等级 Ⅰ / Ⅱ / Ⅲ", level) { level = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { ManageField("经度", lon, numeric = true) { lon = it } }
                        Box(Modifier.weight(1f)) { ManageField("纬度", lat, numeric = true) { lat = it } }
                    }
                    ManageField("防控措施", measure) { measure = it }
                }
            }
        }
        AddBar("新增危险点", { adding = true })
        if (list.isEmpty()) EmptyHint("暂无危险点") else
            LazyColumn(Modifier.weight(1f)) {
                items(list, key = { it.id }) { d ->
                    val c = when (d.level) {
                        "Ⅰ", "1" -> Color(0xFFD32F2F)
                        "Ⅱ", "2" -> Color(0xFFF57C00)
                        else -> Color(0xFFFBC02D)
                    }
                    ManageRow(
                        title = "${d.type}（${d.level} 级）",
                        subtitle = d.measure ?: "未填写防控措施",
                        leadingColor = c,
                        trailing = "定位 ›",
                        onClick = { onFocus(d.lon, d.lat, 16.0) },
                        onDelete = { vm.deleteDanger(d.id); onToast("已删除") }
                    )
                }
            }
    }
}

// ----------------------------------------------------------------------------
// 自定义图层
// ----------------------------------------------------------------------------
@Composable
fun CustomManageScreen(vm: MapViewModel, onBack: () -> Unit, onToast: (String) -> Unit, onOpenImport: () -> Unit) {
    val list = vm.overlays.value
    ManageScaffold("自定义图层", "绘制结果与导入标注，共 ${list.size} 条", onBack) {
        AddBar("从地理信息文件导入", onOpenImport)
        if (list.isEmpty()) EmptyHint("暂无自定义标注\n使用地图「绘制」工具，或导入 KML / GeoJSON") else
            LazyColumn(Modifier.weight(1f)) {
                items(list, key = { it.id }) { o ->
                    ManageRow(
                        title = o.name.ifBlank { o.type },
                        subtitle = "${o.type} · ${FMT.format(Date(o.createdAt))}",
                        onDelete = { vm.deleteOverlay(o.id); onToast("已删除标注") }
                    )
                }
            }
    }
}
