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
    fun `零时间戳也能格式化`() {
        assertEquals("00:00", formatMessageTime(0L))
    }
}
