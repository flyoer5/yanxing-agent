package com.yanxing.agent.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTimeTest {

    @Test
    fun `消息时间格式为小时分钟`() {
        val timestamp = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            java.util.Locale.getDefault(),
        ).parse("2026-08-13 09:07")!!.time
        assertEquals("09:07", formatMessageTime(timestamp))
    }

    @Test
    fun `跨天显示月日与时间`() {
        val parser = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            java.util.Locale.getDefault(),
        )
        val now = parser.parse("2026-08-13 09:07")!!.time
        val previousDay = parser.parse("2026-08-12 23:05")!!.time
        assertEquals("8-12 23:05", formatMessageTime(previousDay, now))
    }

    @Test
    fun `同一天保持小时分钟格式`() {
        val parser = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            java.util.Locale.getDefault(),
        )
        val now = parser.parse("2026-08-13 09:07")!!.time
        val sameDay = parser.parse("2026-08-13 08:05")!!.time
        assertEquals("08:05", formatMessageTime(sameDay, now))
    }
}


    @Test
    fun `消息状态摘要包含时间与编辑状态`() {
        val parser = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            java.util.Locale.getDefault(),
        )
        val now = parser.parse("2026-08-13 09:07")!!.time
        val message = parser.parse("2026-08-13 08:05")!!.time
        assertEquals("发送时间 08:05，已编辑", formatMessageStatus(message, true, now))
    }

    @Test
    fun `无时间戳时只显示编辑状态`() {
        assertEquals("已编辑", formatMessageStatus(0L, true, 1L))
    }
