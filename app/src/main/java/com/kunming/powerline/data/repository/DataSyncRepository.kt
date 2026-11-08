package com.kunming.powerline.data.repository

import android.content.Context
import android.net.Uri
import com.kunming.powerline.data.local.AppDatabase
import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.data.local.entity.ThematicFeatureEntity
import com.kunming.powerline.data.local.entity.ThematicLayerEntity
import com.kunming.powerline.data.local.entity.TowerEntity
import com.kunming.powerline.data.remote.PmsApi
import com.kunming.powerline.data.remote.model.DatasetBundleDto
import com.kunming.powerline.data.remote.model.DatasetFeatureDto
import com.kunming.powerline.data.remote.model.DatasetLayerDto
import com.kunming.powerline.data.remote.model.DatasetMetaDto
import com.kunming.powerline.data.remote.model.DatasetStats
import com.kunming.powerline.data.remote.model.PmsLedgerBundle
import com.kunming.powerline.data.remote.model.PmsLineDto
import com.kunming.powerline.data.remote.model.PmsTowerDto
import com.kunming.powerline.util.FileSaver
import com.kunming.powerline.util.VoltagePalette
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NAS / 服务器 / 本地文件三路数据同步。
 *
 * - 上传：把本地专题图层 + 线路台账组装成一个数据集 POST 到 NAS
 * - 下载：从 NAS 拉数据集并按 id / 名称 UPSERT 回本地 Room
 * - 本地文件：导出 / 导入同一份 JSON，便于离线流转与备份
 *
 * 三种入口共用 DatasetBundleDto（powerline-dataset/1），NAS 端与本地文件内容完全一致。
 */
