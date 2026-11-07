package com.kunming.powerline.data.remote

import com.kunming.powerline.BuildConfig
import com.kunming.powerline.data.local.AppDatabase
import com.kunming.powerline.data.local.entity.CrossingEntity
import com.kunming.powerline.data.local.entity.ImportBatchEntity
import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.data.local.entity.TowerEntity
import com.kunming.powerline.data.repository.LineRepository
import com.kunming.powerline.util.VoltagePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PMS 台账导入仓库
 * - 按 线路名 + 杆塔号 UPSERT（增量同步，不重复创建）
 */
@Singleton
class PmsRepository @Inject constructor(
    private val api: PmsApi,
    private val db: AppDatabase,
    private val lineRepo: LineRepository,
    private val serverConfig: com.kunming.powerline.data.prefs.ServerConfig
) {

    suspend fun fetchUnits(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching { api.getUnits().units }
    }

    suspend fun fetchLines(unit: String?, voltage: Int?, keyword: String?): Result<List<com.kunming.powerline.data.remote.model.PmsLineDto>> =
        withContext(Dispatchers.IO) {
            runCatching { api.getLines(unit, voltage, keyword) }
        }

    suspend fun fetchTowers(lineId: String): Result<List<com.kunming.powerline.data.remote.model.PmsTowerDto>> =
        withContext(Dispatchers.IO) {
            runCatching { api.getTowers(lineId) }
        }

    /**
     * 按选中的线路批量导入
     */
    suspend fun importLines(
        selected: List<com.kunming.powerline.data.remote.model.PmsLineDto>,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<ImportBatchEntity> = withContext(Dispatchers.IO) {
        try {
            val batchId = UUID.randomUUID().toString().replace("-", "").take(16)
            val importedIds = mutableListOf<String>()
            var towerCount = 0

            selected.forEachIndexed { index, dto ->
                onProgress(index + 1, dto.lineName)
                val vol = VoltagePalette.parseLevel(dto.voltageLevel)
                    ?: VoltagePalette.parseLevel(dto.lineName)
                    ?: 110

                // 增量：按线路名查找已存在
                val existing = db.lineDao().findByName(dto.lineName)
                val lineId = existing?.id ?: LineRepository.newId("L")
                val line = LineEntity(
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
                db.lineDao().upsert(line)
                importedIds.add(lineId)

                // 杆塔：优先用 lineId 拉取，失败则退回 lineName
                val towerDto = try {
                    api.getTowers(dto.id ?: lineId)
                } catch (e: Exception) {
                    try {
                        api.getTowers(dto.lineName)
                    } catch (e2: Exception) {
                        emptyList()
                    }
                }

                if (towerDto.isNotEmpty()) {
                    val towers = towerDto.mapIndexedNotNull { i, t ->
                        val lon = t.longitude ?: return@mapIndexedNotNull null
                        val lat = t.latitude ?: return@mapIndexedNotNull null
                        TowerEntity(
                            id = "${lineId}_t_${t.towerNumber}",
                            lineId = lineId,
                            towerNumber = t.towerNumber,
                            lon = lon, lat = lat,
                            elevation = t.elevation,
                            type = t.towerType,
                            circuitNo = t.circuitNo ?: 1,
                            circuitCount = t.circuitCount ?: 1,
                            seq = i
                        )
                    }
                    // UPSERT：同线路同杆塔号覆盖
                    val existingTowers = db.towerDao().getByLine(lineId).associateBy { it.towerNumber }
                    val merged = towers.map { t ->
                        existingTowers[t.towerNumber]?.let { old -> t.copy(id = old.id) } ?: t
                    }
                    db.towerDao().upsertAll(merged)
                    towerCount += merged.size
                    lineRepo.rebuildLineGeometry(lineId)
                }
            }

            val batch = ImportBatchEntity(
                id = batchId,
                sourceType = "pms",
                fileName = "PMS 台账（${selected.size} 条线路）",
                status = "success",
                lineIds = importedIds.joinToString(","),
                towerCount = towerCount
            )
            db.batchDao().upsert(batch)
            Result.success(batch)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** 导入交叉跨越台账 */
    suspend fun importCrossings(bundle: com.kunming.powerline.data.remote.model.PmsLedgerBundle): Int =
        withContext(Dispatchers.IO) {
            var n = 0
            bundle.crossings.forEach { c ->
                val lineId = c.lineName?.let { db.lineDao().findByName(it)?.id } ?: c.lineId
                db.pointDao().upsertCrossing(
                    CrossingEntity(
                        id = "X" + UUID.randomUUID().toString().take(12),
                        lineId = lineId,
                        crossType = c.crossType,
                        crossObject = c.crossObject,
                        distance = c.distance,
                        lon = c.longitude,
                        lat = c.latitude,
                        towerNumber = c.towerNumber
                    )
                )
                n++
            }
            n
        }

    /** 当前生效的 PMS 地址（设置页可改，改完即时生效） */
    fun baseUrl(): String = serverConfig.current.pmsBase
}
