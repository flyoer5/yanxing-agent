package com.yanxing.agent.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptTest {

    private val engine = AIDecisionEngine()

    @Test
    fun `system prompt has system role and core goals`() {
        val dto = engine.generateSystemPrompt(currentScreen = "屏幕内容：设置")
        assertEquals("system", dto.role)
        val content = dto.content
        assertTrue(content.contains("替我行动模式"))
        assertTrue(content.contains("只返回 JSON"))
        assertTrue(content.contains("当前屏幕内容"))
    }

    @Test
    fun `system prompt includes current screen text`() {
        val dto = engine.generateSystemPrompt(currentScreen = "屏幕内容：应用列表")
        assertTrue(dto.content.contains("屏幕内容：应用列表"))
    }

    @Test
    fun `system prompt includes last action when provided`() {
        val dto = engine.generateSystemPrompt(currentScreen = "屏幕内容：设置", lastAction = "点击：关于手机")
        assertTrue(dto.content.contains("上一步操作：点击：关于手机"))
    }

    @Test
    fun `system prompt omits last action when absent`() {
        val dto = engine.generateSystemPrompt(currentScreen = "屏幕内容：设置")
        assertTrue(!dto.content.contains("上一步操作"))
    }

    @Test
    fun `system prompt lists custom constraints in order`() {
        val dto = engine.generateSystemPrompt(
            currentScreen = "屏幕内容：设置",
            constraints = listOf("不得点击删除", "优先使用可见文本"),
        )
        val content = dto.content
        assertTrue(content.contains("1. 不得点击删除"))
        assertTrue(content.contains("2. 优先使用可见文本"))
    }

    @Test
    fun `system prompt uses default constraints when none given`() {
        val dto = engine.generateSystemPrompt(currentScreen = "屏幕内容：设置")
        assertTrue(dto.content.contains("约束规则"))
        // 默认约束至少有一条
        assertTrue(AIDecisionEngine.DEFAULT_CONSTRAINTS.isNotEmpty())
    }
}