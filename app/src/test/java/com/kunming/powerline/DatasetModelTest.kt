package com.kunming.powerline

import com.kunming.powerline.data.remote.model.DatasetBundleDto
import com.kunming.powerline.data.remote.model.DatasetFeatureDto
import com.kunming.powerline.data.remote.model.DatasetLayerDto
import com.kunming.powerline.data.remote.model.DatasetStats
import com.kunming.powerline.data.remote.model.PmsLedgerBundle
import com.kunming.powerline.data.remote.model.PmsLineDto
import com.kunming.powerline.data.remote.model.PmsTowerDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** NAS 数据集格式（上传 / 下载 / 本地 JSON 共用） */
class DatasetModelTest {

    private fun sample() = DatasetBundleDto(
        id = "DS-1",
        name = "昆明巡检数据包",
        kind = DatasetBundleDto.KIND_MIXED,
        version = 2,
        updatedAt = "2026-11-08 16:00:00",
        layers = listOf(
            DatasetLayerDto(
                id = "TL-1", name = "巡视区域",
                features = listOf(
                    DatasetFeatureDto(id = "TF-1", layerId = "TL-1", kind = "polygon"),
                    DatasetFeatureDto(id = "TF-2", layerId = "TL-1", kind = "polygon")
                )
            ),
            DatasetLayerDto(id = "TL-2", name = "隐患点")
        ),
        ledger = PmsLedgerBundle(
            lines = listOf(PmsLineDto(id = "L1", lineName = "110kV 昆呈线")),
            towers = listOf(
                PmsTowerDto(lineId = "L1", towerNumber = "1", longitude = 102.7, latitude = 25.0),
                PmsTowerDto(lineId = "L1", towerNumber = "2", longitude = 102.8, latitude = 25.1)
            )
        )
    )

    @Test
    fun 统计_图层要素线路杆塔() {
        val c = DatasetStats.counts(sample())
        assertEquals(2, c.layers)
        assertEquals(2, c.features)
        assertEquals(1, c.lines)
        assertEquals(2, c.towers)
    }

    @Test
    fun 推断类型() {
        assertEquals(DatasetBundleDto.KIND_MIXED, DatasetStats.inferKind(sample()))
        assertEquals(
            DatasetBundleDto.KIND_LAYERS,
            DatasetStats.inferKind(sample().copy(ledger = PmsLedgerBundle()))
        )
        assertEquals(
            DatasetBundleDto.KIND_LINES,
            DatasetStats.inferKind(sample().copy(layers = emptyList()))
        )
    }

    @Test
    fun 名称校验_拒绝路径分隔符() {
        assertTrue(DatasetStats.validName("昆明 2026"))
        assertFalse(DatasetStats.validName(""))
        assertFalse(DatasetStats.validName("a/b"))
        assertFalse(DatasetStats.validName("a\\b"))
    }

    @Test
    fun 元数据统计一致() {
        val m = DatasetStats.metaOf(sample(), 4096L)
        assertEquals("DS-1", m.id)
        assertEquals(2, m.version)
        assertEquals(2, m.layerCount)
        assertEquals(2, m.featureCount)
        assertEquals(1, m.lineCount)
        assertEquals(2, m.towerCount)
        assertEquals(4096L, m.sizeBytes)
    }

    @Test
    fun 摘要文案包含全部计数() {
        val s = DatasetStats.summary(sample())
        assertTrue(s.contains("2 个图层"))
        assertTrue(s.contains("1 条线路"))
    }

    @Test
    fun Moshi序列化往返() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(DatasetBundleDto::class.java)
        val json = adapter.toJson(sample())
        assertTrue(json.contains("powerline-dataset/1"))
        val back = adapter.fromJson(json)
        assertNotNull(back)
        assertEquals("昆明巡检数据包", back!!.name)
        assertEquals(2, back.layers.size)
        assertEquals(2, back.layers[0].features.size)
        assertEquals("110kV 昆呈线", back.ledger.lines[0].lineName)
        assertEquals(2, DatasetStats.counts(back).towers)
    }
}
