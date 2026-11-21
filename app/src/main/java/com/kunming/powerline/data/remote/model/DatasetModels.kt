package com.kunming.powerline.data.remote.model

import com.squareup.moshi.JsonClass

/**
 * NAS 数据服务器 / 本地 JSON 使用的数据集格式（powerline-dataset/1）。
 *
 * 一个数据集可同时包含「专题图层 + 要素」与「线路 / 杆塔台账」，
 * App 可上传到 NAS、从 NAS 下载、或导出成本地 JSON 文件离线流转。
 */
@JsonClass(generateAdapter = true)
data class DatasetMetaDto(
    val id: String = "",
    val name: String = "",
    /** layers / lines / mixed */
    val kind: String = "mixed",
    val version: Int = 1,
    val updatedAt: String = "",
    val layerCount: Int = 0,
    val featureCount: Int = 0,
    val lineCount: Int = 0,
    val towerCount: Int = 0,
    /** 1.6.4：收藏点 / 危险点 / 护线员等点位总数 */
    val pointCount: Int = 0,
    val sizeBytes: Long = 0L,
    val note: String? = null
)

@JsonClass(generateAdapter = true)
data class DatasetFeatureDto(
    val id: String = "",
    val layerId: String = "",
    val name: String? = null,
    /** point / line / polygon */
    val kind: String = "point",
    /** GeoJSON geometry 原文 */
    val geometryGeojson: String = "",
    val lon: Double? = null,
    val lat: Double? = null,
    val propertiesJson: String? = null,
    val seq: Int = 0
)

