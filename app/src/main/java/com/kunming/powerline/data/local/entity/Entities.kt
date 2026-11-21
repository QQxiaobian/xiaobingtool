package com.kunming.powerline.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 架空线路
 */
@Entity(tableName = "line")
data class LineEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 电压等级：500 / 220 / 110 / 35 (kV) */
    val voltageLevel: Int,
    val startStation: String? = null,
    val endStation: String? = null,
    val inServiceDate: String? = null,
    val remark: String? = null,
    /** 线路几何 GeoJSON（LineString），可为空（空线路） */
    val geometryGeojson: String? = null,
    /** 空线路标记：创建后尚未添加杆塔 */
    val isEmptyLine: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** 归属导入批次，用于 5 分钟内撤销 */
    val importBatchId: String? = null,
    /** 地图组（按区域/单位分组） */
    val groupId: Long? = null
)

/**
 * 杆塔
 */
@Entity(
    tableName = "tower",
    foreignKeys = [
        ForeignKey(
            entity = LineEntity::class,
            parentColumns = ["id"],
            childColumns = ["lineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lineId"), Index(value = ["lineId", "towerNumber"]), Index("towerNumber")]
)
data class TowerEntity(
    @PrimaryKey val id: String,
    val lineId: String,
    val towerNumber: String,
    val lon: Double,
    val lat: Double,
    val elevation: Double? = null,
    /** 杆塔类型：耐张 / 直线 / 转角 / 终端 */
    val type: String? = null,
    /** 同塔多回编号，如 Ⅰ/Ⅱ/Ⅲ；单回为 1 */
    val circuitNo: Int = 1,
    val circuitCount: Int = 1,
    /** 显示偏移（仅视觉，不改真实坐标），单位：米 */
    val visualOffsetLon: Double = 0.0,
    val visualOffsetLat: Double = 0.0,
    /** 在线路中的排序号，用于连线排序 */
    val seq: Int = 0,
    val remark: String? = null
)

/**
 * 输电电缆
 */
@Entity(tableName = "cable", indices = [Index("name")])
data class CableEntity(
    @PrimaryKey val id: String,
    val name: String,
    val voltageLevel: Int,
    /** MultiLineString / LineString GeoJSON */
    val geometryGeojson: String,
    val startNode: String? = null,
    val endNode: String? = null,
    val lengthMeter: Double = 0.0,
    val remark: String? = null,
    val importBatchId: String? = null
)

/**
 * 交叉跨越
 */
@Entity(tableName = "crossing", indices = [Index("lineId")])
data class CrossingEntity(
    @PrimaryKey val id: String,
    val lineId: String? = null,
    /** 跨越对象类型：电力线 / 通信线 / 道路 / 铁路 / 河流 / 建筑物 */
    val crossType: String,
    val crossObject: String? = null,
    /** 交叉距离（米），可自动计算 */
    val distance: Double? = null,
    val lon: Double? = null,
    val lat: Double? = null,
    val towerNumber: String? = null,
    val remark: String? = null
)

/**
 * 视频监控
 */
@Entity(tableName = "camera_point")
data class CameraEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val lon: Double,
    val lat: Double,
    val rtspUrl: String = "",
    /** 在线 / 离线 / 未知 */
    val status: String = "未知",
    val remark: String? = null
)

/**
 * 护线员（实时位置）
 */
@Entity(tableName = "guardian", indices = [Index("name")])
data class GuardianEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String? = null,
    val responsibilitySection: String? = null,
    val lastLon: Double? = null,
    val lastLat: Double? = null,
    val updatedAt: Long = 0L
)

/**
 * 危险点数据集（1.6.4）
 *
 * 一个导入的 CSV / 表格就是一个「危险点图层」，可单独控制显示 / 隐藏。
 * 过去危险点是一张平铺表，几十条报告混在一起无法按线路、按批次区分，
 * 现在按来源文件分组，既保留批次边界，也能整层开关。
 */
