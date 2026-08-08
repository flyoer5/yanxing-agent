package com.yanxing.agent.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮窗主题配色与边缘吸附测试（纯逻辑，不依赖 Android 运行时）。
 */
class OverlayThemeTest {

    /** 计算颜色亮度（0-255），用于对比度断言 */
    private fun luminance(color: Int): Double {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    @Test
    fun `dark mode uses dark card and light text`() {
        val colors = resolveOverlayColors(darkMode = true)
        // 深色卡片亮度低
        assertTrue(luminance(colors.cardBackground) < 64)
        // 标题/正文为浅色，与深色背景有足够对比
        assertTrue(luminance(colors.titleText) > 200)
        assertTrue(luminance(colors.bodyText) > 180)
    }

    @Test
    fun `light mode uses light card and dark text`() {
        val colors = resolveOverlayColors(darkMode = false)
        assertTrue(luminance(colors.cardBackground) > 250)
        assertTrue(luminance(colors.titleText) < 32)
    }

    @Test
    fun `dark and light palettes differ`() {
        val dark = resolveOverlayColors(darkMode = true)
        val light = resolveOverlayColors(darkMode = false)
        assertTrue(dark.cardBackground != light.cardBackground)
        assertTrue(dark.titleText != light.titleText)
        assertTrue(dark.bodyText != light.bodyText)
    }

    @Test
    fun `success and failure colors are distinct and visible on both themes`() {
        for (dark in listOf(true, false)) {
            val colors = resolveOverlayColors(dark)
            assertTrue(colors.successText != colors.failureText)
            // 成功/失败色不应与卡片背景同色（保证可读性）
            assertTrue(colors.successText != colors.cardBackground)
            assertTrue(colors.failureText != colors.cardBackground)
            // 按钮文字与按钮背景有对比
            assertTrue(colors.buttonText != colors.stopButtonBackground)
            assertTrue(colors.buttonText != colors.undoButtonBackground)
        }
    }

    @Test
    fun `snap to nearest horizontal edge`() {
        // 屏幕 1080，窗口 320：maxX=760，midX=380
        assertEquals(0, resolveSnapX(currentX = 100, screenWidth = 1080, windowWidth = 320))    // 靠右
        assertEquals(760, resolveSnapX(currentX = 400, screenWidth = 1080, windowWidth = 320))  // 靠左
        assertEquals(760, resolveSnapX(currentX = 500, screenWidth = 1080, windowWidth = 320))  // 靠左
        assertEquals(760, resolveSnapX(currentX = 380, screenWidth = 1080, windowWidth = 320))  // 正中按实现贴左
    }

    @Test
    fun `snap clamps when window wider than screen`() {
        // 窗口比屏幕宽时贴回 0，不产生负坐标
        assertEquals(0, resolveSnapX(currentX = 50, screenWidth = 300, windowWidth = 400))
        assertEquals(0, resolveSnapX(currentX = -20, screenWidth = 300, windowWidth = 400))
    }

    @Test
    fun `clamp window y keeps overlay on screen`() {
        // 负值 clamp 到 0（顶部）
        assertEquals(0, clampWindowY(currentY = -50, screenHeight = 800, windowHeight = 400))
        // 正常范围内不变
        assertEquals(200, clampWindowY(currentY = 200, screenHeight = 800, windowHeight = 400))
        // 超出底部 clamp 到 maxY
        assertEquals(400, clampWindowY(currentY = 900, screenHeight = 800, windowHeight = 400))
        // 窗口比屏幕还高时 maxY=0，只能贴顶
        assertEquals(0, clampWindowY(currentY = 100, screenHeight = 300, windowHeight = 400))
    }
}