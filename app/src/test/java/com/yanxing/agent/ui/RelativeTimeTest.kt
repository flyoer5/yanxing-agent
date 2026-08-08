package com.yanxing.agent.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `just now within a minute`() {
        assertEquals("刚刚", formatRelativeTime(now - 30_000, now))
    }

    @Test
    fun `minutes ago`() {
        assertEquals("3 分钟前", formatRelativeTime(now - 3 * 60_000, now))
    }

    @Test
    fun `hours ago`() {
        assertEquals("2 小时前", formatRelativeTime(now - 2 * 3_600_000, now))
    }

    @Test
    fun `days ago`() {
        assertEquals("5 天前", formatRelativeTime(now - 5 * 86_400_000, now))
    }

    @Test
    fun `over a week is earlier`() {
        assertEquals("更早", formatRelativeTime(now - 10 * 86_400_000, now))
    }
}