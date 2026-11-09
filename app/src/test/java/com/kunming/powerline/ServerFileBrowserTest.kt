package com.kunming.powerline

import com.kunming.powerline.data.remote.model.ServerFileDto
import com.kunming.powerline.util.ServerFileBrowser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务器文件库文件夹视图与搜索（1.6.2）的纯 Kotlin 单元测试。
 */
class ServerFileBrowserTest {

    private val files = listOf(
        ServerFileDto(id = "1", name = "甲线.kml", path = "甲线.kml"),
        ServerFileDto(id = "2", name = "乙线.kml", path = "220kV片区/乙线.kml"),
        ServerFileDto(id = "3", name = "丙线.kmz", path = "220kV片区/变电站/丙线.kmz"),
        ServerFileDto(id = "4", name = "danger.geojson", path = "专题/danger.geojson"),
        ServerFileDto(id = "5", name = "无路径旧文件.kml", path = "")
    )

    @Test
    fun `根目录只含直接子项`() {
        val dirs = ServerFileBrowser.subdirs(files, "")
        assertEquals(listOf("220kV片区", "专题"), dirs)
        val names = ServerFileBrowser.filesIn(files, "").map { it.id }
        // 根目录：甲线 + 无 path 的旧服务端文件
        assertEquals(listOf("1", "5"), names)
    }

    @Test
    fun `二级目录正确进入`() {
        val dirs = ServerFileBrowser.subdirs(files, "220kV片区")
        assertEquals(listOf("变电站"), dirs)
        assertEquals(listOf("2"), ServerFileBrowser.filesIn(files, "220kV片区").map { it.id })
        assertEquals(listOf("3"), ServerFileBrowser.filesIn(files, "220kV片区/变电站").map { it.id })
    }

    @Test
    fun `path 缺失时退化为文件名`() {
        assertEquals("无路径旧文件.kml", ServerFileBrowser.pathOf(files[4]))
        assertEquals("", ServerFileBrowser.dirOf(files[4]))
    }

    @Test
    fun `搜索匹配文件名与路径且忽略大小写`() {
        val hit1 = ServerFileBrowser.search(files, "甲线")
        assertEquals(listOf("1"), hit1.map { it.id })

        val hit2 = ServerFileBrowser.search(files, "DANGER")
        assertEquals(listOf("4"), hit2.map { it.id })

        val hit3 = ServerFileBrowser.search(files, "220kV片区")
        assertTrue(hit3.map { it.id }.containsAll(listOf("2", "3")))

        assertTrue(ServerFileBrowser.search(files, "  ").isEmpty())
        assertTrue(ServerFileBrowser.search(files, "xyz-不存在").isEmpty())
    }

    @Test
    fun `面包屑与父目录`() {
        assertEquals(
            listOf("根目录" to "", "220kV片区" to "220kV片区", "变电站" to "220kV片区/变电站"),
            ServerFileBrowser.breadcrumb("220kV片区/变电站")
        )
        assertEquals("", ServerFileBrowser.parentOf("220kV片区"))
        assertEquals("220kV片区", ServerFileBrowser.parentOf("220kV片区/变电站"))
    }
}
