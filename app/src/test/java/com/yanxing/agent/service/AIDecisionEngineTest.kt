package com.yanxing.agent.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AIDecisionEngineTest {

    @Test
    fun `parse click and swipe actions`() {
        val json = """{"actions":[{"action":"click","query":"设置"},{"action":"swipe","direction":"DOWN"}]}"""
        val sequence = AIDecisionEngine.parseLLMResponse(json)

        assertTrue(sequence.success)
        assertFalse(sequence.done)
        assertEquals(2, sequence.actions.size)
        assertTrue(sequence.actions[0] is AIDecisionEngine.Action.Click)
        assertEquals("设置", (sequence.actions[0] as AIDecisionEngine.Action.Click).query)
        assertTrue(sequence.actions[1] is AIDecisionEngine.Action.Swipe)
    }

    @Test
    fun `parse input text action`() {
        val json = """{"actions":[{"action":"input_text","query":"搜索框","text":"你好"}]}"""
        val sequence = AIDecisionEngine.parseLLMResponse(json)

        val action = sequence.actions.first() as AIDecisionEngine.Action.InputText
        assertEquals("搜索框", action.query)
        assertEquals("你好", action.text)
    }

    @Test
    fun `parse done true means task finished`() {
        val json = """{"done":true,"reason":"已到达设置页"}"""
        val sequence = AIDecisionEngine.parseLLMResponse(json)

        assertTrue(sequence.success)
        assertTrue(sequence.done)
        assertTrue(sequence.actions.isEmpty())
    }

    @Test
    fun `parse done false with empty actions is not done`() {
        val json = """{"done":false,"actions":[]}"""
        val sequence = AIDecisionEngine.parseLLMResponse(json)

        assertFalse(sequence.success)
        assertFalse(sequence.done)
    }

    @Test
    fun `parse garbage returns empty sequence`() {
        val sequence = AIDecisionEngine.parseLLMResponse("这不是 JSON")

        assertFalse(sequence.success)
        assertTrue(sequence.actions.isEmpty())
    }

    @Test
    fun `continuation prompt contains goal round and history`() {
        val prompt = AIDecisionEngine.generateContinuationPrompt(
            goal = "帮我打开设置",
            currentScreen = "主屏幕",
            lastResult = "点击[设置] → 成功",
            round = 2,
            maxRounds = 5,
        )

        val content = prompt.content.orEmpty()
        assertTrue(content.contains("帮我打开设置"))
        assertTrue(content.contains("第 2 轮决策"))
        assertTrue(content.contains("点击[设置] → 成功"))
    }

    @Test
    fun `continuation prompt limits rounds`() {
        val prompt = AIDecisionEngine.generateContinuationPrompt(
            goal = "测试",
            currentScreen = "屏幕",
            round = 4,
            maxRounds = 5,
        )

        assertTrue(prompt.content.orEmpty().contains("上限 5 轮"))
    }
}
