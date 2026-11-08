package com.kunming.powerline.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kunming.powerline.data.local.dao.BatchDao
import com.kunming.powerline.data.local.dao.CableDao
import com.kunming.powerline.data.local.dao.LineDao
import com.kunming.powerline.data.local.dao.MiscDao
import com.kunming.powerline.data.local.dao.OverlayDao
import com.kunming.powerline.data.local.dao.PhotoDao
import com.kunming.powerline.data.local.dao.PointDao
import com.kunming.powerline.data.local.dao.ThematicDao
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
import com.kunming.powerline.data.local.entity.ThematicFeatureEntity
import com.kunming.powerline.data.local.entity.ThematicLayerEntity
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
        OfflinePackEntity::class,
        ThematicLayerEntity::class,
        ThematicFeatureEntity::class
    ],
    version = 4,
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
    abstract fun thematicDao(): ThematicDao

    companion object {
        const val NAME = "powerline.db"

        /**
         * v1 → v2：新增专题图层两张表（不破坏已有线路/杆塔数据）
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `thematic_layer` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `geometryType` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `visible` INTEGER NOT NULL,
                        `featureCount` INTEGER NOT NULL,
                        `sourceFormat` TEXT NOT NULL,
                        `fileName` TEXT,
                        `remark` TEXT,
                        `importBatchId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `thematic_feature` (
                        `id` TEXT NOT NULL,
                        `layerId` TEXT NOT NULL,
                        `name` TEXT,
                        `kind` TEXT NOT NULL,
                        `geometryGeojson` TEXT NOT NULL,
                        `lon` REAL,
                        `lat` REAL,
                        `propertiesJson` TEXT,
                        `seq` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`layerId`) REFERENCES `thematic_layer`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_thematic_feature_layerId` ON `thematic_feature` (`layerId`)"
                )
            }
        }

        /**
         * v2 → v3：专题图层增加显示控制（名称开关 / 面透明度 / 线宽）
         *
         * 全部带 DEFAULT，老数据自动取默认值：名称照旧显示、面按 22% 半透明。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `thematic_layer` ADD COLUMN `labelVisible` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE `thematic_layer` ADD COLUMN `fillOpacity` REAL NOT NULL DEFAULT 0.22"
                )
                db.execSQL(
                    "ALTER TABLE `thematic_layer` ADD COLUMN `lineWidth` REAL NOT NULL DEFAULT 2.2"
                )
            }
        }

        /**
         * v3 → v4：自定义地图源（online_map）扩展为可真正参与渲染的地图服务器配置。
         *
         * 全部新列带 DEFAULT（可空列无默认值），老版本升级后自动取：
         * 256px 瓦片 / XYZ 方案 / 不透明 / 不作为主底图 / 无版权 / 排序 0。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `online_map` ADD COLUMN `tileSize` INTEGER NOT NULL DEFAULT 256")
                db.execSQL("ALTER TABLE `online_map` ADD COLUMN `scheme` TEXT")
                db.execSQL("ALTER TABLE `online_map` ADD COLUMN `opacity` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `online_map` ADD COLUMN `asBase` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `online_map` ADD COLUMN `attribution` TEXT")
                db.execSQL("ALTER TABLE `online_map` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
