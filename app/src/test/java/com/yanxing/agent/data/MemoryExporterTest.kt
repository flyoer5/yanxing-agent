package com.yanxing.agent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆导出格式测试：验证 formatMemories 纯函数输出结构。
 */
class MemoryExporterTest {

    @Test
    fun `empty memories produce placeholder`() {
        assertEquals("暂无长期记忆", formatMemories(emptyList()))
    }

    @Test
    fun `single memory contains content and category`() {
        val text = formatMemories(
            listOf(Memory("m1", "用户喜欢简洁回复", "preference", isSensitive = false, updatedAt = 0L)),
        )
        assertTrue(text.contains("言行 Agent 长期记忆"))
        assertTrue(text.contains("共 1 条记忆"))
        assertTrue(text.contains("用户喜欢简洁回复"))
        assertTrue(text.contains("分类：preference"))
    }

    @Test
    fun `sensitive memory is annotated`() {
        val text = formatMemories(
            listOf(Memory("m1", "家庭住址在朝阳区", "personal", isSensitive = true, updatedAt = 0L)),
        )
        assertTrue(text.contains("⚠️ 敏感记忆"))
    }

    @Test
    fun `multiple memories numbered`() {
        val text = formatMemories(
            listOf(
                Memory("a", "记忆一", "cat1", isSensitive = false, updatedAt = 0L),
                Memory("b", "记忆二", "cat2", isSensitive = false, updatedAt = 0L),
            ),
        )
        assertTrue(text.contains("共 2 条记忆"))
        assertTrue(text.contains("[1] 记忆一"))
        assertTrue(text.contains("[2] 记忆二"))
    }

    @Test
    fun `no sensitive flag means no warning`() {
        val text = formatMemories(
            listOf(Memory("a", "普通记忆", "general", isSensitive = false, updatedAt = 0L)),
        )
        assertTrue(!text.contains("敏感"))
    }
}