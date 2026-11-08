package com.kunming.powerline

import com.kunming.powerline.data.prefs.SettingsStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App 内模拟定位的状态判定。
 * 注意：这里只测「本 App 是否采用模拟坐标」的规则，
 * 不涉及任何系统级位置注入（本 App 也不提供该能力）。
 */
class MockLocationTest {

    @Test
    fun 未启用时不生效() {
        assertFalse(SettingsStore.MockState(enabled = false, lon = 102.7, lat = 25.0).valid)
    }

    @Test
    fun 启用但缺坐标时不生效() {
        assertFalse(SettingsStore.MockState(enabled = true, lon = null, lat = 25.0).valid)
        assertFalse(SettingsStore.MockState(enabled = true, lon = 102.7, lat = null).valid)
    }

    @Test
    fun 启用且坐标合法时生效() {
        assertTrue(SettingsStore.MockState(enabled = true, lon = 102.71832, lat = 25.03871).valid)
    }

    @Test
    fun 越界坐标不生效() {
        assertFalse(SettingsStore.MockState(enabled = true, lon = 200.0, lat = 25.0).valid)
        assertFalse(SettingsStore.MockState(enabled = true, lon = 102.0, lat = 95.0).valid)
    }

    @Test
    fun 默认精度为5米() {
        assertEquals(5f, SettingsStore.MockState().accuracy)
    }

    private fun assertEquals(expected: Float, actual: Float) {
        org.junit.Assert.assertEquals(expected, actual, 0.0001f)
    }
}
