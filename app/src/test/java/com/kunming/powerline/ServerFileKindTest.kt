package com.kunming.powerline

import com.kunming.powerline.data.remote.model.ServerFileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 服务器原始文件的扩展名分类与本地文件名清洗 */
class ServerFileKindTest {

    @Test
    fun `扩展名识别大小写与多级后缀`() {
        assertEquals("kml", ServerFileKind.extOf("线路.KML"))
        assertEquals("geojson", ServerFileKind.extOf("a.b.geojson"))
        assertEquals("kmz", ServerFileKind.extOf(" 昆明.kmz "))
        assertEquals("", ServerFileKind.extOf("无扩展名"))
    }

    @Test
    fun `白名单只放行可解析格式`() {
        listOf("a.kml", "a.kmz", "a.geojson", "a.json", "a.gpx", "a.shp", "a.zip")
            .forEach { assertTrue("$it 应被支持", ServerFileKind.isSupported(it)) }
        listOf("a.exe", "a.txt", "a.docx", "无扩展名")
            .forEach { assertFalse("$it 不应被支持", ServerFileKind.isSupported(it)) }
    }

    @Test
    fun `二进制格式标记`() {
        listOf("a.kmz", "a.shp", "a.zip").forEach {
            assertTrue(it, ServerFileKind.isBinary(it))
        }
        listOf("a.kml", "a.geojson", "a.gpx", "a.json").forEach {
            assertFalse(it, ServerFileKind.isBinary(it))
        }
    }

    @Test
    fun `线路导入建议`() {
        listOf("a.kml", "a.kmz", "a.gpx", "a.geojson", "a.json").forEach {
            assertTrue(it, ServerFileKind.suggestAsLine(it))
        }
        assertFalse(ServerFileKind.suggestAsLine("a.zip"))
    }

    @Test
    fun `显示标签`() {
        assertEquals("KML", ServerFileKind.label("a.kml"))
        assertEquals("KMZ", ServerFileKind.label("a.kmz"))
        assertEquals("GeoJSON", ServerFileKind.label("a.geojson"))
        assertEquals("SHP 压缩包", ServerFileKind.label("a.zip"))
        assertEquals("未知格式", ServerFileKind.label("a.exe"))
    }

    @Test
    fun `本地文件名清洗保留中文并去掉路径`() {
        assertEquals("passwd.kml", ServerFileKind.safeLocalName("../../etc/passwd.kml"))
        assertEquals("行政区划.kml", ServerFileKind.safeLocalName("行政区划.kml"))
        assertEquals("线路.kmz", ServerFileKind.safeLocalName("C:\\data\\线路.kmz"))
        assertEquals("abc.kml", ServerFileKind.safeLocalName("a\nb\tc.kml"))
        assertEquals("server-file.kml", ServerFileKind.safeLocalName(""))
    }
}
