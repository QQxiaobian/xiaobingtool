package com.kunming.powerline

import com.kunming.powerline.data.prefs.ServerAddresses
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 运行时服务器地址推导：设置页只填一个主地址，自动推导台账与瓦片地址。
 */
class ServerAddressTest {

    @Test
    fun `只填 IP 端口时自动补协议与结尾斜杠`() {
        assertEquals(
            "http://192.168.1.10:8080/",
            ServerAddresses.normalize("192.168.1.10:8080")
        )
        assertEquals(
            "http://nas.local:8080/",
            ServerAddresses.normalize("  http://nas.local:8080  ")
        )
    }

    @Test
    fun `http 主地址推导 PMS 台账与 XYZ 瓦片`() {
        val a = ServerAddresses.deriveFrom("192.168.1.10:8080")
        assertEquals("http://192.168.1.10:8080/", a.pmsBase)
        assertEquals(
            "http://192.168.1.10:8080/tiles/{z}/{x}/{y}.png",
            a.tileServer
        )
    }

    @Test
    fun `https 主地址推导瓦片也用 https`() {
        val a = ServerAddresses.deriveFrom("https://nas.example.com/")
        assertEquals("https://nas.example.com/", a.pmsBase)
        assertEquals(
            "https://nas.example.com/tiles/{z}/{x}/{y}.png",
            a.tileServer
        )
    }

    @Test
    fun `带反代路径前缀时两个地址都保留前缀`() {
        val a = ServerAddresses.deriveFrom("http://10.0.0.5/powerline")
        assertEquals("http://10.0.0.5/powerline/", a.pmsBase)
        assertEquals(
            "http://10.0.0.5/powerline/tiles/{z}/{x}/{y}.png",
            a.tileServer
        )
    }

    @Test
    fun `空地址返回空地址对不会拼出非法 URL`() {
        val a = ServerAddresses.deriveFrom("   ")
        assertEquals("", a.pmsBase)
        assertEquals("", a.tileServer)
    }

    @Test
    fun `1_4_0 默认单端口地址推导`() {
        val a = ServerAddresses.deriveFrom("192.168.1.12:8902")
        assertEquals("http://192.168.1.12:8902/", a.pmsBase)
        assertEquals(
            "http://192.168.1.12:8902/tiles/{z}/{x}/{y}.png",
            a.tileServer
        )
    }
}