@Entity(tableName = "danger_layer")
data class DangerLayerEntity(
    @PrimaryKey val id: String,
    /** 图层名，默认取文件名（去扩展名） */
    val name: String,
    val fileName: String? = null,
    /** 由「杆塔区间」或文件名推断的线路名，仅用于展示与筛选 */
    val lineName: String? = null,
    val visible: Boolean = true,
    val featureCount: Int = 0,
    val sourceFormat: String = "csv",
    val remark: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 危险点（净空危险点 / 施工隐患等）
 *
 * 1.6.4 起支持从「净空危险点报告汇总表」CSV 导入，字段按报告原样保留：
 * 杆塔区间、测量时间、高度、档距、距小号塔距离、地物类型、
 * 水平 / 垂直 / 净空实测距离、缺陷等级、安全距离、植被类型。
 *
 * `type` / `level` 为 1.6.3 及以前的老字段，导入时同步写入
 * （type = 地物类型，level = 缺陷等级），保证旧界面、旧搜索结果不失效。
 */
@Entity(
    tableName = "danger_point",
    indices = [Index("lineId"), Index("layerId")]
)
data class DangerPointEntity(
    @PrimaryKey val id: String,
    val lineId: String? = null,
    /** 所属危险点图层；为空表示 1.6.3 及以前的手工点 / 旧数据 */
    val layerId: String? = null,
    val type: String,
    /** 等级：Ⅰ/Ⅱ/Ⅲ（老数据）或 重大/紧急/一般（报告导入） */
    val level: String = "Ⅲ",
    val measure: String? = null,
    val lon: Double,
    val lat: Double,
    val towerNumber: String? = null,
    val remark: String? = null,
    // ---- 净空危险点报告字段 ----
    /** 报告序号 */
    val seqNo: String? = null,
    /** 杆塔区间，如 01—02 */
    val towerSpan: String? = null,
    /** 测量时间（原文，如 2024/7/5） */
    val measuredAt: String? = null,
    /** 导线高度（m） */
    val heightM: Double? = null,
    /** 档距（m） */
    val spanM: Double? = null,
    /** 距小号塔距离（m） */
    val distSmallTowerM: Double? = null,
    /** 地物类型：植被 / 建筑物 / 道路 … */
    val groundType: String? = null,
    /** 水平距离（m） */
    val horizM: Double? = null,
    /** 垂直距离（m） */
    val vertM: Double? = null,
    /** 净空实测距离（m）—— 筛选与默认标注的主字段 */
    val clearanceM: Double? = null,
    /** 缺陷等级：重大 / 紧急 / 一般 */
    val defectLevel: String? = null,
    /** 安规要求的安全距离（m） */
    val safeDistanceM: Double? = null,
    /** 植被类型（无此列时回退为地物类型） */
    val vegetationType: String? = null
) {
    /** 地图标注用：地物类型 + 净空距离，如「植被 6.5m」 */
    val mapLabel: String
        get() {
            val g = (groundType ?: vegetationType ?: type).ifBlank { "危险点" }
            val c = clearanceM
            return if (c != null) "$g ${fmt1(c)}m" else g
        }

    /** 缺陷等级色（重大红 / 紧急橙 / 一般黄） */
    val levelColor: String
        get() = when ((defectLevel ?: level).trim()) {
            "重大", "Ⅰ", "1", "I" -> "#D32F2F"
            "紧急", "Ⅱ", "2", "II" -> "#F57C00"
            "一般", "Ⅲ", "3", "III" -> "#FBC02D"
            else -> "#F57C00"
        }

    private fun fmt1(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", v)
}

/**
 * 收藏点
 */
@Entity(tableName = "favorite", indices = [Index("name")])
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "默认",
    val lon: Double,
    val lat: Double,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 无人机起飞点
 */
@Entity(tableName = "takeoff_point", indices = [Index("name")])
data class TakeoffPointEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lon: Double,
    val lat: Double,
    val altitude: Double? = null,
    val note: String? = null
)

/**
 * 现场照片（挂接杆塔）
 */
@Entity(tableName = "photo", indices = [Index("towerId"), Index("keywords")])
data class PhotoEntity(
    @PrimaryKey val id: String,
    val towerId: String? = null,
    val filePath: String,
    val keywords: String = "",
    val takenAt: Long = System.currentTimeMillis(),
    val lon: Double? = null,
    val lat: Double? = null,
    val remark: String? = null
)

/**
 * 导入批次（支持 5 分钟内撤销）
 */
@Entity(tableName = "import_batch")
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    /** excel / pms / kml / geojson / gpx / shp / manual / draw */
    val sourceType: String,
    val fileName: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
    /** success / partial / failed / rolled_back */
    val status: String = "success",
    val errorRows: Int = 0,
    /** 逗号分隔的线路 id */
    val lineIds: String = "",
    val towerCount: Int = 0,
    val remark: String? = null
)

