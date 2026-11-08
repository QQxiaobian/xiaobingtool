package com.kunming.powerline

import com.kunming.powerline.util.VersionCompare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `相同版本号视为无更新`() {
        assertFalse(VersionCompare.isNewer("1.4.1", "1.4.1"))
        assertEquals(0, VersionCompare.compare("1.4.1", "v1.4.1"))
    }

    @Test
    fun `新版本能识别`() {
        assertTrue(VersionCompare.isNewer("1.4.1", "v1.5.0"))
        assertTrue(VersionCompare.isNewer("1.4.1", "1.4.2"))
        assertTrue(VersionCompare.isNewer("1.9.9", "2.0.0"))
    }

    @Test
    fun `旧版本不会被误判为更新`() {
        assertFalse(VersionCompare.isNewer("1.5.0", "v1.4.1"))
        assertFalse(VersionCompare.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun `位数不等时补零比较`() {
        assertEquals(0, VersionCompare.compare("1.4", "1.4.0"))
        assertTrue(VersionCompare.compare("1.4.1", "1.4") > 0)
    }

    @Test
    fun `预发布后缀与非数字按 0 处理`() {
        assertEquals(0, VersionCompare.compare("1.5.0", "1.5.0-rc1"))
        assertFalse(VersionCompare.isNewer("1.5.0", "1.5.0-beta"))
    }

    @Test
    fun `空串与非数字输入不崩溃`() {
        assertEquals(0, VersionCompare.compare("", ""))
        assertFalse(VersionCompare.isNewer("1.4.1", ""))
    }
}
