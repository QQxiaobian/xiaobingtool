package com.kunming.powerline.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kunming.powerline.data.local.AppDatabase
import com.kunming.powerline.data.local.entity.FavoriteEntity
import com.kunming.powerline.data.local.entity.ImportBatchEntity
import com.kunming.powerline.data.local.entity.CableEntity
import com.kunming.powerline.data.local.entity.CameraEntity
import com.kunming.powerline.data.local.entity.CrossingEntity
import com.kunming.powerline.data.local.entity.DangerPointEntity
import com.kunming.powerline.data.local.entity.GuardianEntity
import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.data.local.entity.TakeoffPointEntity
import com.kunming.powerline.data.local.entity.TowerEntity
import com.kunming.powerline.data.location.AmapLocationClient
import com.kunming.powerline.data.location.FixLocation
import com.kunming.powerline.data.location.LocationShareSocket
import com.kunming.powerline.data.parser.ImportLine
import com.kunming.powerline.data.parser.ParseResult
import com.kunming.powerline.data.prefs.SettingsStore
import com.kunming.powerline.data.repository.BatchRepository
import com.kunming.powerline.data.repository.LineRepository
import com.kunming.powerline.data.repository.PointRepository
import com.kunming.powerline.data.repository.ValidationIssue
import com.kunming.powerline.service.LocationSharingService
import com.kunming.powerline.ui.layer.MapLayer
import com.kunming.powerline.ui.map.BasemapType
import com.kunming.powerline.util.GeoUtils
import com.kunming.powerline.util.VoltagePalette
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.geojson.Point
import javax.inject.Inject

/**
 * 搜索结果
 */
data class SearchHit(
    val title: String,
    val subtitle: String,
    val lon: Double,
    val lat: Double,
    val kind: String,
    val refId: String? = null,
    val zoom: Double = 16.0
)

