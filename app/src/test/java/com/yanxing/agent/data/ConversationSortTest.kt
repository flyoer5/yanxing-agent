package com.yanxing.agent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话排序纯函数测试（第一百二十二阶段：会话置顶）
 */
class ConversationSortTest {

    private fun conv(id: String, pinned: Boolean = false, updatedAt: Long): Conversation =
        Conversation(id = id, title = "会话$id", groupId = null, pinned = pinned, updatedAt = updatedAt)

    @Test
    fun `置顶会话排在最前`() {
        val list = listOf(
            conv("a", pinned = false, updatedAt = 3000),
            conv("b", pinned = true, updatedAt = 2000),
            conv("c", pinned = false, updatedAt = 1000),
        )
        val sorted = sortConversations(list)
        assertEquals(listOf("b", "a", "c"), sorted.map { it.id })
    }

    @Test
    fun `全部置顶时按更新时间倒序`() {
        val list = listOf(
            conv("a", pinned = true, updatedAt = 1000),
            conv("b", pinned = true, updatedAt = 5000),
            conv("c", pinned = true, updatedAt = 3000),
        )
        val sorted = sortConversations(list)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun `全部未置顶时保持更新时间倒序`() {
        val list = listOf(
            conv("a", updatedAt = 2000),
            conv("b", updatedAt = 4000),
            conv("c", updatedAt = 3000),
        )
        val sorted = sortConversations(list)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun `置顶与未置顶混合时组内各自倒序`() {
        val list = listOf(
            conv("a", pinned = true, updatedAt = 1000),   // 置顶组较旧
            conv("b", pinned = true, updatedAt = 9000),   // 置顶组最新
            conv("c", updatedAt = 8000),                  // 未置顶组最新
            conv("d", updatedAt = 5000),                  // 未置顶组较旧
        )
        val sorted = sortConversations(list)
        assertEquals(listOf("b", "a", "c", "d"), sorted.map { it.id })
    }

    @Test
    fun `空列表与单元素不崩溃`() {
        assertTrue(sortConversations(emptyList()).isEmpty())
        val single = sortConversations(listOf(conv("x", updatedAt = 1)))
        assertEquals(listOf("x"), single.map { it.id })
    }

    @Test
    fun `更新时间相同时置顶仍优先`() {
        val list = listOf(
            conv("a", pinned = false, updatedAt = 1000),
            conv("b", pinned = true, updatedAt = 1000),
        )
        val sorted = sortConversations(list)
        assertEquals(listOf("b", "a"), sorted.map { it.id })
    }
    @Test
    fun `删除当前会话后跳过已归档候选`() {
        val conversations = listOf(
            conv("deleted"),
            conv("archived", archived = true),
            conv("active"),
        )
        assertEquals("active", nextConversationAfterDelete(conversations, "deleted")?.id)
    }

    @Test
    fun `删除列表中唯一未归档会话时无候选`() {
        val conversations = listOf(
            conv("deleted"),
            conv("archived", archived = true),
        )
        assertEquals(null, nextConversationAfterDelete(conversations, "deleted"))
    }

    @Test
    fun `删除非当前项时仍排除指定会话`() {
        val conversations = listOf(conv("first"), conv("second"))
        assertEquals("first", nextConversationAfterDelete(conversations, "second")?.id)
    }
}
