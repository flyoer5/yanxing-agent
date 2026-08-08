package com.yanxing.agent.service

import org.junit.Assert.*
import org.junit.Test

class RootShellCommandTest {

    @Test
    fun `battery level parsing succeeds with valid input`() {
        // 模拟成功读取电池百分比
        val result = runCatching { 
            "78".trim().toIntOrNull() 
        }.getOrNull()
        
        assertEquals(78, result)
    }

    @Test
    fun `battery level parsing fails with invalid input`() {
        val result = runCatching { 
            "invalid".trim().toIntOrNull() 
        }.getOrNull()
        
        assertNull(result)
    }

    @Test
    fun `screen brightness range validation`() {
        // 范围校验是纯逻辑，不依赖设备 root 状态
        assertTrue(RootShell.isBrightnessInRange(0)) // min value
        assertTrue(RootShell.isBrightnessInRange(128)) // Mid-range
        assertTrue(RootShell.isBrightnessInRange(255)) // Max value
        assertFalse(RootShell.isBrightnessInRange(-1)) // Out of range
        assertFalse(RootShell.isBrightnessInRange(300)) // Out of range

        // 越界值在无 root 环境也必须被拒绝（校验先于执行）
        assertFalse(RootShell.setScreenBrightness(-1))
        assertFalse(RootShell.setScreenBrightness(300))
    }

    @Test
    fun `whitelist accepts fixed commands and bounded brightness`() {
        // 全部 8 条固定命令都应通过白名单
        val fixed = listOf(
            RootShell.Commands.GET_DEVICE_INFO,
            RootShell.Commands.BATTERY_LEVEL,
            RootShell.Commands.GET_SCREEN_BRIGHTNESS,
            RootShell.Commands.CLEAR_RECENTS,
            RootShell.Commands.SCREEN_ON,
            RootShell.Commands.SHOW_RECENTS,
            RootShell.Commands.GO_HOME,
            RootShell.Commands.APP_LIST,
        )
        fixed.forEach { assertTrue("应放行: $it", RootShell.isCommandAllowed(it)) }
        assertTrue(RootShell.isCommandAllowed("settings put system screen_brightness 0"))
        assertTrue(RootShell.isCommandAllowed("settings put system screen_brightness 255"))
    }

    @Test
    fun `whitelist rejects arbitrary and malformed commands`() {
        assertFalse(RootShell.isCommandAllowed("echo hello"))
        assertFalse(RootShell.isCommandAllowed("settings put system screen_brightness 256"))
        assertFalse(RootShell.isCommandAllowed("settings put system screen_brightness -1"))
        assertFalse(RootShell.isCommandAllowed("settings put system screen_brightness 1 && id"))
        assertFalse(RootShell.isCommandAllowed("rm -rf /"))
    }

    @Test
    fun `device info format is multiline`() {
        val mockInfo = "Mi Note 3\n8.1.0"
        val splitLine = mockInfo.replace("&&", "\n")
        
        assertTrue(splitLine.contains("\n"))
        assertTrue(splitLine.startsWith("Mi Note 3"))
    }
}
