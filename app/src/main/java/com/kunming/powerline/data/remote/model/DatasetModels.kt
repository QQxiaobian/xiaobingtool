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
    val ledger: PmsLedgerBundle = PmsLedgerBundle()
) {
    companion object {
        const val SCHEMA = "powerline-dataset/1"
        const val KIND_LAYERS = "layers"
        const val KIND_LINES = "lines"
        const val KIND_MIXED = "mixed"
    }
}

/** 纯 Kotlin 统计与校验，供 App / 单元测试复用 */
object DatasetStats {
    data class Counts(
        val layers: Int,
        val features: Int,
        val lines: Int,
        val towers: Int
    )

    fun counts(b: DatasetBundleDto): Counts = Counts(
        layers = b.layers.size,
        features = b.layers.sumOf { it.features.size },
        lines = b.ledger.lines.size,
        towers = b.ledger.towers.size
    )

    /** 由内容推断 kind，避免上传时手工选错 */
    fun inferKind(b: DatasetBundleDto): String {
        val c = counts(b)
        val hasLayers = c.layers > 0 || c.features > 0
        val hasLines = c.lines > 0 || c.towers > 0
        return when {
            hasLayers && hasLines -> DatasetBundleDto.KIND_MIXED
            hasLayers -> DatasetBundleDto.KIND_LAYERS
            else -> DatasetBundleDto.KIND_LINES
        }
    }

    fun summary(b: DatasetBundleDto): String {
        val c = counts(b)
        return "${c.layers} 个图层 / ${c.features} 个要素 / ${c.lines} 条线路 / ${c.towers} 基杆塔"
    }

    /** 名称不能为空、不能含路径分隔符（会用于 NAS 端文件名） */
    fun validName(name: String): Boolean =
        name.trim().isNotEmpty() && !name.contains('/') && !name.contains('\\')

    fun metaOf(b: DatasetBundleDto, sizeBytes: Long = 0L): DatasetMetaDto {
        val c = counts(b)
        return DatasetMetaDto(
            id = b.id, name = b.name, kind = b.kind, version = b.version,
            updatedAt = b.updatedAt, layerCount = c.layers, featureCount = c.features,
            lineCount = c.lines, towerCount = c.towers, sizeBytes = sizeBytes, note = b.note
        )
    }
}
