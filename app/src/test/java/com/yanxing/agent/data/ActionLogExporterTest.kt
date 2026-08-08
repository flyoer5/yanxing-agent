package com.yanxing.agent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行动日志导出格式测试：验证 formatActionLogs 纯函数输出结构。
 */
class ActionLogExporterTest {

    private fun sampleLog(
        id: String = "log-1",
        actionType: String = "click",
        status: String = "success",
        target: String? = "设置",
        error: String? = null,
    ) = ActionLogEntity(
        id = id,
        timestamp = 1_700_000_000_000L, // 2023-11-14 22:13:20
        packageName = "com.android.settings",
        actionType = actionType,
        targetElement = target,
        details = "点击元素：设置",
        status = status,
        errorMessage = error,
    )

    @Test
    fun `timestamp is formatted with date and time pattern`() {
        // 2023-11-14 22:13:20 UTC
        val formatted = formatLogTimestamp(1_700_000_000_000L)
        assertTrue("实际输出: $formatted", formatted.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")))
    }

    @Test
    fun `empty logs produce placeholder text`() {
        assertEquals("暂无操作日志", formatActionLogs(emptyList()))
    }

    @Test
    fun `single log contains header fields`() {
        val text = formatActionLogs(listOf(sampleLog()))
        assertTrue(text.contains("言行 Agent 操作日志"))
        assertTrue(text.contains("共 1 条记录"))
        assertTrue(text.contains("应用：com.android.settings"))
        assertTrue(text.contains("动作：点击"))
        assertTrue(text.contains("目标：设置"))
        assertTrue(text.contains("状态：成功"))
    }

    @Test
    fun `failed log includes error message`() {
        val log = sampleLog(status = "failed", error = "未找到元素")
        val text = formatActionLogs(listOf(log))
        assertTrue(text.contains("状态：失败"))
        assertTrue(text.contains("错误：未找到元素"))
    }

    @Test
    fun `multiple logs numbered sequentially`() {
        val logs = listOf(
            sampleLog(id = "a", actionType = "click"),
            sampleLog(id = "b", actionType = "swipe", target = null),
        )
        val text = formatActionLogs(logs)
        assertTrue(text.contains("共 2 条记录"))
        assertTrue(text.contains("[1]"))
        assertTrue(text.contains("[2]"))
        assertTrue(text.contains("动作：滑动"))
    }

    @Test
    fun `details equal to target is not duplicated`() {
        val log = sampleLog().copy(details = "设置")
        val text = formatActionLogs(listOf(log))
        // 详情与目标相同 → 只打印一行"目标：设置"，不重复"详情：设置"
        assertEquals(1, text.lines().count { it.contains("设置") })
    }

    @Test
    fun `blank details are omitted`() {
        val log = sampleLog().copy(details = "")
        val text = formatActionLogs(listOf(log))
        assertTrue(!text.contains("详情："))
    }

    @Test
    fun `label mapping covers rollback types`() {
        assertEquals("点击", actionTypeLabel("click"))
        assertEquals("回滚", actionTypeLabel("rollback"))
        assertEquals("返回", actionTypeLabel("back"))
        assertEquals("清空输入", actionTypeLabel("clear_text"))
        assertEquals("成功", actionStatusLabel("success"))
        assertEquals("失败", actionStatusLabel("failed"))
        assertEquals("已取消", actionStatusLabel("cancelled"))
    }

    @Test
    fun `timestamp handles epoch and negative values`() {
        // 极值时间戳不应崩溃，且格式保持一致
        assertEquals(19, formatLogTimestamp(0L).length)
        assertEquals(19, formatLogTimestamp(-1_000L).length)
        assertTrue(formatLogTimestamp(0L).matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")))
    }

    @Test
    fun `log time shows date for non-today`() {
        val now = 1_700_000_000_000L
        // 昨天的日志应含日期（M-d），今天的只含时间
        val yesterday = now - 86_400_000L
        val todayText = formatLogTime(now, now)
        val yesterdayText = formatLogTime(yesterday, now)
        assertTrue(todayText, todayText.matches(Regex("""\d{2}:\d{2}:\d{2}""")))
        assertTrue(yesterdayText, yesterdayText.matches(Regex("""\d{1,2}-\d{1,2} \d{2}:\d{2}""")))
    }
}