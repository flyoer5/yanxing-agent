package com.yanxing.agent.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationFilterTest {

    private fun conv(
        id: String,
        title: String = id,
        groupId: String? = null,
        archived: Boolean = false,
        pinned: Boolean = false,
    ) = Conversation(
        id = id,
        title = title,
        groupId = groupId,
        pinned = pinned,
        archived = archived,
        updatedAt = 1L,
    )

    @Test
    fun `默认隐藏归档会话`() {
        val visible = filterConversations(listOf(conv("active"), conv("old", archived = true)), "")
        assertEquals(listOf("active"), visible.map { it.id })
    }

    @Test
    fun `显示归档开关包含归档会话`() {
        val visible = filterConversations(
            listOf(conv("active"), conv("old", archived = true)),
            query = "",
            showArchived = true,
        )
        assertEquals(listOf("active", "old"), visible.map { it.id })
    }

    @Test
    fun `搜索可以发现归档会话`() {
        val visible = filterConversations(
            listOf(conv("old", title = "历史项目", archived = true)),
            query = "历史",
        )
        assertEquals(listOf("old"), visible.map { it.id })
    }

    @Test
    fun `内容命中可以发现归档会话`() {
        val visible = filterConversations(
            listOf(conv("old", archived = true)),
            query = "关键词",
            contentMatchIds = setOf("old"),
        )
        assertEquals(listOf("old"), visible.map { it.id })
    }

    @Test
    fun `分组筛选与归档筛选同时生效`() {
        val conversations = listOf(
            conv("active-a", groupId = "a"),
            conv("active-b", groupId = "b"),
            conv("archived-a", groupId = "a", archived = true),
        )
        assertEquals(
            listOf("active-a"),
            filterConversations(conversations, "", groupId = "a").map { it.id },
        )
        assertEquals(
            listOf("active-a", "archived-a"),
            filterConversations(conversations, "", groupId = "a", showArchived = true).map { it.id },
        )
    }

    @Test
    fun `空输入不改变非归档会话顺序`() {
        val conversations = listOf(conv("one"), conv("two"))
        assertEquals(conversations, filterConversations(conversations, ""))
    }
}