/**
 * 主界面 ViewModel：图层状态、搜索结果、定位、导入、统计
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lineRepo: LineRepository,
    private val pointRepo: PointRepository,
    private val batchRepo: BatchRepository,
    private val overlayRepo: com.kunming.powerline.data.repository.OverlayRepository,
    private val thematicRepo: com.kunming.powerline.data.repository.ThematicRepository,
    private val photoRepo: com.kunming.powerline.data.repository.PhotoRepository,
    private val settings: SettingsStore,
    private val amap: AmapLocationClient,
    private val socket: LocationShareSocket,
    val excelParser: com.kunming.powerline.data.parser.ExcelParser,
    val templateGenerator: com.kunming.powerline.data.parser.TemplateGenerator,
    val geoParser: com.kunming.powerline.data.parser.GeoFileParser,
    val pmsRepository: com.kunming.powerline.data.remote.PmsRepository,
    val lineRepository: LineRepository,
    val offlineStore: com.kunming.powerline.data.offline.OfflineTileStore,
    val tileDownloader: com.kunming.powerline.data.offline.TileDownloader,
    val compass: com.kunming.powerline.data.sensor.CompassSensor,
    private val db: AppDatabase
) : ViewModel() {

    // ---------------- 数据流 ----------------
    val lines = lineRepo.lines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val towers = lineRepo.towers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cables = lineRepo.cables.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val stat = lineRepo.stat.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0)

    val favorites = pointRepo.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val takeoffs = pointRepo.takeoffs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cameras = pointRepo.cameras.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val guardians = pointRepo.guardians.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val dangers = pointRepo.dangers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val crossings = pointRepo.crossings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val overlays = overlayRepo.overlays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val photos = photoRepo.photos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val batches = batchRepo.batches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val thematicLayers = thematicRepo.layers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val thematicFeatures = thematicRepo.features.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------- UI 状态 ----------------
    private val _layerState = MutableStateFlow<Map<String, Boolean>>(
        MapLayer.entries.associate { it.id to it.defaultOn }
    )
    val layerState: StateFlow<Map<String, Boolean>> = _layerState.asStateFlow()

    private val _basemap = MutableStateFlow(BasemapType.YN_SATELLITE)
    val basemap: StateFlow<BasemapType> = _basemap.asStateFlow()

    private val _textSize = MutableStateFlow(11f)
    val textSize: StateFlow<Float> = _textSize.asStateFlow()

    /** 名称（线路名 / 区域名 / 杆塔号）开始显示的缩放级别，低于该级别不渲染文字以免卡顿 */
    private val _labelZoom = MutableStateFlow(11f)
    val labelZoom: StateFlow<Float> = _labelZoom.asStateFlow()

    private val _voltageFilter = MutableStateFlow(setOf(500, 220, 110, 35))
    val voltageFilter: StateFlow<Set<Int>> = _voltageFilter.asStateFlow()

    private val _searchHits = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchHits: StateFlow<List<SearchHit>> = _searchHits.asStateFlow()

    private val _selectedTower = MutableStateFlow<TowerEntity?>(null)
    val selectedTower: StateFlow<TowerEntity?> = _selectedTower.asStateFlow()

    private val _searchTarget = MutableStateFlow<SearchHit?>(null)
    val searchTarget: StateFlow<SearchHit?> = _searchTarget.asStateFlow()

    private val _myLocation = MutableStateFlow<FixLocation?>(null)
    val myLocation: StateFlow<FixLocation?> = _myLocation.asStateFlow()

    private val _routeLine = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routeLine: StateFlow<List<Pair<Double, Double>>> = _routeLine.asStateFlow()

    private val _statusBar = MutableStateFlow("")
    val statusBar: StateFlow<String> = _statusBar.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _layerVersion = MutableStateFlow(0L)
    /** 数据版本号变化 -> 通知地图重绘 */
    val layerVersion: StateFlow<Long> = _layerVersion.asStateFlow()

    private val _lastBatch = MutableStateFlow<ImportBatchEntity?>(null)
    val lastBatch: StateFlow<ImportBatchEntity?> = _lastBatch.asStateFlow()

    val shareConnected: StateFlow<Boolean> = socket.connected
    val peers = socket.peers

    init {
        viewModelScope.launch {
            settings.basemap.collect { name ->
                BasemapType.entries.firstOrNull { it.name == name }?.let { _basemap.value = it }
            }
        }
        viewModelScope.launch {
            settings.textSize.collect { _textSize.value = it }
        }
        viewModelScope.launch {
            settings.labelZoom.collect { _labelZoom.value = it }
        }
        viewModelScope.launch {
            settings.layerState.collect { raw ->
                if (raw.isNotBlank()) {
                    val map = _layerState.value.toMutableMap()
                    raw.split(",").forEach { kv ->
                        val p = kv.split("=")
                        if (p.size == 2) map[p[0]] = p[1] == "1"
                    }
                    _layerState.value = map
                }
            }
        }
        viewModelScope.launch {
            settings.voltageFilter.collect { raw ->
                val set = raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                if (set.isNotEmpty()) _voltageFilter.value = set
            }
        }
        // 实时统计状态栏
        viewModelScope.launch {
            combine(myLocation, stat) { loc, s -> Triple(loc, s.first, s.second) }.collect { (loc, l, c) ->
                _statusBar.value = buildString {
                    append("$l 条线路 / $c 条电缆")
                    if (loc != null) {
                        append("  ·  ")
                        append(String.format("经度 %.5f, 纬度 %.5f, 精度 %.0fm", loc.lon, loc.lat, loc.accuracy))
                    }
                }
            }
        }
        // 数据变更触发重绘
        viewModelScope.launch {
            combine(lines, towers, cables, favorites, takeoffs, cameras, guardians, dangers, crossings) { _ -> }
                .collect { _layerVersion.value = System.currentTimeMillis() }
        }
        // 定位数据
        viewModelScope.launch {
            amap.locations.collect {
                _myLocation.value = it
                // 罗盘不可用/未出数时，用 GPS 行进方向兜底（静止时不动）
                if (!_compassActive && it.speed > 0.6f) {
                    _heading.value = ((it.bearing % 360f) + 360f) % 360f
                }
            }
        }
        viewModelScope.launch {
            amap.errors.collect { _messages.emit(it) }
        }
    }

    // ------------------------------------------------------------------
    // 图层
    // ------------------------------------------------------------------
    fun toggleLayer(layer: MapLayer) {
        val map = _layerState.value.toMutableMap()
        map[layer.id] = !(map[layer.id] ?: true)
        _layerState.value = map
        viewModelScope.launch {
            settings.setLayerState(map.entries.joinToString(",") { "${it.key}=${if (it.value) 1 else 0}" })
        }
        _layerVersion.value = System.currentTimeMillis()
    }

    fun isLayerOn(layer: MapLayer): Boolean = _layerState.value[layer.id] ?: true

    fun setBasemap(type: BasemapType) {
        _basemap.value = type
        viewModelScope.launch { settings.setBasemap(type.name) }
    }

    fun setTextSize(sp: Float) {
        _textSize.value = sp
        viewModelScope.launch { settings.setTextSize(sp) }
    }

    /** 名称显示的缩放阈值（6~17），到该级别才绘制线路名/区域名/杆塔号 */
    fun setLabelZoom(z: Float) {
        val v = z.coerceIn(6f, 17f)
        _labelZoom.value = v
        viewModelScope.launch { settings.setLabelZoom(v) }
        _layerVersion.value = System.currentTimeMillis()
    }

    fun toggleVoltage(level: Int) {
        val cur = _voltageFilter.value.toMutableSet()
        if (cur.contains(level)) cur.remove(level) else cur.add(level)
        if (cur.isEmpty()) cur.add(level)
        _voltageFilter.value = cur
        viewModelScope.launch { settings.setVoltageFilter(cur.joinToString(",")) }
        _layerVersion.value = System.currentTimeMillis()
    }

    fun setVoltageFilter(set: Set<Int>) {
        val v = if (set.isEmpty()) emptySet() else set
        _voltageFilter.value = v
        viewModelScope.launch { settings.setVoltageFilter(v.joinToString(",")) }
        _layerVersion.value = System.currentTimeMillis()
    }

    /** 删除单条线路（连带杆塔） */
    fun deleteLine(lineId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            lineRepo.deleteLine(lineId)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    // ------------------------------------------------------------------
    // 定位
    // ------------------------------------------------------------------
    /** 是否开启持续定位（实时位置点） */
    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking.asStateFlow()

    /** 是否跟随（地图随位置移动 / 朝设备方向旋转） */
    private val _followMode = MutableStateFlow(false)
    val followMode: StateFlow<Boolean> = _followMode.asStateFlow()

    /** 设备朝向（罗盘，度，0=北）；罗盘不可用时回退为 GPS 行进方向 */
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    private var headingJob: Job? = null

    /** 罗盘是否已产出有效方位（用于 GPS 方向兜底判断） */
    private var _compassActive = false

    /**
     * 启动持续定位：打开软件即调用，intervalMs 默认 2s 足够画实时位置。
     * 已在运行时不会重复启动。
     */
    fun startTracking(intervalMs: Long = 2000L) {
        if (amap.isRunning()) {
            _tracking.value = true
            return
        }
        val ok = amap.start(intervalMs = intervalMs, highAccuracy = true, once = false)
        _tracking.value = ok
        if (!ok) {
            viewModelScope.launch { _messages.emit("定位启动失败，请检查定位权限与高德 Key") }
            return
        }
        startHeading()
    }

    fun stopTracking() {
        amap.stop()
        _tracking.value = false
        _followMode.value = false
        headingJob?.cancel()
        headingJob = null
    }

    fun setFollowMode(on: Boolean) {
        _followMode.value = on
        if (on && !_tracking.value) startTracking()
    }

    fun toggleFollowMode() = setFollowMode(!_followMode.value)

    /** 订阅罗盘方位角作为设备朝向（比 GPS bearing 更实时，静止时也有效） */
    private fun startHeading() {
        headingJob?.cancel()
        headingJob = viewModelScope.launch {
            var last = Float.NaN
            compass.azimuthFlow(
                trueNorth = { false },
                location = { _myLocation.value?.let { it.lon to it.lat } }
            ).collect { az ->
                val loc = _myLocation.value
                // GPS 行进方向优先（走路/开车时更准），静止时用罗盘
                val target = if (loc != null && loc.speed > 1.2f) {
                    ((loc.bearing % 360f) + 360f) % 360f
                } else {
                    ((az % 360f) + 360f) % 360f
                }
                // 节流：方位变化 < 3° 不推送，避免高频重组与地图抖动
                _compassActive = true
                if (last.isNaN() || kotlin.math.abs(((target - last + 540f) % 360f) - 180f) >= 3f) {
                    _heading.value = target
                    last = target
                }
            }
        }
    }

    fun locateOnce(onFix: (FixLocation) -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = "正在定位…"
            val ok = withContext(Dispatchers.Main) { amap.start(intervalMs = 2000, highAccuracy = true, once = true) }
            if (!ok) {
                _busy.value = null
                _messages.emit("定位启动失败，请检查定位权限与高德 Key")
                return@launch
            }
            val fix = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                amap.locations.first()
            }
            _busy.value = null
            if (fix != null) {
                _myLocation.value = fix
                _statusBar.value = String.format(
                    "经度 %.5f, 纬度 %.5f, 精度 %.0fm", fix.lon, fix.lat, fix.accuracy
                )
                onFix(fix)
            } else {
                _messages.emit("定位超时，请到室外或检查 GPS 信号")
            }
        }
    }

    // ------------------------------------------------------------------
    // 搜索（支持 8 种语法）
    // ------------------------------------------------------------------
    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _searchHits.value = emptyList()
            return
        }
        viewModelScope.launch {
            val hits = withContext(Dispatchers.IO) { doSearch(q) }
            _searchHits.value = hits
            if (hits.isEmpty()) _messages.emit("未找到与「$q」相关的结果")
        }
    }

    private suspend fun doSearch(q: String): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        val parts = q.split(Regex("\\s+"))
        val head = parts.firstOrNull()?.lowercase() ?: ""

        // 1. 经纬度跳转："103.0232 25.2203"
        if (parts.size >= 2) {
            val coord = GeoUtils.parseCoordinatePair(parts[0], parts[1])
            if (coord != null) {
                hits.add(
                    SearchHit(
                        title = "坐标定位",
                        subtitle = String.format("%.6f, %.6f", coord.first, coord.second),
                        lon = coord.first, lat = coord.second, kind = "coord", zoom = 16.0
                    )
                )
                return hits
            }
        }

        // 2. 前缀指令
        when {
            q.startsWith("照片") -> {
                val kw = q.removePrefix("照片").trim()
                db.photoDao().searchByKeyword(kw.ifBlank { "" }).forEach { p ->
                    val t = p.towerId?.let { db.towerDao().getById(it) }
                    hits.add(
                        SearchHit(
                            title = "照片 ${p.keywords.ifBlank { p.filePath.substringAfterLast('/') }}",
                            subtitle = t?.let { "杆塔 ${it.towerNumber}" } ?: "未挂接杆塔",
                            lon = p.lon ?: t?.lon ?: 0.0, lat = p.lat ?: t?.lat ?: 0.0,
                            kind = "photo", refId = p.id, zoom = 17.0
                        )
                    )
                }
                return hits
            }
            q.startsWith("起飞点") -> {
                val kw = q.removePrefix("起飞点").trim()
                pointRepo.takeoffsOnce().filter { kw.isBlank() || it.name.contains(kw) }.forEach { t ->
                    hits.add(SearchHit(t.name, "起飞点 · 海拔 ${t.altitude ?: "—"}m", t.lon, t.lat, "takeoff", t.id, 17.0))
                }
                return hits
            }
            q.startsWith("视频") -> {
                val kw = q.removePrefix("视频").trim()
                pointRepo.camerasOnce().filter { kw.isBlank() || it.name.contains(kw) }.forEach { c ->
                    hits.add(SearchHit(c.name, "视频监控 · ${c.status}", c.lon, c.lat, "camera", c.id, 17.0))
                }
                return hits
            }
            q.startsWith("护线员") -> {
                val kw = q.removePrefix("护线员").trim()
                pointRepo.guardiansOnce().filter { kw.isBlank() || it.name.contains(kw) }.forEach { g ->
                    if (g.lastLon != null && g.lastLat != null) {
                        hits.add(
                            SearchHit(
                                g.name, "护线员 · ${g.responsibilitySection ?: "未设置责任段"}",
                                g.lastLon, g.lastLat, "guardian", g.id, 16.0
                            )
                        )
                    } else {
                        hits.add(SearchHit(g.name, "护线员 · 暂无位置", 0.0, 0.0, "guardian-offline", g.id, 16.0))
                    }
                }
                return hits
            }
            q.startsWith("杆塔") -> {
                val kw = q.removePrefix("杆塔").trim()
                db.towerDao().fuzzySearch(kw).forEach { t ->
                    val ln = db.lineDao().getById(t.lineId)?.name ?: ""
                    hits.add(SearchHit("$ln ${t.towerNumber}", "杆塔 · ${ln}", t.lon, t.lat, "tower", t.id, 17.0))
                }
                return hits
            }
        }

        // 3. 杆塔精确："220kV 某甲线 12"（最后一段为数字）
        if (parts.size >= 2 && parts.last().matches(Regex("""\d+[A-Za-z0-9\-]*"""))) {
            val towerNo = parts.last()
            val lineName = parts.dropLast(1).joinToString(" ")
            val line = db.lineDao().getAll().firstOrNull {
                it.name.replace(" ", "").contains(lineName.replace(" ", "")) ||
                    lineName.replace(" ", "").contains(it.name.replace(" ", ""))
            }
            if (line != null) {
                val t = db.towerDao().findByNumber(line.id, towerNo)
                if (t != null) {
                    hits.add(SearchHit("${line.name} $towerNo", "杆塔 · 精确匹配", t.lon, t.lat, "tower", t.id, 17.5))
                    return hits
                }
            }
        }

        // 4. 地名 / POI / 收藏点 / 起飞点 / 全景影像名（模糊）
        pointRepo.favoritesOnce().filter { it.name.contains(q) }.forEach {
            hits.add(SearchHit(it.name, "收藏点 · ${it.category}", it.lon, it.lat, "favorite", it.id, 17.0))
        }
        pointRepo.takeoffsOnce().filter { it.name.contains(q) }.forEach {
            hits.add(SearchHit(it.name, "起飞点", it.lon, it.lat, "takeoff", it.id, 17.0))
        }
        lines.value.filter { it.name.contains(q) }.forEach { l ->
            val ts = db.towerDao().getByLine(l.id).sortedBy { it.seq }
            val first = ts.firstOrNull()
            hits.add(
                SearchHit(
                    l.name, "线路 · ${VoltagePalette.labelOf(l.voltageLevel)} · ${ts.size} 基杆塔",
                    first?.lon ?: 0.0, first?.lat ?: 0.0, "line", l.id, 13.0
                )
            )
        }
        cables.value.filter { it.name.contains(q) }.forEach { c ->
            hits.add(SearchHit(c.name, "电缆 · ${VoltagePalette.labelOf(c.voltageLevel)}", 0.0, 0.0, "cable", c.id, 15.0))
        }
        db.towerDao().fuzzySearch(q).take(30).forEach { t ->
            val ln = db.lineDao().getById(t.lineId)?.name ?: ""
            hits.add(SearchHit("$ln ${t.towerNumber}", "杆塔 · 模糊匹配", t.lon, t.lat, "tower", t.id, 16.0))
        }
        pointRepo.camerasOnce().filter { it.name.contains(q) }.forEach {
            hits.add(SearchHit(it.name, "视频监控", it.lon, it.lat, "camera", it.id, 17.0))
        }
        pointRepo.guardiansOnce().filter { it.name.contains(q) && it.lastLon != null }.forEach {
            hits.add(SearchHit(it.name, "护线员", it.lastLon!!, it.lastLat!!, "guardian", it.id, 16.0))
        }
        return hits.distinctBy { "${it.kind}-${it.refId}-${it.title}" }
    }

    fun focusHit(hit: SearchHit) {
        _searchTarget.value = hit
        if (hit.kind == "tower" && hit.refId != null) {
            viewModelScope.launch { _selectedTower.value = db.towerDao().getById(hit.refId) }
        }
    }

    fun clearSearch() {
        _searchHits.value = emptyList()
        _searchTarget.value = null
    }

    fun selectTower(tower: TowerEntity?) {
        _selectedTower.value = tower
    }

    fun clearSelectedTower() {
        _selectedTower.value = null
    }

    // ------------------------------------------------------------------
    // 收藏 / 起飞点快捷添加
    // ------------------------------------------------------------------
    fun addFavorite(name: String, category: String, lon: Double, lat: Double, note: String?) {
        viewModelScope.launch {
            pointRepo.upsertFavorite(
                FavoriteEntity(
                    id = LineRepository.newId("F"), name = name, category = category,
                    lon = lon, lat = lat, note = note
                )
            )
            _messages.emit("已添加到收藏点")
        }
    }

    fun addTakeoff(name: String, lon: Double, lat: Double, altitude: Double?, note: String?) {
        viewModelScope.launch {
            pointRepo.upsertTakeoff(
                TakeoffPointEntity(id = LineRepository.newId("T"), name = name, lon = lon, lat = lat, altitude = altitude, note = note)
            )
            _messages.emit("已添加起飞点")
        }
    }

    /** 按当前定位添加收藏点 */
    fun addFavoriteHere(name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val loc = myLocation.value
            if (loc == null) { onDone(false); return@launch }
            pointRepo.upsertFavorite(
                FavoriteEntity(id = LineRepository.newId("F"), name = name, category = "自定义", lon = loc.lon, lat = loc.lat)
            )
            _layerVersion.value = System.currentTimeMillis()
            onDone(true)
        }
    }

    /** 按当前定位添加起飞点 */
    fun addTakeoffHere(name: String, altitude: Double?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val loc = myLocation.value
            if (loc == null) { onDone(false); return@launch }
            pointRepo.upsertTakeoff(
                TakeoffPointEntity(
                    id = LineRepository.newId("P"), name = name, lon = loc.lon, lat = loc.lat,
                    altitude = altitude ?: loc.altitude
                )
            )
            _layerVersion.value = System.currentTimeMillis()
            onDone(true)
        }
    }

    // ---- 电缆 ----
    fun addCable(name: String, voltage: Int, lon: Double, lat: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            db.cableDao().upsert(
                com.kunming.powerline.data.local.entity.CableEntity(
                    id = LineRepository.newId("C"), name = name, voltageLevel = voltage,
                    geometryGeojson = "{\"type\":\"LineString\",\"coordinates\":[[$lon,$lat],[$lon,$lat]]}"
                )
            )
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun deleteCable(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.cableDao().deleteById(id)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    // ---- 交叉跨越 ----
    fun addCrossing(name: String, type: String, lon: Double, lat: Double, distance: Double?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pointDao().upsertCrossing(
                com.kunming.powerline.data.local.entity.CrossingEntity(
                    id = LineRepository.newId("X"), crossType = type, crossObject = name,
                    lon = lon, lat = lat, distance = distance
                )
            )
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun deleteCrossing(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pointDao().deleteCrossing(id)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    // ---- 危险点 ----
    fun addDanger(type: String, level: String, lon: Double, lat: Double, measure: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pointDao().upsertDanger(
                com.kunming.powerline.data.local.entity.DangerPointEntity(
                    id = LineRepository.newId("D"), type = type, level = level,
                    lon = lon, lat = lat, measure = measure
                )
            )
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun deleteDanger(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pointDao().deleteDanger(id)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    // ---- 点要素通用删除 ----
    fun deleteGuardian(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pointDao().deleteGuardian(id)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun deleteCamera(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pointDao().deleteCamera(id)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun addGuardian(name: String, phone: String?, section: String?, lon: Double?, lat: Double?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pointDao().upsertGuardian(
                com.kunming.powerline.data.local.entity.GuardianEntity(
                    id = LineRepository.newId("G"), name = name, phone = phone,
                    responsibilitySection = section, lastLon = lon, lastLat = lat
                )
            )
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun addCamera(name: String, rtspUrl: String, lon: Double, lat: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pointDao().upsertCamera(
                com.kunming.powerline.data.local.entity.CameraEntity(
                    id = LineRepository.newId("V"), name = name, rtspUrl = rtspUrl, lon = lon, lat = lat
                )
            )
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    // ---- 收藏 / 起飞点 / 照片 / 标注删除 ----
    fun deleteFavorite(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pointDao().deleteFavorite(id)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun deleteTakeoff(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pointDao().deleteTakeoff(id)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    // ------------------------------------------------------------------
    // 各图层的编辑（保存）与文件导入
    // ------------------------------------------------------------------
    fun saveFavorite(f: FavoriteEntity) = edit { db.pointDao().upsertFavorite(f); "收藏点「${f.name}」已保存" }
    fun saveTakeoff(t: TakeoffPointEntity) = edit { db.pointDao().upsertTakeoff(t); "起飞点「${t.name}」已保存" }
    fun saveDanger(d: DangerPointEntity) = edit { db.pointDao().upsertDanger(d); "危险点「${d.type}」已保存" }
    fun saveCrossing(c: CrossingEntity) = edit { db.pointDao().upsertCrossing(c); "交叉跨越「${c.crossObject}」已保存" }
    fun saveCamera(c: CameraEntity) = edit { db.pointDao().upsertCamera(c); "视频监控「${c.name}」已保存" }
    fun saveGuardian(g: GuardianEntity) = edit { db.pointDao().upsertGuardian(g); "护线员「${g.name}」已保存" }
    fun saveCable(c: CableEntity) = edit { db.cableDao().upsert(c); "电缆「${c.name}」已保存" }
    fun saveLine(l: LineEntity) = edit { db.lineDao().upsert(l); "线路「${l.name}」已保存" }

    private fun edit(block: suspend () -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block() }
                .onSuccess {
                    _layerVersion.value = System.currentTimeMillis()
                    _messages.emit(it)
                }
                .onFailure { _messages.emit("保存失败：${it.message}") }
        }
    }

    /**
     * 把解析出来的点要素导入某个点位图层
     * @param target favorite / takeoff / danger / crossing / camera / guardian
     */
    fun importPointsTo(target: String, features: List<com.kunming.powerline.data.parser.ImportFeature>): String {
        return try {
            val n = kotlinx.coroutines.runBlocking { pointRepo.importPoints(target, features) }
            _layerVersion.value = System.currentTimeMillis()
            if (n == 0) "没有可导入的点要素（该图层只接收点数据）" else "已导入 $n 个点要素"
        } catch (t: Throwable) {
            "导入失败：${t.message}"
        }
    }

    /** 线要素 → 电缆图层 */
    fun importLinesToCable(features: List<com.kunming.powerline.data.parser.ImportFeature>, voltage: Int): String {
        return try {
            val lines = features.filter { it.kind == "line" || it.kind == "polygon" }
            if (lines.isEmpty()) return "没有可导入的线要素"
            kotlinx.coroutines.runBlocking {
                lines.forEachIndexed { i, f ->
                    db.cableDao().upsert(
                        CableEntity(
                            id = LineRepository.newId("CB"),
                            name = f.name?.takeIf { it.isNotBlank() } ?: "电缆 ${i + 1}",
                            voltageLevel = voltage,
                            geometryGeojson = f.geometryJson,
                            remark = f.properties["description"]
                        )
                    )
                }
            }
            _layerVersion.value = System.currentTimeMillis()
            "已导入 ${lines.size} 段电缆"
        } catch (t: Throwable) {
            "导入失败：${t.message}"
        }
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.photoDao().deleteById(id)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun deleteOverlay(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.overlayDao().deleteById(id)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    /** 写入照片记录 */
    /**
     * 从相册 / 文件批量导入现场照片
     *
     * 复制到应用私有目录（content:// 重启后会失效），并读 EXIF 的 GPS 与拍摄时间，
     * 没有 GPS 的照片仍保留记录，只是不会出现在地图上。
     */
    fun importPhotosFromUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var ok = 0
            var noGps = 0
            var fail = 0
            val dir = java.io.File(context.filesDir, "photos").apply { mkdirs() }
            uris.forEachIndexed { i, uri ->
                runCatching {
                    val dst = java.io.File(dir, "imp_${System.currentTimeMillis()}_$i.jpg")
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("无法读取文件")
                    input.use { src -> dst.outputStream().use { out -> src.copyTo(out) } }
                    val exif = android.media.ExifInterface(dst.absolutePath)
                    val ll = FloatArray(2)
                    val hasGps = exif.getLatLong(ll)
                    val taken = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?.let { raw ->
                        runCatching {
                            java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                                .parse(raw)?.time
                        }.getOrNull()
                    } ?: System.currentTimeMillis()
                    db.photoDao().upsert(
                        com.kunming.powerline.data.local.entity.PhotoEntity(
                            id = LineRepository.newId("PH"),
                            filePath = dst.absolutePath,
                            keywords = "导入照片",
                            takenAt = taken,
                            lon = if (hasGps) ll[1].toDouble() else null,
                            lat = if (hasGps) ll[0].toDouble() else null
                        )
                    )
                    ok++
                    if (!hasGps) noGps++
                }.onFailure { fail++ }
            }
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit(
                buildString {
                    append("已导入 $ok 张照片")
                    if (noGps > 0) append("（$noGps 张无 GPS 信息，不会显示在地图上）")
                    if (fail > 0) append("，$fail 张失败")
                }
            )
        }
    }

    fun addPhotoRecord(file: java.io.File, towerId: String?, lon: Double?, lat: Double?, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val tower = towerId?.let { id -> towers.value.firstOrNull { it.id == id } }
            val lineName = tower?.let { t -> lines.value.firstOrNull { it.id == t.lineId }?.name }
            db.photoDao().upsert(
                com.kunming.powerline.data.local.entity.PhotoEntity(
                    id = LineRepository.newId("PH"),
                    towerId = towerId,
                    filePath = file.absolutePath,
                    keywords = listOfNotNull(lineName, tower?.towerNumber).joinToString(" "),
                    lon = lon, lat = lat
                )
            )
            _layerVersion.value = System.currentTimeMillis()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    // ------------------------------------------------------------------
    // 线路创建 / 删除 / 导入
    // ------------------------------------------------------------------
    fun createEmptyLine(name: String, voltage: Int, start: String?, end: String?, date: String?, remark: String?) {
        viewModelScope.launch {
            val line = LineEntity(
                id = LineRepository.newId("L"), name = name, voltageLevel = voltage,
                startStation = start, endStation = end, inServiceDate = date, remark = remark,
                isEmptyLine = true
            )
            lineRepo.createLine(line)
            _messages.emit("空线路「$name」创建成功，可长按地图添加杆塔")
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun appendTowerToLine(lineId: String, lon: Double, lat: Double) {
        viewModelScope.launch {
            val count = lineRepo.towerOfLine(lineId).size
            val tower = TowerEntity(
                id = LineRepository.newId("TW"), lineId = lineId,
                towerNumber = "${count + 1}", lon = lon, lat = lat, seq = count
            )
            lineRepo.addTower(tower)
            db.lineDao().updateGeometry(lineId, null)
            _messages.emit("已添加杆塔 ${tower.towerNumber}")
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun deleteAllLines() {
        viewModelScope.launch {
            lineRepo.deleteAllLines()
            _messages.emit("已删除全部线路及关联杆塔")
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    /**
     * 导入解析结果入库，返回批次
     */
    suspend fun saveImport(result: ParseResult, sourceType: String, fileName: String): ImportBatchEntity =
        withContext(Dispatchers.IO) {
            val batchId = LineRepository.newId("B")
            val lineIds = mutableListOf<String>()
            var towerCount = 0
            result.lines.forEach { il ->
                val lineId = LineRepository.newId("L")
                val towers = il.towers.mapIndexed { i, t ->
                    TowerEntity(
                        id = "${lineId}_t${i}", lineId = lineId, towerNumber = t.towerNumber,
                        lon = t.lon, lat = t.lat, elevation = t.elevation, type = t.type,
                        circuitNo = t.circuitNo, circuitCount = t.circuitCount, seq = i,
                        remark = t.remark
                    )
                }
                val line = LineEntity(
                    id = lineId, name = il.name, voltageLevel = il.voltageLevel,
                    startStation = il.startStation, endStation = il.endStation,
                    inServiceDate = il.inServiceDate, remark = il.remark,
                    isEmptyLine = towers.isEmpty(), importBatchId = batchId
                )
                db.lineDao().upsert(line)
                if (towers.isNotEmpty()) {
                    db.towerDao().upsertAll(towers)
                    lineRepo.rebuildLineGeometry(lineId)
                    towerCount += towers.size
                }
                lineIds.add(lineId)
            }
            val batch = ImportBatchEntity(
                id = batchId, sourceType = sourceType, fileName = fileName,
                status = if (result.errors.isEmpty()) "success" else "partial",
                errorRows = result.errors.size, lineIds = lineIds.joinToString(","),
                towerCount = towerCount
            )
            db.batchDao().upsert(batch)
            _lastBatch.value = batch
            _layerVersion.value = System.currentTimeMillis()
            batch
        }

    fun rollbackBatch(batchId: String) {
        viewModelScope.launch {
            val ok = batchRepo.rollback(batchId)
            _messages.emit(if (ok) "已撤销本次导入" else "超过 5 分钟，无法撤销导入")
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    /** 同步版导入入库（供 Compose 回调直接调用），返回提示文案 */
    fun saveImportSync(result: ParseResult, sourceType: String, fileName: String): String {
        return try {
            val batch = kotlinx.coroutines.runBlocking { saveImport(result, sourceType, fileName) }
            "导入完成：${result.lines.size} 条线路 / ${result.towerCount} 基杆塔" +
                if (result.errors.isEmpty()) "" else "，${result.errors.size} 行被忽略"
        } catch (t: Throwable) {
            "导入失败：${t.message}"
        }
    }

    // ------------------------------------------------------------------
    // 专题图层（点 / 线 / 面）
    // ------------------------------------------------------------------
    fun importThematicSync(result: com.kunming.powerline.data.parser.ThematicParseResult, fileName: String): String {
        return try {
            val (layers, features) = kotlinx.coroutines.runBlocking {
                thematicRepo.save(result, fileName.ifBlank { null })
            }
            _layerVersion.value = System.currentTimeMillis()
            "已导入专题图层：$layers 个图层 / $features 个要素"
        } catch (t: Throwable) {
            "导入失败：${t.message}"
        }
    }

    fun setThematicVisible(id: String, visible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            thematicRepo.setVisible(id, visible)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    fun renameThematicLayer(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            thematicRepo.rename(id, name.trim())
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit("图层已重命名为「${name.trim()}」")
        }
    }

    /** 改名 + 备注（管理页「编辑」用） */
    fun updateThematicLayer(id: String, name: String, remark: String?) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            thematicRepo.rename(id, name.trim())
            thematicRepo.setRemark(id, remark)
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit("已保存「${name.trim()}」")
        }
    }

    /** 单层：是否在地图上显示要素名称 / 备注 */
    fun setThematicLabelVisible(id: String, visible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            thematicRepo.setLabelVisible(id, visible)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    /** 单层：面填充透明度 0..0.9 */
    fun setThematicFillOpacity(id: String, opacity: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            thematicRepo.setFillOpacity(id, opacity)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    /** 单层：线宽 */
    fun setThematicLineWidth(id: String, width: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            thematicRepo.setLineWidth(id, width)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    /** 批量：全部面图层透明度 */
    fun setAllThematicFillOpacity(opacity: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            thematicRepo.setAllFillOpacity(opacity)
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit("已把全部区域透明度设为 ${(opacity.coerceIn(0f, 0.9f) * 100).toInt()}%")
        }
    }

    /** 批量：全部图层名称开关 */
    fun setAllThematicLabelVisible(visible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            thematicRepo.setAllLabelVisible(visible)
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit(if (visible) "已显示全部图层名称" else "已隐藏全部图层名称")
        }
    }

    /** 把专题要素移动到另一个图层 */
    fun moveThematicFeature(featureId: String, targetLayerId: String, targetName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = thematicRepo.moveFeature(featureId, targetLayerId)
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit(if (ok) "已移动到「$targetName」" else "移动失败：目标图层不存在")
        }
    }

    /**
     * 专题线要素 → 正式线路（单条）
     *
     * 线要素的每个顶点作为一基"虚拟杆塔"，编号 1..N；线路几何由杆塔顺序重建。
     */
    fun convertThematicFeatureToLine(featureId: String, voltage: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val f = thematicRepo.feature(featureId)
            if (f == null || f.kind != "line") {
                _messages.emit("只有线要素可以转为线路")
                return@launch
            }
            val layers = thematicRepo.getLayers()
            val layerName = layers.firstOrNull { it.id == f.layerId }?.name ?: "专题线路"
            val n = createLineFromCoords(
                coords = com.kunming.powerline.ui.map.ThematicLineConverter.lineCoords(f.geometryGeojson),
                name = com.kunming.powerline.ui.map.ThematicLineConverter.lineName(f.name, layerName, 0, 1),
                voltage = voltage
            )
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit(
                if (n > 0) "已转为线路（$n 基杆塔）" else "转换失败：线要素坐标不足 2 个点"
            )
        }
    }

    /**
     * 整层批量：该图层所有线要素各转成一条线路
     */
    fun convertThematicLinesToLines(layerId: String, voltage: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val layer = thematicRepo.getLayers().firstOrNull { it.id == layerId }
            if (layer == null) {
                _messages.emit("图层不存在")
                return@launch
            }
            val feats = thematicRepo.featuresOfLayer(layerId).filter { it.kind == "line" }
            if (feats.isEmpty()) {
                _messages.emit("该图层没有线要素，无法转换")
                return@launch
            }
            var lineCount = 0
            var towerCount = 0
            feats.forEachIndexed { i, f ->
                val coords = com.kunming.powerline.ui.map.ThematicLineConverter.lineCoords(f.geometryGeojson)
                val name = com.kunming.powerline.ui.map.ThematicLineConverter.lineName(
                    f.name, layer.name, i, feats.size
                )
                val n = createLineFromCoords(coords, name, voltage)
                if (n > 0) {
                    lineCount++
                    towerCount += n
                }
            }
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit(
                if (lineCount > 0) "已转换 $lineCount 条线路 / $towerCount 基杆塔"
                else "转换失败：没有可用的线要素坐标"
            )
        }
    }

    /**
     * 用一组坐标建线路：顶点 → 杆塔（编号 1..N），返回杆塔数（0 表示失败）
     */
    private suspend fun createLineFromCoords(
        coords: List<Pair<Double, Double>>,
        name: String,
        voltage: Int
    ): Int {
        val pts = com.kunming.powerline.ui.map.ThematicLineConverter.dropClosingPoint(coords)
        if (pts.size < com.kunming.powerline.ui.map.ThematicLineConverter.MIN_POINTS) return 0
        val uniqueName = uniqueLineName(name)
        val lineId = com.kunming.powerline.data.repository.LineRepository.newId("LN")
        val line = com.kunming.powerline.data.local.entity.LineEntity(
            id = lineId,
            name = uniqueName,
            voltageLevel = voltage,
            remark = "由专题图层转换"
        )
        lineRepo.createLine(line)
        val nums = com.kunming.powerline.ui.map.ThematicLineConverter.towerNumbers(pts.size)
        val towers = pts.mapIndexed { i, p ->
            com.kunming.powerline.data.local.entity.TowerEntity(
                id = com.kunming.powerline.data.repository.LineRepository.newId("TW"),
                lineId = lineId,
                towerNumber = nums[i],
                lon = p.first,
                lat = p.second,
                seq = i
            )
        }
        lineRepo.addTowers(towers)
        return towers.size
    }

    /** 线路重名自动加序号 */
    private suspend fun uniqueLineName(name: String): String {
        val existing = lineRepo.lineList().map { it.name }.toSet()
        if (name !in existing) return name
        var i = 2
        while ("$name ($i)" in existing) i++
        return "$name ($i)"
    }

    fun deleteThematicLayer(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            thematicRepo.delete(id)
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit("已删除专题图层")
        }
    }

    fun deleteAllThematic() {
        viewModelScope.launch(Dispatchers.IO) {
            thematicRepo.deleteAll()
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit("已清空全部专题图层")
        }
    }

    /** 同塔多回视觉偏移批量重置 */
    fun resetVisualOffsets() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = db.towerDao().getAll()
            all.filter { it.visualOffsetLon != 0.0 || it.visualOffsetLat != 0.0 }
                .forEach { db.towerDao().updateVisualOffset(it.id, 0.0, 0.0) }
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit("已重置 ${all.count { it.visualOffsetLon != 0.0 || it.visualOffsetLat != 0.0 }} 基杆塔的显示偏移")
        }
    }

    /** 保存单基杆塔（编辑经纬度/类型等） */
    fun updateTower(tower: TowerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.towerDao().update(tower)
            lineRepo.rebuildLineGeometry(tower.lineId)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    /** 更新线路基本信息（名称/电压/起止站/投运日期/备注） */
    fun updateLine(line: LineEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.lineDao().upsert(line)
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit("线路「${line.name}」已保存")
        }
    }

    /** 删除单基杆塔（并重建线路几何） */
    fun deleteTower(tower: TowerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.towerDao().deleteById(tower.id)
            lineRepo.rebuildLineGeometry(tower.lineId)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    /** 重新按杆塔 seq 排序整条线路的杆塔（编辑后统一调用） */
    fun resequenceTowers(lineId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = db.towerDao().getByLine(lineId)
            list.forEachIndexed { i, t ->
                if (t.seq != i + 1) db.towerDao().update(t.copy(seq = i + 1))
            }
            lineRepo.rebuildLineGeometry(lineId)
            _layerVersion.value = System.currentTimeMillis()
        }
    }

    /** 手动追加杆塔到指定线路 */
    fun addTower(lineId: String, towerNumber: String, lon: Double, lat: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val seq = (db.towerDao().countByLine(lineId)) + 1
            db.towerDao().upsert(
                TowerEntity(
                    id = LineRepository.newId("T"), lineId = lineId, towerNumber = towerNumber,
                    lon = lon, lat = lat, seq = seq
                )
            )
            lineRepo.rebuildLineGeometry(lineId)
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit("已添加杆塔 $towerNumber")
        }
    }

    // ------------------------------------------------------------------
    // 位置共享
    // ------------------------------------------------------------------
    fun startSharing(room: String) {
        viewModelScope.launch {
            if (room.isBlank()) {
                _messages.emit("请输入房间号")
                return@launch
            }
            settings.setRoomId(room)
            settings.setShareEnabled(true)
            val user = settings.ensureUserId()
            LocationSharingService.start(context, room, user)
            _messages.emit("已开启位置共享：房间 $room")
        }
    }

    fun stopSharing() {
        viewModelScope.launch {
            settings.setShareEnabled(false)
            LocationSharingService.stop(context)
            _messages.emit("已停止位置共享")
        }
    }

    // ------------------------------------------------------------------
    // 台账校验 / 杆塔长度
    // ------------------------------------------------------------------
    suspend fun validateLedger(): List<ValidationIssue> = withContext(Dispatchers.IO) { lineRepo.validate() }

    suspend fun calcSection(line: String, from: String?, to: String?) =
        withContext(Dispatchers.IO) { lineRepo.calcTowerSectionLength(line, from, to) }

    fun addOverlay(o: com.kunming.powerline.data.local.entity.CustomOverlayEntity) {
        viewModelScope.launch {
            overlayRepo.upsert(o)
            _layerVersion.value = System.currentTimeMillis()
            _messages.emit("已保存${o.type}标注：${o.name}")
        }
    }

    fun setBusy(msg: String?) {
        _busy.value = msg
    }

    fun postMessage(msg: String) {
        viewModelScope.launch { _messages.emit(msg) }
    }

    fun setRoute(from: Pair<Double, Double>?, to: Pair<Double, Double>?) {
        if (from == null || to == null) _routeLine.value = emptyList()
        else _routeLine.value = listOf(from, to)
    }

    fun towerPoints(lineId: String): List<Point> =
        towers.value.filter { it.lineId == lineId }.sortedBy { it.seq }.map { Point.fromLngLat(it.lon, it.lat) }
}


