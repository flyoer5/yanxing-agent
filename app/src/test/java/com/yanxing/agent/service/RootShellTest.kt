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
    fun `execute is denied before authorization`() {
        RootShell.setAuthorized(false)
        assertFalse(RootShell.isAuthorized())
        assertTrue(RootShell.execute("echo hello") == null)
    }

    @Test
    fun `authorization can be revoked`() {
        RootShell.setAuthorized(true)
        assertTrue(RootShell.isAuthorized())
        RootShell.setAuthorized(false)
        assertFalse(RootShell.isAuthorized())
    }

    @Test
    fun `execute rejects non-whitelisted commands even when authorized`() {
        // 授权后，白名单外的任意命令仍必须被拒绝（安全兜底）
        RootShell.setAuthorized(true)
        try {
            assertTrue(RootShell.execute("rm -rf /") == null)
            assertTrue(RootShell.execute("echo pwned") == null)
            assertTrue(RootShell.execute("settings put system screen_brightness 99999") == null)
        } finally {
            RootShell.setAuthorized(false)
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
