package com.yanxing.agent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话导出格式测试：验证 formatConversation 纯函数输出结构。
 */
class ConversationExporterTest {

    @Test
    fun `empty conversation produces placeholder`() {
        assertEquals("（空会话）", formatConversation("测试会话", emptyList()))
    }

    @Test
    fun `single message contains title role and content`() {
        val text = formatConversation(
            "替我行动",
            listOf(ChatMessage("m1", "user", "帮我打开设置")),
        )
        assertTrue(text.contains("会话：替我行动"))
        assertTrue(text.contains("共 1 条消息"))
        assertTrue(text.contains("【我】"))
        assertTrue(text.contains("帮我打开设置"))
    }

    @Test
    fun `assistant role labeled correctly`() {
        val text = formatConversation(
            "测试",
            listOf(ChatMessage("m1", "assistant", "好的，已为你点击设置")),
        )
        assertTrue(text.contains("【言行】"))
        assertTrue(text.contains("好的，已为你点击设置"))
    }

    @Test
    fun `attachments are annotated`() {
        val text = formatConversation(
            "图片会话",
            listOf(ChatMessage("m1", "user", "看这张图", attachments = listOf(Attachment("a1", "photo.png", "image", "file:///x")))),
        )
        assertTrue(text.contains("[附件 1 个]"))
    }

    @Test
    fun `multiple messages numbered and separated`() {
        val text = formatConversation(
            "长对话",
            listOf(
                ChatMessage("u", "user", "指令一"),
                ChatMessage("a", "assistant", "回复一"),
                ChatMessage("u2", "user", "指令二"),
            ),
        )
        assertTrue(text.contains("共 3 条消息"))
        assertTrue(text.contains("【我】"))
        assertTrue(text.contains("【言行】"))
        assertTrue(text.contains("指令一"))
        assertTrue(text.contains("回复一"))
        assertTrue(text.contains("指令二"))
    }

    @Test
    fun `blank content messages omit content line`() {
        val text = formatConversation(
            title = "会话",
            messages = listOf(
                ChatMessage("a1", "assistant", "有内容"),
                ChatMessage("b1", "user", "   "),
            ),
        )
        // 空白内容消息不打印内容行，但角色标签仍保留
        assertTrue(text.contains("【我】"))
        assertFalse(text.lines().any { it.isBlank() })
        assertTrue(text.contains("有内容"))
    }

    @Test
    fun `system role labeled correctly`() {
        val text = formatConversation(
            title = "会话",
            messages = listOf(ChatMessage("s1", "system", "记忆已更新：用户偏好简洁")),
        )
        assertTrue(text.contains("【系统】"))
        assertTrue(text.contains("记忆已更新"))
    }

    @Test
    fun `unknown role falls back to raw label`() {
        val text = formatConversation(
            title = "会话",
            messages = listOf(ChatMessage("x1", "custom_role", "未知角色消息")),
        )
        assertTrue("实际输出: $text", text.contains("【custom_role】"))
    }
    @Test
    fun `blank title falls back to default name`() {
        val text = formatConversation(
            title = "   ",
            messages = listOf(ChatMessage("m1", "user", "你好")),
        )
        assertTrue(text.contains("会话：未命名会话"))
        assertFalse(text.contains("会话：  "))
    }
}
