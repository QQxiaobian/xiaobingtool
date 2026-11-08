package com.kunming.powerline.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunming.powerline.data.local.entity.CustomOverlayEntity
import com.kunming.powerline.data.repository.ValidationIssue
import com.kunming.powerline.data.repository.SectionLengthResult
import com.kunming.powerline.ui.draw.DrawState
import com.kunming.powerline.ui.draw.DrawTool
import com.kunming.powerline.ui.draw.DrawToolbar
import com.kunming.powerline.ui.draw.LongPressMenu
import com.kunming.powerline.ui.draw.MapToolbar
import com.kunming.powerline.ui.layermanage.LayerManageHost
import com.kunming.powerline.ui.importui.ImportFlow
import com.kunming.powerline.ui.layer.LayerPanel
import com.kunming.powerline.ui.layer.LayerIds
import com.kunming.powerline.ui.layer.MapLayer
import com.kunming.powerline.ui.layer.RedSwitch
import com.kunming.powerline.ui.layer.VoltageLegend
import com.kunming.powerline.ui.map.BasemapPickerSheet
import com.kunming.powerline.ui.map.LayerRenderer
import com.kunming.powerline.ui.map.MapController
import com.kunming.powerline.ui.map.MapLibreView
import com.kunming.powerline.ui.map.NavigationHelper
import com.kunming.powerline.ui.map.RoundIconButton
import com.kunming.powerline.ui.map.SmallButton
import com.kunming.powerline.ui.map.TrueNorthController
import com.kunming.powerline.ui.map.startLineTour
import com.kunming.powerline.ui.search.SearchBar
import com.kunming.powerline.ui.search.SearchHelpDialog
import com.kunming.powerline.ui.search.SearchResultList
import com.kunming.powerline.ui.settings.SettingsPanel
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.ui.theme.PowerRedLight
import com.kunming.powerline.util.FileSaver
import com.kunming.powerline.util.GeoUtils
import kotlinx.coroutines.launch
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun MapScreen(vm: MapViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val controller = remember { MapController() }
    val drawState = remember { DrawState() }

    val lines by vm.lines.collectAsStateWithLifecycle()
    val towers by vm.towers.collectAsStateWithLifecycle()
    val cables by vm.cables.collectAsStateWithLifecycle()
    val stat by vm.stat.collectAsStateWithLifecycle()
    val layerState by vm.layerState.collectAsStateWithLifecycle()
    val basemap by vm.basemap.collectAsStateWithLifecycle()
    val textSize by vm.textSize.collectAsStateWithLifecycle()
    val labelZoom by vm.labelZoom.collectAsStateWithLifecycle()
    val voltageFilter by vm.voltageFilter.collectAsStateWithLifecycle()
    val hits by vm.searchHits.collectAsStateWithLifecycle()
    val target by vm.searchTarget.collectAsStateWithLifecycle()
    val myLoc by vm.myLocation.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val followMode by vm.followMode.collectAsStateWithLifecycle()
    val heading by vm.heading.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val layerVersion by vm.layerVersion.collectAsStateWithLifecycle()
    val selectedTower by vm.selectedTower.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val takeoffs by vm.takeoffs.collectAsStateWithLifecycle()
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    val guardians by vm.guardians.collectAsStateWithLifecycle()
    val dangers by vm.dangers.collectAsStateWithLifecycle()
    val crossings by vm.crossings.collectAsStateWithLifecycle()
    val overlays by vm.overlays.collectAsStateWithLifecycle()
    val thematicLayers by vm.thematicLayers.collectAsStateWithLifecycle()
    val thematicFeatures by vm.thematicFeatures.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val shareConnected by vm.shareConnected.collectAsStateWithLifecycle()
    val server by vm.serverAddresses.collectAsStateWithLifecycle()
    val customSources by vm.customSourceSpecs.collectAsStateWithLifecycle()
    val mockState by vm.mockState.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showBasemap by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var drawMode by remember { mutableStateOf(false) }
    var drawTool by remember { mutableStateOf(DrawTool.LINE) }
    var longPressTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showLongMenu by remember { mutableStateOf(false) }
    var navInfo by remember { mutableStateOf<String?>(null) }
    var showResults by remember { mutableStateOf(false) }
    var showTowerSheet by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var trueNorth by remember { mutableStateOf(false) }
    var validationIssues by remember { mutableStateOf<List<ValidationIssue>>(emptyList()) }
    var sectionResult by remember { mutableStateOf<SectionLengthResult?>(null) }
    var syncVersion by remember { mutableStateOf(0) }
    var roomIdState by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var managerTarget by remember { mutableStateOf<MapLayer?>(null) }
    var showMapData by remember { mutableStateOf(false) }
    var showServerSettings by remember { mutableStateOf(false) }
    var showCustomMapSources by remember { mutableStateOf(false) }
    var showDataSync by remember { mutableStateOf(false) }
    var showServerFiles by remember { mutableStateOf(false) }
    var showMockLocation by remember { mutableStateOf(false) }
    // 在地图上点选「App 内模拟定位」坐标的临时模式
    var pickingMockLocation by remember { mutableStateOf(false) }
    // 点击专题区域/点位后弹出的信息卡
    var thematicSheet by remember { mutableStateOf<com.kunming.powerline.data.local.entity.ThematicFeatureEntity?>(null) }

    // 拍照：待写入的照片文件
    var pendingPhoto by remember { mutableStateOf<java.io.File?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val f = pendingPhoto
        pendingPhoto = null
        if (result.resultCode == android.app.Activity.RESULT_OK && f != null && f.exists()) {
            val t = selectedTower
            val loc = myLoc
            val ok = com.kunming.powerline.util.PhotoCapture.writeExif(
                f, loc?.lon, loc?.lat, loc?.altitude,
                title = t?.towerNumber?.let { "杆塔 $it" } ?: "现场照片",
                description = t?.let { lines.firstOrNull { l -> l.id == it.lineId }?.name }
            )
            vm.addPhotoRecord(f, t?.id, loc?.lon, loc?.lat) {
                navInfo = if (ok) "照片已保存并关联${t?.let { "杆塔 ${it.towerNumber}" } ?: "当前位置"}" else "照片已保存（Exif 写入失败）"
            }
        }
    }
    var toastText by remember { mutableStateOf<String?>(null) }

    // 返回键逐级关闭浮层，避免误退出
    androidx.activity.compose.BackHandler(
        enabled = showMapData || showServerSettings || showCustomMapSources || showDataSync ||
            showServerFiles || showMockLocation || pickingMockLocation || managerTarget != null || showImport || showSettings ||
            showLayers || showBasemap || showResults || showTowerSheet || drawMode
    ) {
        when {
            pickingMockLocation -> pickingMockLocation = false
            showCustomMapSources -> showCustomMapSources = false
            showDataSync -> showDataSync = false
            showServerFiles -> showServerFiles = false
            showMockLocation -> showMockLocation = false
            showServerSettings -> showServerSettings = false
            showMapData -> showMapData = false
            managerTarget != null -> managerTarget = null
            showImport -> showImport = false
            showSettings -> showSettings = false
            showTowerSheet -> { showTowerSheet = false; vm.clearSelectedTower() }
            showResults -> { showResults = false; vm.clearSearch() }
            drawMode -> { drawMode = false; drawState.clear(); controller.setFeatures(LayerIds.SRC_DRAW, emptyList()) }
            showBasemap -> showBasemap = false
            showLayers -> showLayers = false
        }
    }

    // 同步数据 -> 地图
    LaunchedEffect(layerVersion, controller.ready, textSize, labelZoom, voltageFilter, selectedTower?.id) {
        if (controller.ready) {
            LayerRenderer.renderAll(
                ctrl = controller,
                lines = lines, towers = towers, cables = cables,
                favorites = favorites, takeoffs = takeoffs,
                photos = photos, cameras = cameras, guardians = guardians,
                dangers = dangers, crossings = crossings,
                overlays = overlays,
                thematicLayers = thematicLayers,
                thematicFeatures = thematicFeatures,
                layerOn = { l -> layerState[l.id] ?: l.defaultOn },
                voltageFilter = voltageFilter,
                textSize = textSize,
                selectedTowerId = selectedTower?.id,
                labelZoom = labelZoom
            )
        }
    }

    // 真北 / 磁北罗盘跟随：地图方位 = -罗盘方位
    LaunchedEffect(trueNorth, followMode, controller.ready) {
        if (!trueNorth || followMode || !controller.ready) return@LaunchedEffect
        vm.compass.azimuthFlow(
            trueNorth = { trueNorth },
            location = { myLoc?.let { it.lon to it.lat } }
        ).collect { az ->
            if (trueNorth) controller.rotateTo(-az.toDouble())
        }
    }

    // ---------------- 实时定位 ----------------
    // 打开软件即请求权限并启动持续定位；有权限则直接开始
    val requestLocationPermission = com.kunming.powerline.ui.common.rememberLocationPermissionRequest(
        onGranted = { vm.startTracking() },
        onDenied = { navInfo = "未授予定位权限，无法显示当前位置" }
    )
    LaunchedEffect(Unit) {
        if (com.kunming.powerline.ui.common.PermissionHelper.hasLocation(context)) {
            vm.startTracking()
        } else {
            requestLocationPermission()
        }
    }

    // 位置 / 方向变化 -> 更新蓝点、精度圈与方向箭头
    LaunchedEffect(myLoc, heading, controller.ready) {
        val loc = myLoc ?: return@LaunchedEffect
        if (!controller.ready) return@LaunchedEffect
        LayerRenderer.renderMyLocation(controller, loc.lon, loc.lat, loc.accuracy, heading)
    }

    // 跟随模式下，手机转向时地图跟着转（正上方 = 手机朝向）
    LaunchedEffect(heading, followMode, controller.ready) {
        if (!followMode || !controller.ready) return@LaunchedEffect
        val target = -heading.toDouble()
        if (Math.abs(((target - controller.currentBearing + 540.0) % 360.0) - 180.0) >= 3.0) {
            controller.rotateTo(target)
        }
    }

    // 用户手势拖动/缩放地图 -> 静默退出跟随，不弹提示（避免每次打开都打扰操作）
    LaunchedEffect(controller) {
        controller.onUserGesture = {
            if (vm.followMode.value) {
                vm.setFollowMode(false)
            }
        }
    }

    // 首次定位成功后自动居中；跟随模式下持续跟随位置与朝向
    var autoCentered by remember { mutableStateOf(false) }
    LaunchedEffect(myLoc, followMode, controller.ready) {
        val loc = myLoc ?: return@LaunchedEffect
        if (!controller.ready) return@LaunchedEffect
        when {
            !autoCentered -> {
                autoCentered = true
                // 首次定位只居中到当前位置，不进入跟随模式：
                // 过去这里会自动开启跟随，用户一拖地图就退出并弹出「已退出跟随」，
                // 每次打开 App 都会被打断，巡检时反而碍事。
                controller.easeTo(loc.lon, loc.lat, 16.0)
            }
            followMode -> {
                // 跟随：地图中心 = 当前位置，正上方 = 手机朝向
                // 用 moveCamera 而非动画，避免每 2 秒一次的定位回调造成画面抖动；
                // 缩放交给用户（这里不改 zoom，避免用户缩小后被强制拉回）
                controller.moveTo(loc.lon, loc.lat, null, animate = false)
                controller.rotateTo(-heading.toDouble())
            }
        }
    }

    // 搜索结果落到地图
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        if (t.lon == 0.0 && t.lat == 0.0) return@LaunchedEffect
        controller.easeTo(t.lon, t.lat, t.zoom)
        controller.setFeatures(
            LayerIds.SRC_SEARCH,
            listOf(Feature.fromGeometry(Point.fromLngLat(t.lon, t.lat)))
        )
        showResults = false
    }

    // 导航连线
    LaunchedEffect(myLoc) {
        val loc = myLoc
        val t = longPressTarget
        if (loc != null && t != null) {
            controller.setFeatures(
                LayerIds.SRC_ROUTE,
                listOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(
                            listOf(Point.fromLngLat(loc.lon, loc.lat), Point.fromLngLat(t.second, t.first))
                        )
                    )
                )
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFFBF6EE))) {
        MapLibreView(
            controller = controller,
            initialBasemap = basemap,
            serverTileBase = server.tileServer,
            customSources = customSources,
            onTap = { lat, lon, x, y ->
                if (pickingMockLocation) {
                    // 点图选点：只写入本 App 的模拟坐标，不写入系统位置、不影响其他应用
                    vm.setMockLocation(true, lon, lat) { msg -> toastText = msg }
                    pickingMockLocation = false
                } else {
                // 手动操作地图 -> 退出跟随
                if (followMode) vm.setFollowMode(false)
                if (drawMode) {
                    // 检查是否点中杆塔
                    val feats = controller.queryFeatures(x, y, arrayOf(LayerIds.LYR_TOWER))
                    if (feats.isNotEmpty() && !drawState.paused) {
                        val id = feats.first().getStringProperty("fid")
                        vm.selectTower(towers.firstOrNull { it.id == id })
                        showTowerSheet = id != null
                    } else {
                        drawState.add(lon, lat)
                        controller.setFeatures(LayerIds.SRC_DRAW, drawState.previewFeatures())
                        syncVersion++
                    }
                } else {
                    val feats = controller.queryFeatures(
                        x, y,
                        arrayOf(LayerIds.LYR_TOWER, LayerIds.LYR_CAMERA, LayerIds.LYR_FAVORITE,
                            LayerIds.LYR_TAKEOFF, LayerIds.LYR_DANGER, LayerIds.LYR_GUARDIAN,
                            LayerIds.LYR_LINE, LayerIds.LYR_CABLE,
                            // 专题图层：点 / 线 / 面都可点，用来查看名称与 KML 备注
                            LayerIds.LYR_THEMATIC_POINT, LayerIds.LYR_THEMATIC_LINE,
                            LayerIds.LYR_THEMATIC_FILL)
                    )
                    if (feats.isNotEmpty()) {
                        // 专题面可能铺满整屏，优先响应杆塔、点位等其他要素
                        val thematicIds = thematicFeatures.mapTo(HashSet()) { it.id }
                        val f = feats.firstOrNull { feat ->
                            val id = feat.getStringProperty("fid")
                            id == null || id !in thematicIds
                        } ?: feats.first()
                        val fid = f.getStringProperty("fid")
                        when {
                            fid != null && towers.any { it.id == fid } -> {
                                vm.selectTower(towers.firstOrNull { it.id == fid })
                                showTowerSheet = true
                            }
                            fid != null && cameras.any { it.id == fid } -> {
                                val cam = cameras.first { it.id == fid }
                                navInfo = "视频监控：${cam.name}\nRTSP：${cam.rtspUrl.ifBlank { "未配置" }}"
                            }
                            fid != null && favorites.any { it.id == fid } -> {
                                val fav = favorites.first { it.id == fid }
                                navInfo = "收藏点：${fav.name}\n${fav.note ?: ""}"
                            }
                            fid != null && takeoffs.any { it.id == fid } -> {
                                val t = takeoffs.first { it.id == fid }
                                navInfo = "起飞点：${t.name}\n海拔 ${t.altitude ?: "—"}m\n${t.note ?: ""}"
                            }
                            fid != null && dangers.any { it.id == fid } -> {
                                val d = dangers.first { it.id == fid }
                                navInfo = "危险点：${d.type}（${d.level}级）\n措施：${d.measure ?: "—"}"
                            }
                            fid != null && guardians.any { it.id == fid } -> {
                                val g = guardians.first { it.id == fid }
                                navInfo = "护线员：${g.name}\n责任段：${g.responsibilitySection ?: "—"}\n电话：${g.phone ?: "—"}"
                            }
                            fid != null && thematicFeatures.any { it.id == fid } -> {
                                // 区域 / 点位 / 线：弹信息卡，展示名称与 KML 备注
                                thematicSheet = thematicFeatures.first { it.id == fid }
                            }
                        }
                    }
                }
                } // end else（非选点模式）
            },
            onLongPress = { lat, lon, _, _ ->
                if (followMode) vm.setFollowMode(false)
                if (!drawMode) {
                    longPressTarget = lon to lat
                    showLongMenu = true
                }
            }
        )

        // ---------------- 顶部区域 ----------------
        Column(Modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { q ->
                        vm.search(q)
                        showResults = true
                    },
                    onHelpClick = { showHelp = true },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Default.Map, "底图") { showBasemap = !showBasemap }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Default.Layers, "图层") { showLayers = !showLayers }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Default.Draw, "绘制") {
                    drawMode = !drawMode
                    if (!drawMode) {
                        drawState.clear()
                        controller.setFeatures(LayerIds.SRC_DRAW, emptyList())
                    }
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Default.Settings, "设置") { showSettings = !showSettings }
                Spacer(Modifier.weight(1f))
            }
        }

        // ---------------- 底图选择 ----------------
        AnimatedVisibility(showBasemap, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 90.dp, end = 10.dp)) {
            BasemapPickerSheet(
                current = basemap,
                onPick = { vm.setBasemap(it); showBasemap = false },
                onDismiss = { showBasemap = false }
            )
        }

        // ---------------- 左侧图层面板 ----------------
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight()) {
            LayerPanel(
                visible = showLayers,
                layerState = layerState,
                thematicLayers = thematicLayers,
                onToggle = { vm.toggleLayer(it) },
                onToggleThematic = { id, on -> vm.setThematicVisible(id, on) },
                onManage = { l ->
                showLayers = false
                if (l == MapLayer.OVERHEAD_LINE) {
                    showImport = true
                } else {
                    managerTarget = l
                }
            }, onDismiss = { showLayers = false })
        }

        // ---------------- 右侧工具栏 ----------------
        MapToolbar(
            bearing = controller.currentBearing,
            playing = playing,
            onPlayToggle = {
                playing = !playing
                if (playing) startLineTour(scope, controller, lines.firstOrNull()?.id, towers) { playing = false }
            },
            onCamera = {
                controller.snapshot { bmp ->
                    if (bmp != null) {
                        val saved = FileSaver.saveSnapshot(context, bmp)
                        navInfo = if (saved != null) "截图已保存到 Pictures/输电巡检" else "截图保存失败"
                    }
                }
            },
            onLocate = {
                // 定位按钮 = 单次回到当前位置。
                // 跟随模式已取消（打开就跟随会抢镜头，一拖动又弹「已退出跟随」）。
                vm.setFollowMode(false)
                val loc = myLoc
                if (loc != null) {
                    controller.easeTo(loc.lon, loc.lat, 16.0)
                    controller.rotateTo(-heading.toDouble())
                } else {
                    vm.locateOnce { fix -> controller.easeTo(fix.lon, fix.lat, 16.0) }
                }
            },
            onZoomIn = { controller.zoomIn() },
            onZoomOut = { controller.zoomOut() },
            onCompass = {
                controller.resetNorth()
            },
            following = followMode,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
        )

        // ---------------- 左下角图例 + 统计 ----------------
        Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 10.dp)) {
            VoltageLegend(
                visible = true,
                filter = voltageFilter,
                onToggle = { vm.toggleVoltage(it) }
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .background(Color(0xE6FFFDF8), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text("${stat.first} 条线路 / ${stat.second} 条电缆", color = Color(0xFF2B2B2B), fontSize = 11.sp)
            }
        }

        // ---------------- 绘制工具栏 ----------------
        AnimatedVisibility(drawMode, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 110.dp, start = 8.dp, end = 8.dp)) {
            DrawToolbar(
                tool = drawTool,
                onToolChange = {
                    drawTool = it
                    drawState.tool = it
                    drawState.clear()
                    controller.setFeatures(LayerIds.SRC_DRAW, emptyList())
                },
                vertexCount = drawState.points.size,
                measureText = drawState.measureText(),
                onUndo = {
                    drawState.undo()
                    controller.setFeatures(LayerIds.SRC_DRAW, drawState.previewFeatures())
                    syncVersion++
                },
                onDelete = {
                    drawState.clear()
                    controller.setFeatures(LayerIds.SRC_DRAW, emptyList())
                },
                onClose = {
                    drawMode = false
                    drawState.clear()
                    controller.setFeatures(LayerIds.SRC_DRAW, emptyList())
                },
                onSave = {
                    if (drawTool == DrawTool.TEXT) drawState.textContent = drawState.textContent.ifBlank { "文字标注" }
                    val o = drawState.toOverlay()
                    if (o != null) {
                        vm.addOverlay(o)
                        drawState.clear()
                        controller.setFeatures(LayerIds.SRC_DRAW, emptyList())
                    } else {
                        vm.postMessage("请先在地图上点击至少 ${if (drawTool.isArea) 2 else 2} 个点")
                    }
                },
                onPauseToggle = { drawState.paused = !drawState.paused },
                onMore = { vm.postMessage(drawState.measureText().ifBlank { "更多：可设置字号 / 颜色" }) }
            )
        }

        // ---------------- 搜索结果 ----------------
        if (hits.isNotEmpty() && showResults) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 4.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFBF6EE), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("搜索结果（${hits.size}）", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2B2B2B))
                        Spacer(Modifier.weight(1f))
                        Text("关闭", fontSize = 12.sp, color = PowerRed, modifier = Modifier.clickable {
                            showResults = false
                            vm.clearSearch()
                        })
                    }
                    SearchResultList(hits = hits, onPick = { vm.focusHit(it) })
                }
            }
        }

        // ---------------- 杆塔详情 ----------------
        if (showTowerSheet && selectedTower != null) {
            val t = selectedTower!!
            val ln = lines.firstOrNull { it.id == t.lineId }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 10.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFBF6EE), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${ln?.name ?: ""} ${t.towerNumber}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2B2B2B))
                        Spacer(Modifier.weight(1f))
                        Text("关闭", fontSize = 12.sp, color = PowerRed, modifier = Modifier.clickable {
                            showTowerSheet = false
                            vm.clearSelectedTower()
                        })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "电压：${ln?.let { com.kunming.powerline.util.VoltagePalette.labelOf(it.voltageLevel) } ?: "—"}" +
                            "   回路：${t.circuitCount}    类型：${t.type ?: "—"}",
                        fontSize = 12.sp, color = Color(0xFF444444)
                    )
                    Text(
                        String.format("坐标：%.6f, %.6f   海拔：%s", t.lon, t.lat, t.elevation?.let { "%.1fm".format(it) } ?: "—"),
                        fontSize = 12.sp, color = Color(0xFF444444)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("导航") {
                            NavigationHelper.open(context, NavigationHelper.NavTarget("${ln?.name} ${t.towerNumber}", t.lon, t.lat))
                        }
                        SmallButton("设为导航目标") {
                            longPressTarget = t.lon to t.lat
                            vm.postMessage("已设为导航目标")
                        }
                        SmallButton("拍照") {
                            val f = com.kunming.powerline.util.PhotoCapture.newPhotoFile(context)
                            pendingPhoto = f
                            runCatching {
                                cameraLauncher.launch(com.kunming.powerline.util.PhotoCapture.buildIntent(context, f))
                            }.onFailure { navInfo = "无法启动相机：${it.message}" }
                        }
                        SmallButton("关闭") { showTowerSheet = false; vm.clearSelectedTower() }
                    }
                }
            }
        }

        // ---------------- 长按菜单 ----------------
        LongPressMenu(
            visible = showLongMenu,
            onDismiss = { showLongMenu = false },
            onNavigate = {
                longPressTarget?.let { (lon, lat) ->
                    NavigationHelper.open(context, NavigationHelper.NavTarget("目标点", lon, lat))
                }
            },
            onCompassToggle = { navInfo = "罗盘显示已切换" },
            onResetNorth = { controller.resetNorth() },
            onRouteToggle = {
                val loc = myLoc
                val t = longPressTarget
                if (loc != null && t != null) {
                    val d = GeoUtils.distance(loc.lon, loc.lat, t.first, t.second)
                    navInfo = "直线距离 ${GeoUtils.formatDistance(d)}"
                    controller.setFeatures(
                        LayerIds.SRC_ROUTE,
                        listOf(Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(loc.lon, loc.lat), Point.fromLngLat(t.first, t.second)))))
                    )
                } else {
                    vm.postMessage("请先定位获取当前位置")
                }
            },
            onCalibrate = {
                navInfo = "已将目标点设为当前位置（校准）"
            }
        )

        // ---------------- 设置 ----------------
        AnimatedVisibility(showSettings, modifier = Modifier.fillMaxSize()) {
            SettingsPanel(
                roomId = roomIdState,
                onRoomIdChange = { roomIdState = it },
                sharing = sharing,
                shareConnected = shareConnected,
                peerCount = peers.size,
                onStartShare = { vm.startSharing(roomIdState); sharing = true },
                onStopShare = { vm.stopSharing(); sharing = false },
                textSize = textSize,
                onTextSizeChange = { vm.setTextSize(it) },
                labelZoom = labelZoom,
                onLabelZoomChange = { vm.setLabelZoom(it) },
                trueNorth = trueNorth,
                onTrueNorthChange = { v: Boolean -> trueNorth = v; TrueNorthController.enabled = v },
                onValidateLedger = {
                    scope.launch { validationIssues = vm.validateLedger() }
                },
                validationIssues = validationIssues,
                onOpenHelp = {
                    val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(com.kunming.powerline.BuildConfig.HELP_DOC_URL))
                    try { context.startActivity(i) } catch (_: Throwable) {}
                },
                onOpenMapDataSettings = { showSettings = false; showMapData = true },
                onOpenServerSettings = { showSettings = false; showServerSettings = true },
                onOpenCustomMapSources = { showSettings = false; showCustomMapSources = true },
                onOpenDataSync = { showSettings = false; showDataSync = true },
                onOpenServerFiles = { showSettings = false; showServerFiles = true },
                onOpenMockLocation = { showSettings = false; showMockLocation = true },
                onOpenGroupSettings = { navInfo = "地图组设置：按区域 / 单位分组" },
                onBack = { showSettings = false },
                sectionResult = sectionResult,
                onCalcSection = { n, f, t ->
                    scope.launch { sectionResult = vm.calcSection(n, f, t) }
                },
                onExportOverlays = { vm.postMessage("导出 GeoJSON 请使用「标注导出」入口") }
            )
        }

        // ---------------- Busy ----------------
        if (busy != null) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.background(Color(0xF5FFFDF8), RoundedCornerShape(14.dp)).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = PowerRedLight, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(busy!!, color = Color(0xFF2B2B2B), fontSize = 13.sp)
                }
            }
        }

        // ---------------- 模拟定位标记（明确区分真实 / 演示位置） ----------------
        if (mockState.valid) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .background(Color(0xFFFFF3E0), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "模拟定位（仅本 App）· 非真实位置",
                    color = Color(0xFF8A4B00), fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ---------------- 地图点选模拟位置（仅本 App） ----------------
        if (pickingMockLocation) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (mockState.valid) 44.dp else 8.dp)
                    .background(Color(0xFFFFF3E0), RoundedCornerShape(14.dp))
                    .clickable { pickingMockLocation = false }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "点击地图选择模拟位置 · 点此取消",
                    color = Color(0xFF8A4B00), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ---------------- 提示 ----------------
        if (navInfo != null) {
            Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    navInfo!!,
                    color = Color(0xFFB71C1C), fontSize = 12.5.sp,
                    modifier = Modifier
                        .background(Color(0xF2FFFDF8), RoundedCornerShape(10.dp))
                        .clickable { navInfo = null }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }

        // ---------------- 专题要素信息卡（点击区域查看名称与 KML 备注）----------------
        thematicSheet?.let { f ->
            com.kunming.powerline.ui.map.ThematicInfoDialog(
                feature = f,
                layer = thematicLayers.firstOrNull { it.id == f.layerId },
                onDismiss = { thematicSheet = null },
                onFocus = {
                    f.lon?.let { lon ->
                        f.lat?.let { lat -> controller.easeTo(lon, lat, 15.0) }
                    }
                },
                onConvertToLine = if (f.kind == "line") {
                    { vm.convertThematicFeatureToLine(f.id, 110) }
                } else null
            )
        }

        if (showHelp) SearchHelpDialog { showHelp = false }

        // ---------------- 创建/导入线路 ----------------
        if (showImport) {
            Box(Modifier.fillMaxSize().background(Color(0xFFFBF6EE))) {
                ImportFlow(
                    vm = vm,
                    onExit = { showImport = false },
                    onToast = { toastText = it },
                    onFocus = { lon, lat, z ->
                        // 关闭导入流程回到地图并定位
                        showImport = false
                        controller.easeTo(lon, lat, z)
                    }
                )
            }
        }

        // ---------------- 服务器地址设置（运行时可改） ----------------
        if (showServerSettings) {
            Box(Modifier.fillMaxSize()) {
                com.kunming.powerline.ui.settings.ServerSettingsScreen(
                    vm = vm,
                    onBack = { showServerSettings = false }
                )
            }
        }

        // ---------------- 自定义地图源 / 数据同步 / 模拟定位（1.2.0） ----------------
        if (showCustomMapSources) {
            Box(Modifier.fillMaxSize()) {
                com.kunming.powerline.ui.settings.CustomMapSourceScreen(
                    vm = vm,
                    onBack = { showCustomMapSources = false }
                )
            }
        }
        if (showDataSync) {
            Box(Modifier.fillMaxSize()) {
                com.kunming.powerline.ui.settings.DataSyncScreen(
                    vm = vm,
                    onBack = { showDataSync = false }
                )
            }
        }
        if (showServerFiles) {
            Box(Modifier.fillMaxSize()) {
                com.kunming.powerline.ui.settings.ServerFilesScreen(
                    vm = vm,
                    onBack = { showServerFiles = false }
                )
            }
        }
        if (showMockLocation) {
            Box(Modifier.fillMaxSize()) {
                com.kunming.powerline.ui.settings.MockLocationScreen(
                    vm = vm,
                    onBack = { showMockLocation = false },
                    onPickOnMap = { showMockLocation = false; pickingMockLocation = true }
                )
            }
        }

        // ---------------- 基础地图数据设置 ----------------
        if (showMapData) {
            Box(Modifier.fillMaxSize()) {
                com.kunming.powerline.ui.settings.MapDataSettingsScreen(
                    store = vm.offlineStore,
                    downloader = vm.tileDownloader,
                    onBack = { showMapData = false },
                    onToast = { toastText = it }
                )
            }
        }

        // ---------------- 各图层的管理页 ----------------
        managerTarget?.let { layer ->
            Box(Modifier.fillMaxSize()) {
                LayerManageHost(
                    layer = layer,
                    vm = vm,
                    onClose = { managerTarget = null },
                    onToast = { toastText = it },
                    onFocus = { lon, lat, z -> controller.easeTo(lon, lat, z) },
                    onOpenImport = { managerTarget = null; showImport = true }
                )
            }
        }

        // ---------------- 通用轻提示 ----------------
        if (toastText != null) {
            LaunchedEffect(toastText) {
                kotlinx.coroutines.delay(2600)
                toastText = null
            }
            Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    toastText!!,
                    color = Color(0xFF2B2B2B), fontSize = 12.5.sp,
                    modifier = Modifier
                        .background(Color(0xF2FFFDF8), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
}


