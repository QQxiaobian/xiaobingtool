package com.kunming.powerline.data.repository

import com.kunming.powerline.data.local.AppDatabase
import com.kunming.powerline.data.local.entity.CameraEntity
import com.kunming.powerline.data.local.entity.CrossingEntity
import com.kunming.powerline.data.local.entity.CustomOverlayEntity
import com.kunming.powerline.data.local.entity.DangerPointEntity
import com.kunming.powerline.data.local.entity.FavoriteEntity
import com.kunming.powerline.data.local.entity.GuardianEntity
import com.kunming.powerline.data.local.entity.PhotoEntity
import com.kunming.powerline.data.local.entity.TakeoffPointEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PointRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.pointDao()

    val favorites: Flow<List<FavoriteEntity>> = dao.observeFavorites()
    val takeoffs: Flow<List<TakeoffPointEntity>> = dao.observeTakeoffs()
    val cameras: Flow<List<CameraEntity>> = dao.observeCameras()
    val guardians: Flow<List<GuardianEntity>> = dao.observeGuardians()
    val dangers: Flow<List<DangerPointEntity>> = dao.observeDangers()
    val crossings: Flow<List<CrossingEntity>> = dao.observeCrossings()

    suspend fun favoritesOnce() = dao.getFavorites()
    suspend fun takeoffsOnce() = dao.getTakeoffs()
    suspend fun camerasOnce() = dao.getCameras()
    suspend fun guardiansOnce() = dao.getGuardians()
    suspend fun dangersOnce() = dao.getDangers()

    suspend fun upsertFavorite(f: FavoriteEntity) = dao.upsertFavorite(f)
    suspend fun deleteFavorite(id: String) = dao.deleteFavorite(id)

    suspend fun upsertTakeoff(t: TakeoffPointEntity) = dao.upsertTakeoff(t)
    suspend fun deleteTakeoff(id: String) = dao.deleteTakeoff(id)

    suspend fun upsertCamera(c: CameraEntity) = dao.upsertCamera(c)
    suspend fun deleteCamera(id: String) = dao.deleteCamera(id)

    suspend fun upsertGuardian(g: GuardianEntity) = dao.upsertGuardian(g)
    suspend fun deleteGuardian(id: String) = dao.deleteGuardian(id)
    suspend fun updateGuardianPosition(id: String, lon: Double, lat: Double, ts: Long) =
        dao.updateGuardianPosition(id, lon, lat, ts)

    suspend fun upsertDanger(d: DangerPointEntity) = dao.upsertDanger(d)
    suspend fun deleteDanger(id: String) = dao.deleteDanger(id)

    suspend fun upsertCrossing(c: CrossingEntity) = dao.upsertCrossing(c)
    suspend fun deleteCrossing(id: String) = dao.deleteCrossing(id)
}

@Singleton
class PhotoRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.photoDao()
    val photos: Flow<List<PhotoEntity>> = dao.observeAll()

    fun byTower(towerId: String): Flow<List<PhotoEntity>> = dao.observeByTower(towerId)
    suspend fun byTowerOnce(towerId: String) = dao.getByTower(towerId)
    suspend fun allOnce() = dao.getAll()
    suspend fun search(kw: String) = dao.searchByKeyword(kw)
    suspend fun upsert(p: PhotoEntity) = dao.upsert(p)
    suspend fun delete(id: String) = dao.deleteById(id)
}

@Singleton
class OverlayRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.overlayDao()
    val overlays: Flow<List<CustomOverlayEntity>> = dao.observeAll()
    suspend fun allOnce() = dao.getAll()
    suspend fun upsert(o: CustomOverlayEntity) = dao.upsert(o)
    suspend fun delete(id: String) = dao.deleteById(id)
    suspend fun clear() = dao.deleteAll()
}

@Singleton
class BatchRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.batchDao()
    val batches: Flow<List<com.kunming.powerline.data.local.entity.ImportBatchEntity>> = dao.observeAll()
    suspend fun upsert(b: com.kunming.powerline.data.local.entity.ImportBatchEntity) = dao.upsert(b)
    suspend fun get(id: String) = dao.getById(id)
    suspend fun updateStatus(id: String, status: String) = dao.updateStatus(id, status)

    /** 5 分钟内可撤销本次导入批次 */
    suspend fun rollback(batchId: String): Boolean {
        val batch = dao.getById(batchId) ?: return false
        if (System.currentTimeMillis() - batch.importedAt > 5 * 60 * 1000L) return false
        val lineIds = batch.lineIds.split(",").filter { it.isNotBlank() }
        // 先删子表再删线路，避免外键约束问题
        lineIds.forEach { db.towerDao().deleteByLine(it) }
        lineIds.forEach { db.lineDao().deleteById(it) }
        db.cableDao().deleteByBatch(batchId)
        dao.updateStatus(batchId, "rolled_back")
        return true
    }
}
