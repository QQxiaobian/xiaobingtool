package com.kunming.powerline.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.kunming.powerline.data.local.entity.CableEntity
import com.kunming.powerline.data.local.entity.CameraEntity
import com.kunming.powerline.data.local.entity.CrossingEntity
import com.kunming.powerline.data.local.entity.CustomOverlayEntity
import com.kunming.powerline.data.local.entity.DangerPointEntity
import com.kunming.powerline.data.local.entity.FavoriteEntity
import com.kunming.powerline.data.local.entity.GuardianEntity
import com.kunming.powerline.data.local.entity.ImportBatchEntity
import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.data.local.entity.MapGroupEntity
import com.kunming.powerline.data.local.entity.OfflinePackEntity
import com.kunming.powerline.data.local.entity.OnlineMapEntity
import com.kunming.powerline.data.local.entity.PhotoEntity
import com.kunming.powerline.data.local.entity.TakeoffPointEntity
import com.kunming.powerline.data.local.entity.TowerEntity
import com.kunming.powerline.data.local.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LineDao {

    @Query("SELECT * FROM line ORDER BY voltageLevel DESC, name ASC")
    fun observeAll(): Flow<List<LineEntity>>

    @Query("SELECT * FROM line ORDER BY voltageLevel DESC, name ASC")
    suspend fun getAll(): List<LineEntity>

    @Query("SELECT * FROM line WHERE id = :id")
    suspend fun getById(id: String): LineEntity?

    @Query("SELECT * FROM line WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): LineEntity?

    @Query("SELECT * FROM line WHERE name LIKE '%' || :kw || '%'")
    suspend fun searchByName(kw: String): List<LineEntity>

    @Query("SELECT COUNT(*) FROM line")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(line: LineEntity)

    @Upsert
    suspend fun upsertAll(lines: List<LineEntity>)

    @Query("UPDATE line SET geometryGeojson = :geojson, isEmptyLine = 0 WHERE id = :id")
    suspend fun updateGeometry(id: String, geojson: String?)

    @Delete
    suspend fun delete(line: LineEntity)

    @Query("DELETE FROM line WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM line WHERE importBatchId = :batchId")
    suspend fun deleteByBatch(batchId: String)

    @Query("DELETE FROM line")
    suspend fun deleteAll()
}

@Dao
interface TowerDao {

    @Query("SELECT * FROM tower WHERE lineId = :lineId ORDER BY seq ASC")
    fun observeByLine(lineId: String): Flow<List<TowerEntity>>

    @Query("SELECT * FROM tower ORDER BY lineId, seq ASC")
    fun observeAll(): Flow<List<TowerEntity>>

    @Query("SELECT * FROM tower ORDER BY lineId, seq ASC")
    suspend fun getAll(): List<TowerEntity>

    @Query("SELECT * FROM tower WHERE lineId = :lineId ORDER BY seq ASC")
    suspend fun getByLine(lineId: String): List<TowerEntity>

    @Query("SELECT * FROM tower WHERE id = :id")
    suspend fun getById(id: String): TowerEntity?

    @Query("SELECT * FROM tower WHERE towerNumber LIKE '%' || :kw || '%' OR remark LIKE '%' || :kw || '%' LIMIT 50")
    suspend fun fuzzySearch(kw: String): List<TowerEntity>

    @Query("SELECT * FROM tower WHERE lineId = :lineId AND towerNumber = :number LIMIT 1")
    suspend fun findByNumber(lineId: String, number: String): TowerEntity?

    @Query("SELECT COUNT(*) FROM tower")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tower WHERE lineId = :lineId")
    suspend fun countByLine(lineId: String): Int

    @Upsert
    suspend fun upsert(tower: TowerEntity)

    @Upsert
    suspend fun upsertAll(towers: List<TowerEntity>)

    @Update
    suspend fun update(tower: TowerEntity)

    @Query("UPDATE tower SET visualOffsetLon = :dlon, visualOffsetLat = :dlat WHERE id = :id")
    suspend fun updateVisualOffset(id: String, dlon: Double, dlat: Double)

    @Query("DELETE FROM tower WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tower WHERE lineId = :lineId")
    suspend fun deleteByLine(lineId: String)

    @Query("DELETE FROM tower")
    suspend fun deleteAll()
}

@Dao
interface CableDao {
    @Query("SELECT * FROM cable ORDER BY name")
    fun observeAll(): Flow<List<CableEntity>>

    @Query("SELECT * FROM cable ORDER BY name")
    suspend fun getAllOnce(): List<CableEntity>

    @Query("SELECT COUNT(*) FROM cable")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM cable WHERE name LIKE '%' || :kw || '%'")
    suspend fun searchByName(kw: String): List<CableEntity>

    @Upsert
    suspend fun upsert(cable: CableEntity)

    @Query("DELETE FROM cable WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cable WHERE importBatchId = :batchId")
    suspend fun deleteByBatch(batchId: String)

    @Query("DELETE FROM cable")
    suspend fun deleteAll()
}

@Dao
interface PointDao {
    // ---- 收藏点 ----
    @Query("SELECT * FROM favorite ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorite ORDER BY createdAt DESC")
    suspend fun getFavorites(): List<FavoriteEntity>

    @Upsert suspend fun upsertFavorite(f: FavoriteEntity)
    @Query("DELETE FROM favorite WHERE id = :id") suspend fun deleteFavorite(id: String)

    // ---- 起飞点 ----
    @Query("SELECT * FROM takeoff_point ORDER BY name")
    fun observeTakeoffs(): Flow<List<TakeoffPointEntity>>

    @Query("SELECT * FROM takeoff_point ORDER BY name")
    suspend fun getTakeoffs(): List<TakeoffPointEntity>

    @Upsert suspend fun upsertTakeoff(t: TakeoffPointEntity)
    @Query("DELETE FROM takeoff_point WHERE id = :id") suspend fun deleteTakeoff(id: String)

    // ---- 视频监控 ----
    @Query("SELECT * FROM camera_point")
    fun observeCameras(): Flow<List<CameraEntity>>

    @Query("SELECT * FROM camera_point")
    suspend fun getCameras(): List<CameraEntity>

    @Upsert suspend fun upsertCamera(c: CameraEntity)
    @Query("DELETE FROM camera_point WHERE id = :id") suspend fun deleteCamera(id: String)

    // ---- 护线员 ----
    @Query("SELECT * FROM guardian ORDER BY name")
    fun observeGuardians(): Flow<List<GuardianEntity>>

    @Query("SELECT * FROM guardian ORDER BY name")
    suspend fun getGuardians(): List<GuardianEntity>

    @Upsert suspend fun upsertGuardian(g: GuardianEntity)
    @Query("DELETE FROM guardian WHERE id = :id") suspend fun deleteGuardian(id: String)
    @Query("UPDATE guardian SET lastLon = :lon, lastLat = :lat, updatedAt = :ts WHERE id = :id")
    suspend fun updateGuardianPosition(id: String, lon: Double, lat: Double, ts: Long)

    // ---- 危险点 ----
    @Query("SELECT * FROM danger_point")
    fun observeDangers(): Flow<List<DangerPointEntity>>

    @Query("SELECT * FROM danger_point")
    suspend fun getDangers(): List<DangerPointEntity>

    @Upsert suspend fun upsertDanger(d: DangerPointEntity)
    @Query("DELETE FROM danger_point WHERE id = :id") suspend fun deleteDanger(id: String)

    // ---- 交叉跨越 ----
    @Query("SELECT * FROM crossing")
    fun observeCrossings(): Flow<List<CrossingEntity>>

    @Query("SELECT * FROM crossing")
    suspend fun getCrossings(): List<CrossingEntity>

    @Upsert suspend fun upsertCrossing(c: CrossingEntity)
    @Query("DELETE FROM crossing WHERE id = :id") suspend fun deleteCrossing(id: String)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo ORDER BY takenAt DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photo WHERE towerId = :towerId ORDER BY takenAt DESC")
    fun observeByTower(towerId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photo WHERE towerId = :towerId ORDER BY takenAt DESC")
    suspend fun getByTower(towerId: String): List<PhotoEntity>

    @Query("SELECT * FROM photo ORDER BY takenAt DESC")
    suspend fun getAll(): List<PhotoEntity>

    @Query("SELECT * FROM photo WHERE keywords LIKE '%' || :kw || '%' LIMIT 100")
    suspend fun searchByKeyword(kw: String): List<PhotoEntity>

    @Upsert suspend fun upsert(p: PhotoEntity)
    @Query("DELETE FROM photo WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface OverlayDao {
    @Query("SELECT * FROM custom_overlay ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomOverlayEntity>>

    @Query("SELECT * FROM custom_overlay ORDER BY createdAt DESC")
    suspend fun getAll(): List<CustomOverlayEntity>

    @Upsert suspend fun upsert(o: CustomOverlayEntity)
    @Query("DELETE FROM custom_overlay WHERE id = :id") suspend fun deleteById(id: String)
    @Query("DELETE FROM custom_overlay") suspend fun deleteAll()
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM import_batch ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batch WHERE id = :id")
    suspend fun getById(id: String): ImportBatchEntity?

    @Upsert suspend fun upsert(batch: ImportBatchEntity)

    @Query("UPDATE import_batch SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}

@Dao
interface MiscDao {
    // ---- 轨迹 ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(p: TrackPointEntity): Long

    @Query("SELECT * FROM track_point WHERE ts >= :since ORDER BY ts ASC")
    suspend fun getTrackSince(since: Long): List<TrackPointEntity>

    @Query("DELETE FROM track_point WHERE ts < :before")
    suspend fun purgeTrackBefore(before: Long)

    // ---- 地图组 ----
    @Query("SELECT * FROM map_group ORDER BY sortOrder")
    fun observeGroups(): Flow<List<MapGroupEntity>>

    @Upsert suspend fun upsertGroup(g: MapGroupEntity): Long
    @Query("DELETE FROM map_group WHERE id = :id") suspend fun deleteGroup(id: Long)

    // ---- 在线地图（自定义地图源 / 自定义地图服务器） ----
    @Query("SELECT * FROM online_map ORDER BY sortOrder ASC, name ASC")
    fun observeOnlineMaps(): Flow<List<OnlineMapEntity>>

    @Query("SELECT * FROM online_map ORDER BY sortOrder ASC, name ASC")
    suspend fun getOnlineMaps(): List<OnlineMapEntity>

    @Upsert suspend fun upsertOnlineMap(m: OnlineMapEntity)
    @Query("DELETE FROM online_map WHERE id = :id") suspend fun deleteOnlineMap(id: String)

    @Query("UPDATE online_map SET enabled = :enabled WHERE id = :id")
    suspend fun setOnlineMapEnabled(id: String, enabled: Boolean)

    /** 主底图互斥：先把全部置 false，再把指定源置 true */
    @Query("UPDATE online_map SET asBase = 0")
    suspend fun clearBaseFlags()

    @Query("UPDATE online_map SET asBase = 1 WHERE id = :id")
    suspend fun setAsBase(id: String)

    // ---- 离线包 ----
    @Query("SELECT * FROM offline_pack ORDER BY createdAt DESC")
    fun observeOfflinePacks(): Flow<List<OfflinePackEntity>>

    @Upsert suspend fun upsertOfflinePack(p: OfflinePackEntity)
    @Query("DELETE FROM offline_pack WHERE id = :id") suspend fun deleteOfflinePack(id: String)
}

/**
 * 组合事务操作
 */
@Dao
interface TransactionDao {
    @Transaction
    suspend fun replaceTowersForLine(lineDao: LineDao, towerDao: TowerDao, line: LineEntity, towers: List<TowerEntity>) {
        towerDao.deleteByLine(line.id)
        lineDao.upsert(line)
        towerDao.upsertAll(towers)
    }
}
