package com.kunming.powerline.data.repository

import com.kunming.powerline.data.local.dao.ThematicDao
import com.kunming.powerline.data.local.entity.ThematicFeatureEntity
import com.kunming.powerline.data.local.entity.ThematicLayerEntity
import com.kunming.powerline.data.parser.ImportLayer
import com.kunming.powerline.data.parser.ThematicParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 专题图层仓库：点 / 线 / 面图层的持久化与查询
 */
@Singleton
class ThematicRepository @Inject constructor(
    private val dao: ThematicDao
) {
    val layers: Flow<List<ThematicLayerEntity>> = dao.observeLayers()
    val features: Flow<List<ThematicFeatureEntity>> = dao.observeFeatures()

    fun featuresOf(layerId: String): Flow<List<ThematicFeatureEntity>> = dao.observeFeaturesByLayer(layerId)

    /** 导入结果入库，返回 (图层数, 要素数) */
    suspend fun save(
        result: ThematicParseResult,
        fileName: String?,
        batchId: String? = null
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var layerCount = 0
        var featureCount = 0
        result.layers.forEachIndexed { layerIdx, src ->
            val layerId = newId("TL")
            val layer = ThematicLayerEntity(
                id = layerId,
                name = uniqueName(src.name.ifBlank { "未命名图层" }),
                geometryType = src.geometryType,
                color = palette[(baseIndex + layerIdx) % palette.size],
                featureCount = src.features.size,
                sourceFormat = result.format.ifBlank { "kml" },
                fileName = fileName,
                importBatchId = batchId
            )
            dao.upsertLayer(layer)
            layerCount++

            val list = src.features.mapIndexed { i, f ->
                ThematicFeatureEntity(
                    id = newId("TF"),
                    layerId = layerId,
                    name = f.name,
                    kind = f.kind,
                    geometryGeojson = f.geometryJson,
                    lon = f.lon,
                    lat = f.lat,
                    propertiesJson = f.properties.takeIf { it.isNotEmpty() }?.let { m ->
                        m.entries.joinToString(",", "{", "}") { (k, v) ->
                            "\"${k.replace("\"", "\\\"")}\":\"${v.replace("\"", "\\\"")}\""
                        }
                    },
                    seq = i
                )
            }
            if (list.isNotEmpty()) {
                dao.upsertFeatures(list)
                featureCount += list.size
            }
        }
        layerCount to featureCount
    }

    /** 同名图层自动加序号，避免列表中无法区分 */
    private suspend fun uniqueName(name: String): String {
        val existing = dao.getLayers().map { it.name }.toSet()
        if (name !in existing) return name
        var i = 2
        while ("$name ($i)" in existing) i++
        return "$name ($i)"
    }

    suspend fun setVisible(id: String, visible: Boolean) = withContext(Dispatchers.IO) {
        dao.setVisible(id, visible)
    }

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        dao.rename(id, name)
    }

    suspend fun setRemark(id: String, remark: String?) = withContext(Dispatchers.IO) {
        dao.setRemark(id, remark?.takeIf { it.isNotBlank() })
    }

    suspend fun setLabelVisible(id: String, visible: Boolean) = withContext(Dispatchers.IO) {
        dao.setLabelVisible(id, visible)
    }

    suspend fun setFillOpacity(id: String, opacity: Float) = withContext(Dispatchers.IO) {
        dao.setFillOpacity(id, opacity.coerceIn(MIN_OPACITY, MAX_OPACITY))
    }

    suspend fun setLineWidth(id: String, width: Float) = withContext(Dispatchers.IO) {
        dao.setLineWidth(id, width.coerceIn(MIN_LINE_WIDTH, MAX_LINE_WIDTH))
    }

    /** 批量设置面透明度，返回生效的图层数 */
    suspend fun setAllFillOpacity(opacity: Float) = withContext(Dispatchers.IO) {
        dao.setAllFillOpacity(opacity.coerceIn(MIN_OPACITY, MAX_OPACITY))
    }

    suspend fun setAllLabelVisible(visible: Boolean) = withContext(Dispatchers.IO) {
        dao.setAllLabelVisible(visible)
    }

    suspend fun setColor(id: String, color: Int) = withContext(Dispatchers.IO) {
        dao.setColor(id, color)
    }

    /** 把要素移动到另一个图层（类型不限，geometry 原样带走） */
    suspend fun moveFeature(featureId: String, targetLayerId: String): Boolean =
        withContext(Dispatchers.IO) {
            val f = dao.getFeature(featureId) ?: return@withContext false
            if (f.layerId == targetLayerId) return@withContext false
            dao.getLayer(targetLayerId) ?: return@withContext false
            val seq = dao.countFeatures(targetLayerId)
            dao.moveFeature(featureId, targetLayerId, seq)
            dao.refreshFeatureCount(f.layerId)
            dao.refreshFeatureCount(targetLayerId)
            true
        }

    suspend fun feature(id: String) = withContext(Dispatchers.IO) { dao.getFeature(id) }

    suspend fun featuresOfLayer(id: String) = withContext(Dispatchers.IO) { dao.getFeaturesByLayer(id) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteFeatures(id)
        dao.deleteLayer(id)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAllFeatures()
        dao.deleteAllLayers()
    }

    suspend fun getLayers() = withContext(Dispatchers.IO) { dao.getLayers() }

    companion object {
        private var baseIndex = Random.nextInt(1000)

        /** 面透明度可调范围：0% 全透明 ~ 90% 几乎不透明（100% 会完全盖住底图，不提供） */
        const val MIN_OPACITY = 0f
        const val MAX_OPACITY = 0.9f

        const val MIN_LINE_WIDTH = 1f
        const val MAX_LINE_WIDTH = 8f

        fun newId(prefix: String): String =
            "$prefix-${System.currentTimeMillis()}-${Random.nextInt(100000)}"

        /** 专题图层配色：蓝 / 青 / 橙 / 紫 / 绿 / 棕 */
        val palette = intArrayOf(
            0xFF1565C0.toInt(),
            0xFF00838F.toInt(),
            0xFFEF6C00.toInt(),
            0xFF6A1B9A.toInt(),
            0xFF2E7D32.toInt(),
            0xFF4E342E.toInt()
        )
    }
}
