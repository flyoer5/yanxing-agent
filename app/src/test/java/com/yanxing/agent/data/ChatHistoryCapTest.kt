package com.yanxing.agent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryCapTest {

    private fun msg(id: Int, content: String) =
        ChatMessage(id = "m$id", role = "user", content = content, createdAt = id.toLong())

    @Test
    fun `短历史原样保留`() {
        val history = listOf(msg(1, "你好"), msg(2, "在吗"))
        assertEquals(history, capHistoryForRequest(history))
    }

    @Test
    fun `超过条数上限只保留最近的`() {
        val history = (1..50).map { msg(it, "消息$it") }
        val capped = capHistoryForRequest(history, maxMessages = 10, maxChars = 100_000)
        assertEquals(10, capped.size)
        assertEquals("消息41", capped.first().content)
        assertEquals("消息50", capped.last().content)
    }

    @Test
    fun `超过字符上限从最旧开始丢弃`() {
        val history = listOf(
            msg(1, "a".repeat(300)),
            msg(2, "b".repeat(300)),
            msg(3, "c".repeat(300)),
        )
        val capped = capHistoryForRequest(history, maxMessages = 100, maxChars = 500)
        assertTrue(capped.size in 1..2)
        assertEquals("c".repeat(300), capped.last().content)
    }

    @Test
    fun `单条超长消息不会全部丢弃`() {
        val history = listOf(msg(1, "x".repeat(10_000)))
        val capped = capHistoryForRequest(history, maxMessages = 30, maxChars = 100)
        assertEquals(1, capped.size) // 保底保留最近一条
    }
}
