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
    fun `timestamp is formatted as yyyy-MM-dd HH:mm:ss`() {
        // 2023-11-14 22:13:20 UTC
        val formatted = formatLogTimestamp(1_700_000_000_000L)
        assertTrue(formatted.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")), "实际输出: $formatted")
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
    fun `label mapping covers rollback types`() {
        assertEquals("点击", actionTypeLabel("click"))
        assertEquals("回滚", actionTypeLabel("rollback"))
        assertEquals("返回", actionTypeLabel("back"))
        assertEquals("清空输入", actionTypeLabel("clear_text"))
        assertEquals("成功", actionStatusLabel("success"))
        assertEquals("失败", actionStatusLabel("failed"))
        assertEquals("已取消", actionStatusLabel("cancelled"))
    }
}