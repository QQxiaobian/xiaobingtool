package com.kunming.powerline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kunming.powerline.data.local.entity.ThematicFeatureEntity
import com.kunming.powerline.data.local.entity.ThematicLayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThematicDao {

    @Query("SELECT * FROM thematic_layer ORDER BY createdAt DESC")
    fun observeLayers(): Flow<List<ThematicLayerEntity>>

    @Query("SELECT * FROM thematic_layer ORDER BY createdAt DESC")
    suspend fun getLayers(): List<ThematicLayerEntity>

    @Query("SELECT * FROM thematic_layer WHERE id = :id")
    suspend fun getLayer(id: String): ThematicLayerEntity?

    @Query("SELECT * FROM thematic_feature ORDER BY layerId, seq ASC")
    fun observeFeatures(): Flow<List<ThematicFeatureEntity>>

    @Query("SELECT * FROM thematic_feature ORDER BY layerId, seq ASC")
    suspend fun getFeatures(): List<ThematicFeatureEntity>

    @Query("SELECT * FROM thematic_feature WHERE layerId = :layerId ORDER BY seq ASC")
    fun observeFeaturesByLayer(layerId: String): Flow<List<ThematicFeatureEntity>>

    @Query("SELECT * FROM thematic_feature WHERE layerId = :layerId ORDER BY seq ASC")
    suspend fun getFeaturesByLayer(layerId: String): List<ThematicFeatureEntity>

    @Query("SELECT COUNT(*) FROM thematic_feature WHERE layerId = :layerId")
    suspend fun countFeatures(layerId: String): Int

    @Query("SELECT * FROM thematic_feature WHERE id = :id")
    suspend fun getFeature(id: String): ThematicFeatureEntity?

    @Query("UPDATE thematic_feature SET layerId = :layerId, seq = :seq WHERE id = :id")
    suspend fun moveFeature(id: String, layerId: String, seq: Int)

    /** 重算某个图层的要素数（移动要素后台账要同步） */
    @Query(
        "UPDATE thematic_layer SET featureCount = " +
            "(SELECT COUNT(*) FROM thematic_feature WHERE layerId = :id) WHERE id = :id"
    )
    suspend fun refreshFeatureCount(id: String)

    @Upsert
    suspend fun upsertLayer(layer: ThematicLayerEntity)

    @Upsert
    suspend fun upsertLayers(layers: List<ThematicLayerEntity>)

    @Upsert
    suspend fun upsertFeatures(features: List<ThematicFeatureEntity>)

    @Query("UPDATE thematic_layer SET visible = :visible WHERE id = :id")
    suspend fun setVisible(id: String, visible: Boolean)

    @Query("UPDATE thematic_layer SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE thematic_layer SET color = :color WHERE id = :id")
    suspend fun setColor(id: String, color: Int)

    @Query("UPDATE thematic_layer SET remark = :remark WHERE id = :id")
    suspend fun setRemark(id: String, remark: String?)

    @Query("UPDATE thematic_layer SET labelVisible = :visible WHERE id = :id")
    suspend fun setLabelVisible(id: String, visible: Boolean)

    @Query("UPDATE thematic_layer SET fillOpacity = :opacity WHERE id = :id")
    suspend fun setFillOpacity(id: String, opacity: Float)

    @Query("UPDATE thematic_layer SET lineWidth = :width WHERE id = :id")
    suspend fun setLineWidth(id: String, width: Float)

    /** 批量：只对含面的图层生效（点 / 纯线图层没有填充色可调） */
    @Query(
        "UPDATE thematic_layer SET fillOpacity = :opacity " +
            "WHERE geometryType = 'polygon' OR geometryType = 'mixed'"
    )
    suspend fun setAllFillOpacity(opacity: Float)

    /** 批量：统一开 / 关要素名称 */
    @Query("UPDATE thematic_layer SET labelVisible = :visible")
    suspend fun setAllLabelVisible(visible: Boolean)

    @Query("DELETE FROM thematic_layer WHERE id = :id")
    suspend fun deleteLayer(id: String)

    @Query("DELETE FROM thematic_feature WHERE layerId = :layerId")
    suspend fun deleteFeatures(layerId: String)

    @Query("DELETE FROM thematic_layer")
    suspend fun deleteAllLayers()

    @Query("DELETE FROM thematic_feature")
    suspend fun deleteAllFeatures()
}