/**
 * 自定义绘制结果（标注工具栏）
 */
@Entity(tableName = "custom_overlay")
data class CustomOverlayEntity(
    @PrimaryKey val id: String,
    /** line / polygon / circle / rectangle / text */
    val type: String,
    val name: String,
    val geometryGeojson: String,
    val color: Int = 0xFFB71C1C.toInt(),
    val textSize: Float = 14f,
    val extra: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 自定义地图组（按区域/单位）
 */
@Entity(tableName = "map_group")
data class MapGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val area: String? = null,
    val unit: String? = null,
    val sortOrder: Int = 0
)

/**
 * 轨迹点（后台持续定位记录）
 */
@Entity(tableName = "track_point", indices = [Index("ts")])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lon: Double,
    val lat: Double,
    val speed: Float,
    val accuracy: Float,
    val ts: Long,
    val roomId: String? = null
)

/**
 * 在线地图（用户自定义底图源 / 自定义地图服务器）
 *
 * 1.2.0 起可真正参与渲染：多个源可叠加，其中一个可设为「主底图」替代内置底图。
 * 全部新增列均带 DEFAULT，v3→v4 用 ALTER TABLE 平滑升级，不影响已有数据。
 */
@Entity(tableName = "online_map")
data class OnlineMapEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** raster tiles 模板 URL，必须含 {z} / {x} / {y}；可选 {s} 子域占位符 */
    val tileUrl: String,
    val minZoom: Int = 3,
    val maxZoom: Int = 18,
    /** 逗号分隔的子域名，配合 URL 里的 {s} 使用，如 "a,b,c" */
    val subdomains: String = "",
    /** 是否启用（未启用的源不参与渲染） */
    val enabled: Boolean = true,
    /** 瓦片尺寸，通常 256；部分高清源为 512 */
    @androidx.room.ColumnInfo(defaultValue = "256") val tileSize: Int = 256,
    /** xyz（默认）或 tms（Y 轴翻转，{y} 会改写为 {-y}） */
    val scheme: String? = null,
    /** 叠加不透明度 0~1（作为主底图时固定为 1） */
    @androidx.room.ColumnInfo(defaultValue = "1.0") val opacity: Float = 1f,
    /** 是否替代内置底图作为主底图；最多一个为 true */
    @androidx.room.ColumnInfo(defaultValue = "0") val asBase: Boolean = false,
    /** 版权 / 数据来源说明 */
    val attribution: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val sortOrder: Int = 0
) {
    val isTms: Boolean get() = scheme.equals("tms", ignoreCase = true)
}

/**
 * 离线瓦片包（mbtiles 记录）
 */
@Entity(tableName = "offline_pack")
data class OfflinePackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filePath: String,
    /** 经纬度范围：minLon,minLat,maxLon,maxLat */
    val bounds: String = "",
    val minZoom: Int = 10,
    val maxZoom: Int = 16,
    val sizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
