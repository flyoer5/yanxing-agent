package com.yanxing.agent.ui

import com.yanxing.agent.data.Memory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRelevanceTest {

    private fun memory(id: String, content: String, category: String = "通用") =
        Memory(id = id, content = content, category = category, isSensitive = false, updatedAt = 0L)

    @Test
    fun `keyword match returns related memories`() {
        val memories = listOf(
            memory("1", "用户喜欢喝美式咖啡", "偏好"),
            memory("2", "项目目标是上线言行 Agent", "项目"),
            memory("3", "今天天气很好", "通用"),
        )
        val result = relevantMemories("咖啡", memories)
        assertEquals(1, result.size)
        assertTrue(result[0].content.contains("咖啡"))
    }

    @Test
    fun `project category boosted by keyword`() {
        val memories = listOf(
            memory("1", "言行项目进入第五十八阶段", "项目"),
            memory("2", "喜欢看科幻电影", "偏好"),
        )
        val result = relevantMemories("项目 进展", memories)
        assertTrue(result.any { it.content.contains("项目") })
        assertTrue(!result.any { it.content.contains("电影") })
    }

    @Test
    fun `result capped at five`() {
        val memories = (1..10).map { memory("m$it", "包含关键词的内容 $it", "通用") }
        val result = relevantMemories("关键词", memories)
        assertTrue(result.size <= 5)
    }

    @Test
    fun `no match returns empty`() {
        val result = relevantMemories("不存在的词", listOf(memory("1", "别的内容")))
        assertTrue(result.isEmpty())
    }
}
