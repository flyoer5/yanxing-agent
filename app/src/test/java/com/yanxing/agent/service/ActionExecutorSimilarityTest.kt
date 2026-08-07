package com.yanxing.agent.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ActionExecutor 相似度匹配引擎测试（纯函数，不依赖 Android 运行时）。
 * 覆盖 findSmartNode 的匹配核心：Levenshtein 相似度计算与阈值边界。
 */
class ActionExecutorSimilarityTest {

    @Test
    fun `identical strings score 1`() {
        assertEquals(1.0f, ActionExecutor.calculateSimilarity("确定", "确定"), 0.001f)
        assertEquals(1.0f, ActionExecutor.calculateSimilarity("搜索", "搜索"), 0.001f)
    }

    @Test
    fun `empty strings score 0`() {
        assertEquals(0.0f, ActionExecutor.calculateSimilarity("", "确定"), 0.001f)
        assertEquals(0.0f, ActionExecutor.calculateSimilarity("确定", ""), 0.001f)
        // 空对空：两者相等，按完全匹配处理
        assertEquals(1.0f, ActionExecutor.calculateSimilarity("", ""), 0.001f)
    }

    @Test
    fun `near identical long text scores high`() {
        // 长文本仅 1 个字符差异 → 高相似度（>0.9 达到 HIGH 阈值）
        val score = ActionExecutor.calculateSimilarity("打开设置页面并调整亮度", "打开设置页面并调整亮读")
        assertTrue(score >= 0.9f)
    }

    @Test
    fun `unrelated strings score near 0`() {
        val score = ActionExecutor.calculateSimilarity("删除", "保存")
        assertTrue(score < 0.5f)
    }

    @Test
    fun `partial match lands between thresholds`() {
        // 半匹配：应落在 MEDIUM(0.7) 与 HIGH(0.9) 之间或之下，体现阈值分级
        val score = ActionExecutor.calculateSimilarity("确定按钮", "取消按钮")
        assertTrue(score in 0.5f..0.9f)
    }

    @Test
    fun `similarity is symmetric`() {
        val a = ActionExecutor.calculateSimilarity("搜索联系人", "联系人搜索")
        val b = ActionExecutor.calculateSimilarity("联系人搜索", "搜索联系人")
        assertEquals(a, b, 0.001f)
    }

    @Test
    fun `similarity is bounded between 0 and 1`() {
        val samples = listOf(
            "确定" to "确定",
            "确定按钮" to "取消按钮",
            "abc" to "xyz",
            "很长的一段文本" to "短",
            "输入框" to "输入",
        )
        for ((s1, s2) in samples) {
            val score = ActionExecutor.calculateSimilarity(s1, s2)
            assertTrue("score $score for ($s1,$s2) out of bounds", score in 0.0f..1.0f)
        }
    }
}