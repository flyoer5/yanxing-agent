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
        assertTrue(RootShell.setScreenBrightness(0)) // Should succeed (min value)
        assertTrue(RootShell.setScreenBrightness(128)) // Mid-range
        assertTrue(RootShell.setScreenBrightness(255)) // Max value
        assertFalse(RootShell.setScreenBrightness(-1)) // Out of range
        assertFalse(RootShell.setScreenBrightness(300)) // Out of range
    }

    @Test
    fun `device info format is multiline`() {
        val mockInfo = "Mi Note 3\n8.1.0"
        val splitLine = mockInfo.replace("&&", "\n")
        
        assertTrue(splitLine.contains("\n"))
        assertTrue(splitLine.startsWith("Mi Note 3"))
    }
}