@JsonClass(generateAdapter = true)
data class DatasetLayerDto(
    val id: String = "",
    val name: String = "",
    /** point / line / polygon / mixed */
    val geometryType: String = "mixed",
    val color: Int = 0xFF1565C0.toInt(),
    val visible: Boolean = true,
    val labelVisible: Boolean = true,
    val fillOpacity: Float = 0.22f,
    val lineWidth: Float = 2.2f,
    val sourceFormat: String = "kml",
    val remark: String? = null,
    val features: List<DatasetFeatureDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DatasetBundleDto(
    val schema: String = SCHEMA,
    val id: String = "",
    val name: String = "",
    val kind: String = "mixed",
    val version: Int = 1,
    val updatedAt: String = "",
    val note: String? = null,
    val layers: List<DatasetLayerDto> = emptyList(),
    val ledger: PmsLedgerBundle = PmsLedgerBundle(),
    /** 1.6.4：点位类数据（收藏夹 / 危险点 / 护线员 / 监控 / 起飞点 / 交叉跨越 / 电缆 / 标注） */
    val points: DatasetPointsDto = DatasetPointsDto()
) {
    companion object {
        const val SCHEMA = "powerline-dataset/1"
        const val KIND_LAYERS = "layers"
        const val KIND_LINES = "lines"
        const val KIND_MIXED = "mixed"
    }
}

// ----------------------------------------------------------------------------
// 点位类数据（1.6.4：此前只同步专题图层与线路台账，收藏夹等点位全部丢失）
// ----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class DatasetFavoriteDto(
    val id: String = "",
    val name: String = "",
    val category: String = "默认",
    val lon: Double = 0.0,
    val lat: Double = 0.0,
    val note: String? = null,
    val createdAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class DatasetTakeoffDto(
    val id: String = "",
    val name: String = "",
    val lon: Double = 0.0,
    val lat: Double = 0.0,
    val altitude: Double? = null,
    val note: String? = null
)

@JsonClass(generateAdapter = true)
data class DatasetCameraDto(
    val id: String = "",
    val name: String = "",
    val lon: Double = 0.0,
    val lat: Double = 0.0,
    val rtspUrl: String = "",
    val status: String = "未知",
    val remark: String? = null
)

@JsonClass(generateAdapter = true)
data class DatasetGuardianDto(
    val id: String = "",
    val name: String = "",
    val phone: String? = null,
    /** 责任段 */
    val section: String? = null,
    val lon: Double? = null,
    val lat: Double? = null,
    val updatedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class DatasetDangerLayerDto(
    val id: String = "",
    val name: String = "",
    val fileName: String? = null,
    val lineName: String? = null,
    val visible: Boolean = true,
    val remark: String? = null,
    val createdAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class DatasetDangerDto(
    val id: String = "",
    val layerId: String? = null,
    val lineId: String? = null,
    val lineName: String? = null,
    val type: String = "危险点",
    val level: String = "Ⅲ",
    val measure: String? = null,
    val lon: Double = 0.0,
    val lat: Double = 0.0,
    val towerNumber: String? = null,
    val remark: String? = null,
    val seqNo: String? = null,
    val towerSpan: String? = null,
    val measuredAt: String? = null,
    val heightM: Double? = null,
    val spanM: Double? = null,
    val distSmallTowerM: Double? = null,
    val groundType: String? = null,
    val horizM: Double? = null,
    val vertM: Double? = null,
    val clearanceM: Double? = null,
    val defectLevel: String? = null,
    val safeDistanceM: Double? = null,
    val vegetationType: String? = null
)

@JsonClass(generateAdapter = true)
data class DatasetCrossingDto(
    val id: String = "",
    val lineId: String? = null,
    val lineName: String? = null,
    val crossType: String = "其他",
    val crossObject: String? = null,
    val distance: Double? = null,
    val lon: Double? = null,
    val lat: Double? = null,
    val towerNumber: String? = null,
    val remark: String? = null
)

@JsonClass(generateAdapter = true)
data class DatasetCableDto(
    val id: String = "",
    val name: String = "",
    val voltageLevel: Int = 110,
    val geometryGeojson: String = "",
    val startNode: String? = null,
    val endNode: String? = null,
    val lengthMeter: Double = 0.0,
    val remark: String? = null
)

@JsonClass(generateAdapter = true)
data class DatasetOverlayDto(
    val id: String = "",
    /** line / polygon / circle / rectangle / text */
    val type: String = "line",
    val name: String = "",
    val geometryGeojson: String = "",
    val color: Int = 0xFFB71C1C.toInt(),
    val textSize: Float = 14f,
    val extra: String? = null,
    val createdAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class DatasetPointsDto(
    val favorites: List<DatasetFavoriteDto> = emptyList(),
    val takeoffs: List<DatasetTakeoffDto> = emptyList(),
    val cameras: List<DatasetCameraDto> = emptyList(),
    val guardians: List<DatasetGuardianDto> = emptyList(),
    val dangerLayers: List<DatasetDangerLayerDto> = emptyList(),
    val dangers: List<DatasetDangerDto> = emptyList(),
    val crossings: List<DatasetCrossingDto> = emptyList(),
    val cables: List<DatasetCableDto> = emptyList(),
    val overlays: List<DatasetOverlayDto> = emptyList()
) {
    /** 点位总数（不含危险点图层这类容器） */
    val total: Int
        get() = favorites.size + takeoffs.size + cameras.size + guardians.size +
            dangers.size + crossings.size + cables.size + overlays.size

    val isEmpty: Boolean get() = total == 0 && dangerLayers.isEmpty()

    /** 人类可读分部统计，用于导出结果提示 */
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (favorites.isNotEmpty()) parts += "${favorites.size} 收藏点"
        if (dangerLayers.isNotEmpty()) parts += "${dangerLayers.size} 危险点图层/${dangers.size} 危险点"
        else if (dangers.isNotEmpty()) parts += "${dangers.size} 危险点"
        if (takeoffs.isNotEmpty()) parts += "${takeoffs.size} 起飞点"
        if (cameras.isNotEmpty()) parts += "${cameras.size} 视频监控"
        if (guardians.isNotEmpty()) parts += "${guardians.size} 护线员"
        if (crossings.isNotEmpty()) parts += "${crossings.size} 交叉跨越"
        if (cables.isNotEmpty()) parts += "${cables.size} 电缆"
        if (overlays.isNotEmpty()) parts += "${overlays.size} 标注"
        return if (parts.isEmpty()) "无点位" else parts.joinToString("、")
    }
}

/** 纯 Kotlin 统计与校验，供 App / 单元测试复用 */
object DatasetStats {
    data class Counts(
        val layers: Int,
        val features: Int,
        val lines: Int,
        val towers: Int,
        /** 1.6.4：点位总数（收藏 / 危险点 / 护线员 …），默认 0 兼容老数据集 */
        val points: Int = 0
    )

    fun counts(b: DatasetBundleDto): Counts = Counts(
        layers = b.layers.size,
        features = b.layers.sumOf { it.features.size },
        lines = b.ledger.lines.size,
        towers = b.ledger.towers.size,
        points = b.points.total
    )

    /** 由内容推断 kind，避免上传时手工选错 */
    fun inferKind(b: DatasetBundleDto): String {
        val c = counts(b)
        val hasLayers = c.layers > 0 || c.features > 0 || c.points > 0
        val hasLines = c.lines > 0 || c.towers > 0
        return when {
            hasLayers && hasLines -> DatasetBundleDto.KIND_MIXED
            hasLayers -> DatasetBundleDto.KIND_LAYERS
            else -> DatasetBundleDto.KIND_LINES
        }
    }

    fun summary(b: DatasetBundleDto): String {
        val c = counts(b)
        val sb = StringBuilder()
        sb.append("${c.layers} 个图层 / ${c.features} 个要素 / ${c.lines} 条线路 / ${c.towers} 基杆塔")
        if (c.points > 0) sb.append(" / ${c.points} 个点位（${b.points.summary()}）")
        return sb.toString()
    }

    /** 名称不能为空、不能含路径分隔符（会用于 NAS 端文件名） */
    fun validName(name: String): Boolean =
        name.trim().isNotEmpty() && !name.contains('/') && !name.contains('\\')

    fun metaOf(b: DatasetBundleDto, sizeBytes: Long = 0L): DatasetMetaDto {
        val c = counts(b)
        return DatasetMetaDto(
            id = b.id, name = b.name, kind = b.kind, version = b.version,
            updatedAt = b.updatedAt, layerCount = c.layers, featureCount = c.features,
            lineCount = c.lines, towerCount = c.towers, pointCount = c.points,
            sizeBytes = sizeBytes, note = b.note
        )
    }
}
