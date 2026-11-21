package com.kunming.powerline

import com.kunming.powerline.data.parser.GeoJsonExporter
import com.kunming.powerline.data.remote.model.DatasetBundleDto
import com.kunming.powerline.data.remote.model.DatasetDangerDto
import com.kunming.powerline.data.remote.model.DatasetFavoriteDto
import com.kunming.powerline.data.remote.model.DatasetPointsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 全量 GeoJSON 导出点位（1.6.4）：收藏夹与危险点报告字段 */
class GeoJsonPointsTest {

    private fun sample() = DatasetBundleDto(
        name = "点位导出",
        points = DatasetPointsDto(
            favorites = listOf(
                DatasetFavoriteDto(id = "F1", name = "施工点", category = "隐患", lon = 102.8, lat = 24.9, note = "每天巡查")
            ),
            dangers = listOf(
                DatasetDangerDto(
                    id = "D1", layerId = "DL1", lineName = "110kV松万线",
                    type = "植被", groundType = "植被", vegetationType = "桉树",
                    lon = 102.867557, lat = 24.858408,
                    clearanceM = 6.51, safeDistanceM = 7.0, defectLevel = "重大",
                    towerSpan = "00—01", measuredAt = "2024/7/5",
                    horizM = 2.24, vertM = 6.12, heightM = 1972.77, spanM = 32.31,
                    distSmallTowerM = 14.38,
                    seqNo = "2"
                )
            )
        )
    )

    @Test
    fun `收藏点导出为点要素`() {
        val s = GeoJsonExporter.export(sample())
        assertTrue(s.contains("\"type\":\"favorite\""))
        assertTrue(s.contains("\"name\":\"施工点\""))
        assertTrue(s.contains("\"category\":\"隐患\""))
    }

    @Test
    fun `危险点导出净空字段`() {
        val s = GeoJsonExporter.export(sample())
        assertTrue(s.contains("\"type\":\"danger\""))
        assertTrue(s.contains("\"clearance_m\":\"6.51\""))
        assertTrue(s.contains("\"safe_distance_m\":\"7.0\""))
        assertTrue(s.contains("\"ground_type\":\"植被\""))
        assertTrue(s.contains("\"vegetation_type\":\"桉树\""))
        assertTrue(s.contains("\"defect_level\":\"重大\""))
        assertTrue(s.contains("\"tower_span\":\"00—01\""))
        assertTrue(s.contains("\"from_small_tower_m\":\"14.38\""))
    }

    @Test
    fun `要素计数包含点位`() {
        assertEquals(2, GeoJsonExporter.featureCount(sample()))
    }

    @Test
    fun `空点位不影响几何结构`() {
        val s = GeoJsonExporter.export(DatasetBundleDto(name = "空"))
        assertTrue(s.startsWith("{\"type\":\"FeatureCollection\",\"features\":["))
        assertTrue(s.endsWith("]}"))
        assertFalse(s.contains("\"type\":\"favorite\""))
    }
}
