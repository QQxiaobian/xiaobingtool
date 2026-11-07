package com.kunming.powerline

import com.kunming.powerline.data.parser.ShapefileThematicReader
import com.kunming.powerline.ui.map.ThematicLineConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shapefile → 专题图层的二进制解析
 *
 * 用最小可用的 .shp / .dbf 手工构造数据，验证：
 * 点/线/面三种几何、属性表取名、环闭合、坐标原样保留（WGS84）。
 */
class ShpThematicParseTest {

    // ------------------------------------------------------------------
    // 构造 shapefile
    // ------------------------------------------------------------------
    private fun le(bytes: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes).array()
    private fun be(bytes: Int) = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(bytes).array()
    private fun d(v: Double) = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(v).array()

    private class Rec(val content: ByteArray)

    private fun buildShp(shapeType: Int, records: List<Rec>): ByteArray {
        val body = records.map { r ->
            val lenWords = r.content.size / 2
            be(1) + be(lenWords) + r.content
        }.fold(ByteArray(0)) { a, b -> a + b }
        val header = ByteArray(100)
        System.arraycopy(be(9994), 0, header, 0, 4)
        System.arraycopy(be((100 + body.size) / 2), 0, header, 24, 4)
        System.arraycopy(le(1000), 0, header, 28, 4)
        System.arraycopy(le(shapeType), 0, header, 32, 4)
        // bbox 全 0，解析不用
        return header + body
    }

    private fun pointContent(lon: Double, lat: Double): ByteArray =
        le(1) + d(lon) + d(lat)

    private fun polyContent(type: Int, rings: List<List<Pair<Double, Double>>>): ByteArray {
        val allPts = rings.flatten()
        val parts = mutableListOf<Int>()
        var idx = 0
        rings.forEach { parts.add(idx); idx += it.size }
        var buf = le(type)
        repeat(4) { buf += d(0.0) } // bbox
        buf += le(rings.size)
        buf += le(allPts.size)
        parts.forEach { buf += le(it) }
        allPts.forEach { (lon, lat) -> buf += d(lon); buf += d(lat) }
        return buf
    }

    /** dBASE III 属性表 */
    private fun buildDbf(fields: List<Pair<String, Int>>, rows: List<List<String>>): ByteArray {
        val headerLen = 32 + fields.size * 32 + 1
        val recordLen = 1 + fields.sumOf { it.second }
        val b = ByteBuffer.allocate(headerLen + rows.size * recordLen + 1).order(ByteOrder.LITTLE_ENDIAN)
        b.put(0x03); b.put(126); b.put(1); b.put(1)
        b.putInt(rows.size)
        b.putShort(headerLen.toShort())
        b.putShort(recordLen.toShort())
        b.position(32)
        fields.forEach { (name, len) ->
            val nb = ByteArray(11)
            name.toByteArray(Charsets.US_ASCII).copyInto(nb)
            b.put(nb); b.put('C'.code.toByte())
            b.putInt(0); b.put(len.toByte()); b.put(0.toByte())
            b.put(ByteArray(14))
        }
        b.put(0x0D.toByte())
        rows.forEach { row ->
            b.put(' '.code.toByte())
            row.forEachIndexed { i, v ->
                val len = fields[i].second
                val cell = ByteArray(len) { ' '.code.toByte() }
                v.toByteArray(Charsets.US_ASCII).take(len).forEachIndexed { k, c -> cell[k] = c }
                b.put(cell)
            }
        }
        b.put(0x1A.toByte())
        return b.array()
    }

    // ------------------------------------------------------------------
    @Test
    fun `面要素解析为 polygon 且属性表名称可用`() {
        val ring = listOf(
            102.70 to 25.04, 102.72 to 25.04, 102.72 to 25.06, 102.70 to 25.06, 102.70 to 25.04
        )
        val shp = buildShp(5, listOf(Rec(polyContent(5, listOf(ring)))))
        val dbf = buildDbf(
            listOf("NAME" to 20, "REMARK" to 30),
            listOf(listOf("AreaA", "flood zone"))
        )
        val r = ShapefileThematicReader.read(shp, dbf, null, "区域.shp")
        assertEquals(1, r.layers.size)
        val layer = r.layers[0]
        assertEquals("polygon", layer.geometryType)
        assertEquals("区域", layer.name)
        val f = layer.features[0]
        assertEquals("AreaA", f.name)
        assertEquals("polygon", f.kind)
        assertEquals("flood zone", f.properties["description"])
        assertNotNull(f.lon)
        assertTrue(f.geometryJson.contains("Polygon"))
    }

