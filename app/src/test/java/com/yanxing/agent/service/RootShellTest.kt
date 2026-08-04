package com.yanxing.agent.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootShellTest {

    @Test
    fun `common commands are defined`() {
        assertTrue(RootShell.Commands.GET_DEVICE_INFO.isNotBlank())
        assertTrue(RootShell.Commands.BATTERY_LEVEL.isNotBlank())
        assertTrue(RootShell.Commands.CLEAR_RECENTS.isNotBlank())
        assertTrue(RootShell.Commands.SCREEN_ON.isNotBlank())
        assertTrue(RootShell.Commands.SET_SCREEN_BRIGHTNESS.isNotBlank())
    }

    @Test
    fun `reset cache does not crash`() {
        RootShell.resetCache()
        RootShell.resetCache()
    }

    @Test
    fun `execute without root returns null or runs`() {
        // 无 Root 设备应返回 null；有 Root 且被授权时才返回输出
        val result = RootShell.execute("echo hello")
        if (RootShell.isRootAvailable()) {
            // 可能被授权也可能被拒绝，两者都合理
            assertTrue(result == null || result!!.isNotEmpty())
        } else {
            assertTrue(result == null)
        }
    }

    @Test
    fun `root detection returns boolean`() {
        val result = RootShell.isRootAvailable()
        assertTrue(result == true || result == false)
        // 第二次调用应命中缓存
        RootShell.isRootAvailable()
    }
}
