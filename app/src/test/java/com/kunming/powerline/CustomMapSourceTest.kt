package com.kunming.powerline

import com.kunming.powerline.ui.map.BasemapType
import com.kunming.powerline.ui.map.CustomMapSource
import com.kunming.powerline.ui.map.CustomSourceSpec
import com.kunming.powerline.ui.map.TileSourceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自定义地图源（XYZ / TMS / 自建地图服务器）模板与 style 生成 */
class CustomMapSourceTest {

    @Test
    fun 校验_接受标准XYZ模板() {
        assertNull(CustomMapSource.validate("http://192.168.1.10:8080/tiles/{z}/{x}/{y}.png"))
        assertNull(CustomMapSource.validate("https://a.example.com/{z}/{x}/{y}.jpg"))
    }

    @Test
    fun 校验_缺少占位符被拒绝() {
        assertNotNull(CustomMapSource.validate("http://nas/tiles/{z}/{x}.png"))
        assertNotNull(CustomMapSource.validate("http://nas/tiles.png"))
        assertNotNull(CustomMapSource.validate(""))
    }

    @Test
    fun 校验_拒绝非法协议与引号() {
        assertNotNull(CustomMapSource.validate("ftp://nas/{z}/{x}/{y}.png"))
        assertNotNull(CustomMapSource.validate("http://nas/{z}/{x}/{y}\".png"))
    }

    @Test
    fun 子域名展开() {
        val t = "https://{s}.example.com/{z}/{x}/{y}.png"
        assertEquals(
            listOf("https://example.com/{z}/{x}/{y}.png"),
            CustomMapSource.expand(t, "")
        )
        assertEquals(
            listOf(
                "https://a.example.com/{z}/{x}/{y}.png",
                "https://b.example.com/{z}/{x}/{y}.png"
            ),
            CustomMapSource.expand(t, "a,b")
        )
    }

    @Test
    fun 规格_TMS保留原URL并标记scheme() {
        val s = CustomMapSource.spec(
            id = "s1", name = "自建 TMS", template = "http://nas/{z}/{x}/{y}.png",
            scheme = "tms", tileSize = 512, minZoom = 4, maxZoom = 20,
            opacity = 0.4f, asBase = true
        )
        assertNotNull(s)
        assertEquals("tms", s!!.scheme)
        assertEquals(512, s.tileSize)
        assertEquals("http://nas/{z}/{x}/{y}.png", s.tiles.single())
        assertTrue(s.asBase)
        assertEquals(0.4f, s.opacity)
    }

    @Test
    fun 规格_非法模板返回null且缩放被夹紧() {
        assertNull(CustomMapSource.spec("x", "坏源", "not-a-url"))
        val s = CustomMapSource.spec(
            "x", "源", "http://nas/{z}/{x}/{y}.png",
            tileSize = 999, minZoom = 30, maxZoom = 2
        )!!
        assertEquals(256, s.tileSize)
        assertEquals(24, s.minZoom)
        assertEquals(24, s.maxZoom)
    }

    @Test
    fun JSON转义() {
        assertEquals("a\\\"b\\\\c", CustomMapSource.jsonEscape("a\"b\\c"))
    }

    @Test
    fun style_自定义主底图替代内置底图() {
        val base = CustomSourceSpec(
            id = "b", name = "自建瓦片",
            tiles = listOf("http://nas/{z}/{x}/{y}.png"),
            tileSize = 512, scheme = "tms", asBase = true
        )
        val json = TileSourceFactory.buildStyleJson(
            BasemapType.YN_SATELLITE, customSources = listOf(base)
        )
        assertTrue(json.contains("http://nas/{z}/{x}/{y}.png"))
        assertTrue(json.contains("\"tileSize\":512"))
        assertTrue(json.contains("\"scheme\":\"tms\""))
        assertFalse(json.contains("custom-src-0"))
    }

    @Test
    fun style_启用的自定义源作为叠加层() {
        val overlay = CustomSourceSpec(
            id = "o", name = "注记层",
            tiles = listOf("http://nas/label/{z}/{x}/{y}.png"),
            opacity = 0.6f
        )
        val json = TileSourceFactory.buildStyleJson(
            BasemapType.YN_SATELLITE, customSources = listOf(overlay)
        )
        assertTrue(json.contains("custom-src-0"))
        assertTrue(json.contains("custom-lyr-0"))
        assertTrue(json.contains("http://nas/label/{z}/{x}/{y}.png"))
        assertTrue(json.contains("\"raster-opacity\":0.6"))
    }

    @Test
    fun style_无自定义源时保持兼容() {
        val json = TileSourceFactory.buildStyleJson(BasemapType.YN_SATELLITE)
        assertFalse(json.contains("custom-src-0"))
        assertTrue(json.contains("basemap-layer"))
    }
}
