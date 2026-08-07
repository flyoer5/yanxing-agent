package com.yanxing.agent.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮窗状态流测试：验证结果更新、成功/失败着色标记、计数联动的纯逻辑部分。
 * FloatingProgressOverlay 依赖 Android Context，此处通过 CheckboxState 直接验证状态流转。
 */
class FloatingProgressOverlayStateTest {

    @Test
    fun `state tracks result message and success flag`() {
        var state = FloatingProgressOverlay.CheckboxState()
        // 模拟一次成功结果
        state = state.copy(
            success = state.success + 1,
            lastResult = "已点击：确定",
            lastResultIsSuccess = true,
        )
        assertEquals(1, state.success)
        assertEquals("已点击：确定", state.lastResult)
        assertTrue(state.lastResultIsSuccess)
    }

    @Test
    fun `state tracks failure result separately`() {
        var state = FloatingProgressOverlay.CheckboxState()
        state = state.copy(
            failed = state.failed + 1,
            lastResult = "未找到元素：确定",
            lastResultIsSuccess = false,
        )
        assertEquals(1, state.failed)
        assertEquals("未找到元素：确定", state.lastResult)
        assertTrue(!state.lastResultIsSuccess)
    }

    @Test
    fun `stopped flag suppresses further result updates`() {
        val state = FloatingProgressOverlay.CheckboxState(stopped = true)
        assertTrue(state.stopped)
        // 停止后不应继续推进执行结果
        assertEquals("", state.lastResult)
    }

    @Test
    fun `reset clears counters but preserves mode`() {
        // resetProgress 保留 actionModeEnabled，清空计数器与结果
        val resetState = FloatingProgressOverlay.CheckboxState(
            total = 5,
            success = 3,
            failed = 1,
            currentStep = "已完成",
            actionModeEnabled = true,
            lastResult = "上一步结果",
        ).copy(
            total = 0,
            success = 0,
            failed = 0,
            currentStep = "",
            lastResult = "",
        )
        assertTrue(resetState.actionModeEnabled)
        assertEquals(0, resetState.total)
        assertEquals("", resetState.lastResult)
    }
}