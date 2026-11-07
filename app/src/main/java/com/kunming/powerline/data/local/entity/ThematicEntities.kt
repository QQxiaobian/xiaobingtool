package com.kunming.powerline.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 专题图层（KML / GeoJSON / GPX 导入的点、线、面要素集合）
 *
 * 与"架空线路"解耦：巡视点位、区域范围、通道、隐患等一切非线路要素都放这里，
 * 一个文件（或 KML 的一个 Folder）对应一个图层。
 */
@Entity(tableName = "thematic_layer")
data class ThematicLayerEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** point / line / polygon / mixed */
    val geometryType: String,
    /** ARGB 颜色 */
    val color: Int = 0xFF1565C0.toInt(),
    val visible: Boolean = true,
    val featureCount: Int = 0,
    /** kml / geojson / gpx */
    val sourceFormat: String = "kml",
    val fileName: String? = null,
    /** 图层备注，展示在名称下方（如"XX 变电站周边巡视范围"） */
    val remark: String? = null,
    val importBatchId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** 是否在地图上显示要素名称 / 备注（区域名） */
    @ColumnInfo(defaultValue = "1") val labelVisible: Boolean = true,
    /** 面填充透明度 0..1（批量可调，越小越不遮底图） */
    @ColumnInfo(defaultValue = "0.22") val fillOpacity: Float = 0.22f,
    /** 线宽（px） */
    @ColumnInfo(defaultValue = "2.2") val lineWidth: Float = 2.2f
) {
    /** 是否含面要素（决定是否给出透明度调节项） */
    val hasPolygon: Boolean
        get() = geometryType == "polygon" || geometryType == "mixed"

    /** 透明度百分比文案 */
    val opacityPercent: Int
        get() = (fillOpacity * 100f).toInt().coerceIn(0, 100)
}

/**
 * 专题要素（点 / 线 / 面）
 */
@Entity(
    tableName = "thematic_feature",
    foreignKeys = [
        ForeignKey(
            entity = ThematicLayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["layerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("layerId")]
)
data class ThematicFeatureEntity(
    @PrimaryKey val id: String,
    val layerId: String,
    val name: String? = null,
    /** point / line / polygon */
    val kind: String,
    /** GeoJSON geometry 原文，如 {"type":"Point","coordinates":[102.7,25.0]} */
    val geometryGeojson: String,
    /** 点要素坐标，或线/面的中心点，用于定位 */
    val lon: Double? = null,
    val lat: Double? = null,
    /** 原始属性（JSON），用于详情展示 */
    val propertiesJson: String? = null,
    val seq: Int = 0
)
