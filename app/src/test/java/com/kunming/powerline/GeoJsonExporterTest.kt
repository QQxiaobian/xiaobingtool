package com.kunming.powerline

import com.kunming.powerline.data.parser.GeoJsonExporter
import com.kunming.powerline.data.remote.model.DatasetBundleDto
import com.kunming.powerline.data.remote.model.DatasetFeatureDto
import com.kunming.powerline.data.remote.model.DatasetLayerDto
import com.kunming.powerline.data.remote.model.PmsLedgerBundle
import com.kunming.powerline.data.remote.model.PmsLineDto
import com.kunming.powerline.data.remote.model.PmsTowerDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 全量 GeoJSON 导出：结构、要素数量、属性、转义、边界数据 */
class GeoJsonExporterTest {

    private fun sample(): DatasetBundleDto = DatasetBundleDto(
        id = "DS-1",
        name = "昆明巡检包",
        kind = DatasetBundleDto.KIND_MIXED,
        layers = listOf(
            DatasetLayerDto(
                id = "TL-1",
                name = "行政区划",
                geometryType = "polygon",
                features = listOf(
                    DatasetFeatureDto(
                        id = "TF-1", layerId = "TL-1", name = "盘龙区", kind = "polygon",
                        geometryGeojson = "{\"type\":\"Polygon\",\"coordinates\":[[[102.7,25.0],[102.8,25.0],[102.8,25.1],[102.7,25.0]]]}",
                        propertiesJson = "{\"code\":\"530103\"}"
                    )
                )
            )
        ),
        ledger = PmsLedgerBundle(
            lines = listOf(
                PmsLineDto(id = "L1", lineName = "220kV 昆钢 I 回线", voltageLevel = "220kV")
            ),
            towers = listOf(
                PmsTowerDto(lineName = "220kV 昆钢 I 回线", towerNumber = "1",
                    longitude = 102.71832, latitude = 25.03871, elevation = 1891.2),
                PmsTowerDto(lineName = "220kV 昆钢 I 回线", towerNumber = "2",
                    longitude = 102.72654, latitude = 25.03118)
            )
        )
    )

    @Test
    fun `导出为 FeatureCollection 且结构完整`() {
        val s = GeoJsonExporter.export(sample())
        assertTrue(s.startsWith("{\"type\":\"FeatureCollection\",\"features\":["))
        assertTrue(s.endsWith("]}"))
        assertTrue(s.contains("\"type\":\"LineString\""))
        assertTrue(s.contains("\"type\":\"Point\""))
        assertTrue(s.contains("\"type\":\"Polygon\""))
        assertEquals(4, GeoJsonExporter.featureCount(sample()))
    }

    @Test
    fun `线路与杆塔属性写入 properties`() {
        val s = GeoJsonExporter.export(sample())
        assertTrue(s.contains("\"name\":\"220kV 昆钢 I 回线\""))
        assertTrue(s.contains("\"voltage\":\"220kV\""))
        assertTrue(s.contains("\"tower_number\":\"1\""))
        assertTrue(s.contains("\"layer\":\"行政区划\""))
        assertTrue(s.contains("\"attrs\":\"{\\\"code\\\":\\\"530103\\\"}\""))
    }

    @Test
    fun `坐标按原始精度写出`() {
        val s = GeoJsonExporter.export(sample())
        assertTrue(s.contains("[102.71832,25.03871,1891.2]"))
        assertTrue(s.contains("[102.72654,25.03118]"))
    }

    @Test
    fun `特殊字符被正确转义`() {
        val b = sample().copy(
            layers = listOf(
                DatasetLayerDto(
                    id = "TL-2", name = "图层\"A\\B\nC",
                    features = listOf(
                        DatasetFeatureDto(
                            name = "要素\"1", geometryGeojson = "{\"type\":\"Point\",\"coordinates\":[1.0,2.0]}"
                        )
                    )
                )
            ),
            ledger = PmsLedgerBundle()
        )
        val s = GeoJsonExporter.export(b)
        assertTrue(s.contains("图层\\\"A\\\\B\\nC"))
        assertTrue(s.contains("要素\\\"1"))
        assertFalse(s.contains("图层\"A"))
    }

    @Test
    fun `空数据集导出空集合`() {
        val s = GeoJsonExporter.export(DatasetBundleDto())
        assertEquals("{\"type\":\"FeatureCollection\",\"features\":[]}", s)
        assertEquals(0, GeoJsonExporter.featureCount(DatasetBundleDto()))
    }

    @Test
    fun `少于两个杆塔的线路不生成 LineString`() {
        val b = DatasetBundleDto(
            ledger = PmsLedgerBundle(
                lines = listOf(PmsLineDto(lineName = "空线路")),
                towers = listOf(
                    PmsTowerDto(lineName = "空线路", towerNumber = "1", longitude = 102.7, latitude = 25.0)
                )
            )
        )
        val s = GeoJsonExporter.export(b)
        assertFalse(s.contains("LineString"))
        assertTrue(s.contains("\"type\":\"Point\""))
        assertEquals(1, GeoJsonExporter.featureCount(b))
    }

    @Test
    fun `非法 geometry 被跳过且不影响其它要素`() {
        val b = sample().copy(
            layers = listOf(
                DatasetLayerDto(
                    id = "TL-3", name = "混合",
                    features = listOf(
                        DatasetFeatureDto(name = "坏数据", geometryGeojson = "not-json"),
                        DatasetFeatureDto(name = "好数据",
                            geometryGeojson = "{\"type\":\"Point\",\"coordinates\":[102.7,25.0]}")
                    )
                )
            )
        )
        val s = GeoJsonExporter.export(b)
        assertFalse(s.contains("坏数据"))
        assertTrue(s.contains("好数据"))
        assertEquals(4, GeoJsonExporter.featureCount(b))
    }
}