@Singleton
class DataSyncRepository @Inject constructor(
    private val api: PmsApi,
    private val db: AppDatabase,
    private val lineRepo: LineRepository,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context
) {
    private val adapter by lazy { moshi.adapter(DatasetBundleDto::class.java) }
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    private fun now(): String = fmt.format(Date())
    private fun newId(prefix: String): String =
        "$prefix-" + UUID.randomUUID().toString().replace("-", "").take(12)

    /** 服务器上的数据集列表（元数据） */
    suspend fun listRemote(): Result<List<DatasetMetaDto>> = withContext(Dispatchers.IO) {
        runCatching { api.listDatasets() }
    }

    suspend fun deleteRemote(id: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            api.deleteDataset(id)
            "已删除服务器数据集 $id"
        }
    }

    /** 组装本机数据为一个数据集（不联网，导出 / 上传都用它） */
    suspend fun buildLocal(name: String, kind: String): DatasetBundleDto =
        withContext(Dispatchers.IO) {
            val includeLayers = kind != DatasetBundleDto.KIND_LINES
            val includeLines = kind != DatasetBundleDto.KIND_LAYERS

            val layerDtos = if (includeLayers) {
                val layers = db.thematicDao().getLayers()
                val features = db.thematicDao().getFeatures()
                layers.map { l ->
                    DatasetLayerDto(
                        id = l.id,
                        name = l.name,
                        geometryType = l.geometryType,
                        color = l.color,
                        visible = l.visible,
                        labelVisible = l.labelVisible,
                        fillOpacity = l.fillOpacity,
                        lineWidth = l.lineWidth,
                        sourceFormat = l.sourceFormat,
                        remark = l.remark,
                        features = features.filter { it.layerId == l.id }.map { f ->
                            DatasetFeatureDto(
                                id = f.id, layerId = f.layerId, name = f.name,
                                kind = f.kind, geometryGeojson = f.geometryGeojson,
                                lon = f.lon, lat = f.lat,
                                propertiesJson = f.propertiesJson, seq = f.seq
                            )
                        }
                    )
                }
            } else emptyList()

            val ledger = if (includeLines) {
                val lines = db.lineDao().getAll()
                val towers = db.towerDao().getAll()
                val nameById = lines.associate { it.id to it.name }
                PmsLedgerBundle(
                    lines = lines.map { l ->
                        PmsLineDto(
                            id = l.id, lineName = l.name,
                            voltageLevel = "${l.voltageLevel}kV",
                            startStation = l.startStation, endStation = l.endStation,
                            inServiceDate = l.inServiceDate, remark = l.remark
                        )
                    },
                    towers = towers.map { t ->
                        PmsTowerDto(
                            id = t.id, lineId = t.lineId, lineName = nameById[t.lineId],
                            towerNumber = t.towerNumber, longitude = t.lon, latitude = t.lat,
                            elevation = t.elevation, towerType = t.type,
                            circuitCount = t.circuitCount, circuitNo = t.circuitNo
                        )
                    }
                )
            } else PmsLedgerBundle()

            DatasetBundleDto(
                id = newId("DS"),
                name = name.trim(),
                kind = kind,
                version = 1,
                updatedAt = now(),
                note = "由 App ${DatasetBundleDto.SCHEMA} 导出",
                layers = layerDtos,
                ledger = ledger
            )
        }

    /** 上传本机数据到 NAS / 服务器 */
    suspend fun upload(name: String, kind: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(DatasetStats.validName(name)) { "数据集名称不能为空，且不能包含 / 或 \\" }
            val b = buildLocal(name.trim(), kind)
            api.uploadDataset(b)
            "已上传「${b.name}」：${DatasetStats.summary(b)}"
        }
    }

    /** 从 NAS / 服务器下载并合并进本地库 */
    suspend fun download(id: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val b = api.downloadDataset(id)
            "已下载「${b.name.ifBlank { id }}」：${applyBundle(b)}"
        }
    }

    /** 导出为本地 JSON 文件（SAF，用户自己选保存位置） */
    suspend fun exportToUri(uri: Uri, name: String, kind: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(DatasetStats.validName(name)) { "数据集名称不能为空，且不能包含 / 或 \\" }
                val b = buildLocal(name.trim(), kind)
                val json = adapter.indent("  ").toJson(b)
                require(FileSaver.writeText(context, uri, json)) { "写入文件失败" }
                "已导出本地 JSON：${DatasetStats.summary(b)}"
            }
        }

    /** 从本地 JSON 文件导入（与 NAS 下载走同一套合并逻辑） */
    suspend fun importFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("无法读取所选文件")
            val b = adapter.fromJson(text) ?: error("JSON 解析失败，请确认是 App 导出的数据集文件")
            "已导入本地 JSON「${b.name.ifBlank { "数据集" }}」：${applyBundle(b)}"
        }
    }

    /**
     * 把数据集写入本地库：
     * - 图层 / 要素按 id 覆盖（先清同 id 旧要素，保证重复下载不残留）
     * - 线路按「名称优先」查找已有，杆塔按线路名 + 杆塔号覆盖
     * 返回人类可读的统计文案。
     */
    private suspend fun applyBundle(b: DatasetBundleDto): String = withContext(Dispatchers.IO) {
        val dao = db.thematicDao()
        var layerCount = 0
        var featureCount = 0

        b.layers.forEach { l ->
            val entity = ThematicLayerEntity(
                id = l.id.ifBlank { newId("TL") },
                name = l.name.ifBlank { "未命名图层" },
                geometryType = l.geometryType,
                color = l.color,
                visible = l.visible,
                featureCount = l.features.size,
                sourceFormat = l.sourceFormat,
                remark = l.remark,
                labelVisible = l.labelVisible,
                fillOpacity = l.fillOpacity,
                lineWidth = l.lineWidth
            )
            dao.upsertLayer(entity)
            dao.deleteFeatures(entity.id)
            if (l.features.isNotEmpty()) {
                dao.upsertFeatures(l.features.map { f ->
                    ThematicFeatureEntity(
                        id = f.id.ifBlank { newId("TF") },
                        layerId = entity.id,
                        name = f.name,
                        kind = f.kind,
                        geometryGeojson = f.geometryGeojson,
                        lon = f.lon,
                        lat = f.lat,
                        propertiesJson = f.propertiesJson,
                        seq = f.seq
                    )
                })
            }
            layerCount++
            featureCount += l.features.size
        }

        val batchId = newId("DSB")
        val idMap = mutableMapOf<String, String>()
        var lineCount = 0
        b.ledger.lines.forEach { dto ->
            val existing = db.lineDao().findByName(dto.lineName)
            val lineId = existing?.id
                ?: dto.id?.takeIf { it.isNotBlank() }
                ?: newId("L")
            val vol = VoltagePalette.parseLevel(dto.voltageLevel)
                ?: VoltagePalette.parseLevel(dto.lineName)
                ?: existing?.voltageLevel
                ?: 110
            db.lineDao().upsert(
                LineEntity(
                    id = lineId,
                    name = dto.lineName,
                    voltageLevel = vol,
                    startStation = dto.startStation,
                    endStation = dto.endStation,
                    inServiceDate = dto.inServiceDate,
                    remark = dto.remark ?: dto.unitName,
                    geometryGeojson = existing?.geometryGeojson,
                    isEmptyLine = existing?.isEmptyLine ?: true,
                    importBatchId = batchId
                )
            )
            idMap[dto.lineName] = lineId
            dto.id?.takeIf { it.isNotBlank() }?.let { idMap[it] = lineId }
            lineCount++
        }

        var towerCount = 0
        b.ledger.towers.groupBy { t ->
            t.lineId?.let { idMap[it] } ?: t.lineName?.let { idMap[it] }
        }.forEach { (lineId, list) ->
            if (lineId == null) return@forEach
            val existing = db.towerDao().getByLine(lineId).associateBy { it.towerNumber }
            val merged = list.mapIndexedNotNull { i, t ->
                val lon = t.longitude ?: return@mapIndexedNotNull null
                val lat = t.latitude ?: return@mapIndexedNotNull null
                val old = existing[t.towerNumber]
                TowerEntity(
                    id = old?.id ?: t.id?.takeIf { it.isNotBlank() } ?: newId("T"),
                    lineId = lineId,
                    towerNumber = t.towerNumber,
                    lon = lon,
                    lat = lat,
                    elevation = t.elevation,
                    type = t.towerType,
                    circuitNo = t.circuitNo ?: 1,
                    circuitCount = t.circuitCount ?: 1,
                    seq = i
                )
            }
            if (merged.isNotEmpty()) {
                db.towerDao().upsertAll(merged)
                towerCount += merged.size
                lineRepo.rebuildLineGeometry(lineId)
            }
        }

        "$layerCount 个图层 / $featureCount 个要素 / $lineCount 条线路 / $towerCount 基杆塔"
    }
}