    @Test
    fun `折线要素解析为 line`() {
        val line = listOf(102.70 to 25.04, 102.75 to 25.08, 102.78 to 25.10)
        val shp = buildShp(3, listOf(Rec(polyContent(3, listOf(line)))))
        val dbf = buildDbf(listOf("NAME" to 20), listOf(listOf("LineB")))
        val r = ShapefileThematicReader.read(shp, dbf, null, "通道.shp")
        assertEquals("line", r.layers[0].geometryType)
        assertEquals("LineB", r.layers[0].features[0].name)
    }

    @Test
    fun `点要素解析为 point`() {
        val shp = buildShp(1, listOf(Rec(pointContent(102.71, 25.05))))
        val dbf = buildDbf(listOf("NAME" to 20), listOf(listOf("Pole1")))
        val r = ShapefileThematicReader.read(shp, dbf, null, "点位.shp")
        val f = r.layers[0].features[0]
        assertEquals("point", f.kind)
        assertEquals(102.71, f.lon!!, 1e-9)
        assertEquals(25.05, f.lat!!, 1e-9)
        assertEquals("Pole1", f.name)
    }

    @Test
    fun `没有属性表时用序号占位名称`() {
        val shp = buildShp(1, listOf(Rec(pointContent(102.71, 25.05))))
        val r = ShapefileThematicReader.read(shp, null, null, "无属性.shp")
        assertEquals(1, r.layers[0].features.size)
        // 无 dbf 时 name 为空，交由地图用图层名兜底
        assertEquals(null, r.layers[0].features[0].name)
    }

    @Test
    fun `多环多边形按时分别成要素`() {
        val r1 = listOf(102.70 to 25.04, 102.72 to 25.04, 102.72 to 25.06, 102.70 to 25.04)
        val r2 = listOf(102.80 to 25.14, 102.82 to 25.14, 102.82 to 25.16, 102.80 to 25.14)
        val shp = buildShp(5, listOf(Rec(polyContent(5, listOf(r1, r2)))))
        val r = ShapefileThematicReader.read(shp, null, null, "多环.shp")
        assertEquals(2, r.layers[0].features.size)
    }

    // ------------------------------------------------------------------
    // 专题线 → 线路
    // ------------------------------------------------------------------
    @Test
    fun `线要素坐标可提取并去掉闭合尾点`() {
        val geo = """{"type":"LineString","coordinates":[[102.7,25.0],[102.8,25.1],[102.7,25.0]]}"""
        val pts = ThematicLineConverter.lineCoords(geo)
        assertEquals(3, pts.size)
        val dropped = ThematicLineConverter.dropClosingPoint(pts)
        assertEquals(2, dropped.size)
    }

    @Test
    fun `MultiLineString 取最长的一段`() {
        val geo = """{"type":"MultiLineString","coordinates":[[[102.7,25.0],[102.8,25.1]],[[103.0,25.0],[103.1,25.0],[103.2,25.0]]]}"""
        assertEquals(3, ThematicLineConverter.lineCoords(geo).size)
    }

    @Test
    fun `脏几何不会崩溃`() {
        assertTrue(ThematicLineConverter.lineCoords("not json").isEmpty())
        assertTrue(ThematicLineConverter.lineCoords(null).isEmpty())
        assertTrue(ThematicLineConverter.lineCoords("""{"type":"Point","coordinates":[1,2]}""").isEmpty())
    }

    @Test
    fun `杆塔编号从 1 开始连续`() {
        assertEquals(listOf("1", "2", "3"), ThematicLineConverter.towerNumbers(3))
    }
}
