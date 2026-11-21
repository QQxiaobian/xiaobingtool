package com.kunming.powerline

import com.kunming.powerline.data.remote.model.DatasetBundleDto
import com.kunming.powerline.data.remote.model.DatasetDangerDto
import com.kunming.powerline.data.remote.model.DatasetDangerLayerDto
import com.kunming.powerline.data.remote.model.DatasetFavoriteDto
import com.kunming.powerline.data.remote.model.DatasetOverlayDto
import com.kunming.powerline.data.remote.model.DatasetPointsDto
import com.kunming.powerline.data.remote.model.DatasetStats
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据集点位段（1.6.4）：收藏夹 / 危险点等此前完全不在导出范围内。
 */
class DatasetPointsTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(DatasetBundleDto::class.java)

    private fun sample() = DatasetBundleDto(
        id = "DS-2",
        name = "含点位的巡检包",
        note = "test",
        points = DatasetPointsDto(
            favorites = listOf(
                DatasetFavoriteDto(id = "FV-1", name = "大号侧施工点", category = "隐患", lon = 102.8, lat = 24.9, note = "每天巡查")
            ),
            dangerLayers = listOf(
                DatasetDangerLayerDto(id = "DL-1", name = "110kV松万线净空报告", fileName = "report.csv", lineName = "110kV松万线", visible = true)
            ),
            dangers = listOf(
                DatasetDangerDto(
                    id = "DG-1", layerId = "DL-1", lineName = "110kV松万线",
                    type = "植被", groundType = "植被", vegetationType = "植被",
                    lon = 102.867557, lat = 24.858408,
                    clearanceM = 6.51, safeDistanceM = 7.0, defectLevel = "重大",
                    towerSpan = "00—01", seqNo = "2", measuredAt = "2024/7/5",
                    horizM = 2.24, vertM = 6.12
                )
            ),
            overlays = listOf(
                DatasetOverlayDto(id = "OV-1", name = "巡视范围", type = "polygon",
                    geometryGeojson = "{\"type\":\"Polygon\",\"coordinates\":[]}")
            )
        )
    )

    @Test
    fun `点位计入统计与摘要`() {
        val c = DatasetStats.counts(sample())
        // 1 收藏点 + 1 危险点 + 1 标注
        assertEquals(3, c.points)
        val s = DatasetStats.summary(sample())
        assertTrue(s.contains("3 个点位"))
        assertTrue(s.contains("收藏点"))
        assertTrue(s.contains("危险点"))
    }

    @Test
    fun `元数据带 pointCount`() {
        assertEquals(3, DatasetStats.metaOf(sample()).pointCount)
    }

    @Test
    fun `序列化往返保留点位`() {
        val json = adapter.toJson(sample())
        assertTrue(json.contains("\"points\""))
        assertTrue(json.contains("大号侧施工点"))
        assertTrue(json.contains("6.51"))
        val back = adapter.fromJson(json)!!
        assertEquals(1, back.points.favorites.size)
        assertEquals("隐患", back.points.favorites[0].category)
        assertEquals(1, back.points.dangerLayers.size)
        assertEquals(6.51, back.points.dangers[0].clearanceM!!, 1e-9)
        assertEquals("00—01", back.points.dangers[0].towerSpan)
        assertEquals(1, back.points.overlays.size)
    }

    @Test
    fun `只有点位的数据集推断为图层类`() {
        val onlyPoints = DatasetBundleDto(points = sample().points)
        assertEquals(DatasetBundleDto.KIND_LAYERS, DatasetStats.inferKind(onlyPoints))
    }

    @Test
    fun `空点位段不增加摘要噪声`() {
        val s = DatasetStats.summary(DatasetBundleDto(name = "空"))
        assertTrue(s.contains("0 条线路"))
        assertTrue(!s.contains("点位"))
    }

    @Test
    fun `旧数据集缺少 points 字段仍可解析`() {
        val old = """{"schema":"powerline-dataset/1","id":"DS-OLD","name":"旧包","layers":[],"ledger":{"lines":[],"towers":[]}}"""
        val b = adapter.fromJson(old)
        assertNotNull(b)
        assertEquals("旧包", b!!.name)
        assertEquals(0, b.points.total)
    }

    @Test
    fun `未来新增字段不会导致解析失败`() {
        // 新版本 App 写入的字段，老版本 App 读到时应忽略而不是崩溃
        val json = """{"schema":"powerline-dataset/1","id":"DS-F","name":"未来版本",
            |"layers":[],"ledger":{"lines":[],"towers":[]},
            |"points":{"favorites":[],"hydrogenStations":[{"id":"X"}]},
            |"futureTopLevel":123}""".trimMargin()
        val b = adapter.fromJson(json)
        assertNotNull(b)
        assertEquals("未来版本", b!!.name)
    }

    @Test
    fun `点位分部摘要可读`() {
        val s = sample().points.summary()
        assertTrue(s.contains("1 收藏点"))
        assertTrue(s.contains("1 危险点图层/1 危险点"))
    }
}
