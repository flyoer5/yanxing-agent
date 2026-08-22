package com.yanxing.agent.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionStatusLabelTest {

    @Test
    fun `全部成功记为 success`() {
        assertEquals("success", ActionStatus.Completed(3, 3).toLogStatusLabel())
    }

    @Test
    fun `部分失败记为 failed`() {
        assertEquals("failed", ActionStatus.Completed(2, 3).toLogStatusLabel())
    }

    @Test
    fun `执行中记为 running`() {
        assertEquals("running", ActionStatus.Executing(1, 3).toLogStatusLabel())
    }

    @Test
    fun `取消不再记成 unknown`() {
        assertEquals("cancelled", ActionStatus.PendingConfirm.Canceled.toLogStatusLabel())
    }

    @Test
    fun `其余状态记为 unknown`() {
        assertEquals("unknown", ActionStatus.Idle.toLogStatusLabel())
    }
}
