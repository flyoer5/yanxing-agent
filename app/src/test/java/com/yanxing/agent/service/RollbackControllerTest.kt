package com.yanxing.agent.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RollbackControllerTest {

    @Test
    fun `click suggests a back action`() {
        val s = RollbackController.suggestRollback(
            AIDecisionEngine.Action.Click("确定按钮"), "com.example"
        )
        assertNotNull(s)
        assertEquals(1, s!!.actions.size)
        assertTrue(s.actions[0] is AIDecisionEngine.Action.Back)
        assertTrue(s.confidence >= 0.8f)
    }

    @Test
    fun `input text suggests clearing the field`() {
        val s = RollbackController.suggestRollback(
            AIDecisionEngine.Action.InputText("searchBar", "hello"), "com.example"
        )
        assertNotNull(s)
        val clear = s!!.actions[0] as? AIDecisionEngine.Action.ClearText
        assertNotNull(clear)
        assertEquals("searchBar", clear!!.query)
    }

    @Test
    fun `swipe suggests opposite direction`() {
        val s = RollbackController.suggestRollback(
            AIDecisionEngine.Action.Swipe(AIDecisionEngine.SwipeDirection.UP), "com.example"
        )
        assertNotNull(s)
        val reverse = s!!.actions[0] as? AIDecisionEngine.Action.Swipe
        assertNotNull(reverse)
        assertEquals(AIDecisionEngine.SwipeDirection.DOWN, reverse!!.direction)
    }

    @Test
    fun `longPress is not auto reversible but provides manual guidance`() {
        val s = RollbackController.suggestRollback(
            AIDecisionEngine.Action.LongPress("某个元素"), "com.example"
        )
        assertNotNull(s)
        // 不能自动逆转
        assertTrue(s!!.actions.isEmpty())
        // 但必须提供手动引导步骤
        assertTrue(s.manualSteps.isNotEmpty())
        assertNotNull(s.warning)
    }

    @Test
    fun `back action is not auto reversible but provides guidance`() {
        val s = RollbackController.suggestRollback(
            AIDecisionEngine.Action.Back, "com.example"
        )
        assertNotNull(s)
        assertTrue(s!!.actions.isEmpty())
        assertTrue(s.manualSteps.isNotEmpty())
    }

    @Test
    fun `clear text is not auto reversible but provides guidance`() {
        val s = RollbackController.suggestRollback(
            AIDecisionEngine.Action.ClearText("queryBar"), "com.example"
        )
        assertNotNull(s)
        assertTrue(s!!.actions.isEmpty())
        assertTrue(s.manualSteps.isNotEmpty())
    }

    @Test
    fun `all swipe directions produce a valid reverse`() {
        // 每个方向的逆操作都应返回单个反向滑动，且方向正确成对
        val cases = mapOf(
            AIDecisionEngine.SwipeDirection.UP to AIDecisionEngine.SwipeDirection.DOWN,
            AIDecisionEngine.SwipeDirection.DOWN to AIDecisionEngine.SwipeDirection.UP,
            AIDecisionEngine.SwipeDirection.LEFT to AIDecisionEngine.SwipeDirection.RIGHT,
            AIDecisionEngine.SwipeDirection.RIGHT to AIDecisionEngine.SwipeDirection.LEFT,
        )
        cases.forEach { (dir, opposite) ->
            val s = RollbackController.suggestRollback(AIDecisionEngine.Action.Swipe(dir), "com.example")
            assertNotNull("$dir should produce a suggestion", s)
            val reverse = s!!.actions.singleOrNull() as? AIDecisionEngine.Action.Swipe
            assertNotNull("$dir reverse should be a Swipe", reverse)
            assertEquals(opposite, reverse!!.direction)
        }
    }
}