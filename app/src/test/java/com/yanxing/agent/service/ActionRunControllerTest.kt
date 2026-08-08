package com.yanxing.agent.service

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActionRunControllerTest {

    private lateinit var controller: ActionRunController

    @Before
    fun setup() {
        controller = ActionRunController(maxRounds = 3)
    }

    @Test
    fun `初始状态 - 未启动`() {
        assertFalse(controller.isRunning)
        assertFalse(controller.isCancelled)
        assertEquals(0, controller.round)
    }

    @Test
    fun `启动任务后进入第一轮`() {
        controller.start()
        assertTrue(controller.isRunning)
        assertFalse(controller.isCancelled)
        assertEquals(1, controller.round)
    }

    @Test
    fun `停止成功返回 true，重复停止返回 false`() {
        controller.start()
        assertTrue(controller.cancel())
        assertTrue(controller.isCancelled)
        assertFalse(controller.cancel()) // 重复停止
    }

    @Test
    fun `停止后不能继续下一轮`() {
        controller.start()
        controller.cancel()
        assertFalse(controller.canContinue())
    }

    @Test
    fun `未到上限可以继续`() {
        controller.start()
        assertTrue(controller.canContinue())
        controller.nextRound()
        assertTrue(controller.canContinue())
        controller.nextRound()
        assertFalse(controller.canContinue()) // 第 3 轮达到上限
    }

    @Test
    fun `轮次推进正确递增`() {
        controller.start()
        assertEquals(1, controller.round)
        assertEquals(2, controller.nextRound())
        assertEquals(2, controller.round)
        assertEquals(3, controller.nextRound())
        assertEquals(3, controller.round)
    }

    @Test
    fun `停止后推进轮次不再增长`() {
        controller.start()
        controller.cancel()
        val beforeRound = controller.round
        assertEquals(beforeRound, controller.nextRound())
        assertEquals(beforeRound, controller.round)
    }

    @Test
    fun `重置后轮次归零但停止标记保留`() {
        controller.start()
        controller.cancel()
        controller.reset()
        assertEquals(0, controller.round)
        assertTrue(controller.isCancelled) // 停止标记保留，避免残留协程继续执行
    }

    @Test
    fun `重新启动清除停止标记`() {
        controller.start()
        controller.cancel()
        controller.reset()
        controller.start()
        assertFalse(controller.isCancelled)
        assertEquals(1, controller.round)
    }

    @Test
    fun `未启动时停止返回 false`() {
        assertFalse(controller.cancel())
    }

    @Test
    fun `达到轮次上限后不再继续`() {
        val capped = ActionRunController(maxRounds = 3)
        capped.start() // round=1
        capped.nextRound() // 2
        capped.nextRound() // 3
        assertEquals(3, capped.round)
        // 已达上限：canContinue 应为 false（未停止，但无剩余轮次）
        assertFalse(capped.canContinue())
    }

    @Test
    fun `nextRound cannot exceed max rounds`() {
        val capped = ActionRunController(maxRounds = 2)
        capped.start() // round=1
        capped.nextRound() // 2
        capped.nextRound() // 仍为 2（不再增长，避免死循环膨胀）
        capped.nextRound()
        assertEquals(2, capped.round)
    }
}
