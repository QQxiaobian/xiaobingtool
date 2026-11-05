package com.kunming.powerline.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kunming.powerline.data.local.dao.BatchDao
import com.kunming.powerline.data.local.dao.CableDao
import com.kunming.powerline.data.local.dao.LineDao
import com.kunming.powerline.data.local.dao.MiscDao
import com.kunming.powerline.data.local.dao.OverlayDao
import com.kunming.powerline.data.local.dao.PhotoDao
import com.kunming.powerline.data.local.dao.PointDao
import com.kunming.powerline.data.local.dao.TowerDao
import com.kunming.powerline.data.local.dao.TransactionDao
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

@Database(
    entities = [
        LineEntity::class,
        TowerEntity::class,
        CableEntity::class,
        CrossingEntity::class,
        CameraEntity::class,
        GuardianEntity::class,
        DangerPointEntity::class,
        FavoriteEntity::class,
        TakeoffPointEntity::class,
        PhotoEntity::class,
        ImportBatchEntity::class,
        CustomOverlayEntity::class,
        MapGroupEntity::class,
        TrackPointEntity::class,
        OnlineMapEntity::class,
        OfflinePackEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lineDao(): LineDao
    abstract fun towerDao(): TowerDao
    abstract fun cableDao(): CableDao
    abstract fun pointDao(): PointDao
    abstract fun photoDao(): PhotoDao
    abstract fun overlayDao(): OverlayDao
    abstract fun batchDao(): BatchDao
    abstract fun miscDao(): MiscDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "powerline.db"
    }
}
