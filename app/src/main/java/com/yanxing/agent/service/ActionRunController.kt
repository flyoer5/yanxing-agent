package com.yanxing.agent.service

/**
 * 替我行动的运行控制器：集中管理"是否被用户停止"和"当前决策轮次"。
 *
 * 抽出成独立类的原因：
 * 1. 停止判定需要在多个协程分支里复用（动作执行、继续决策、确认回调）；
 * 2. 这部分逻辑不依赖 Android 框架，可以直接做单元测试。
 */
class ActionRunController(private val maxRounds: Int = DEFAULT_MAX_ROUNDS) {

    @Volatile
    private var cancelled: Boolean = false

    /** 当前决策轮次，0 表示没有进行中的任务 */
    var round: Int = 0
        private set

    /** 用户是否已请求停止 */
    val isCancelled: Boolean get() = cancelled

    /** 是否有正在进行的任务 */
    val isRunning: Boolean get() = round > 0 && !cancelled

    /** 开始一个新任务：清除停止标记并回到第一轮 */
    fun start() {
        cancelled = false
        round = 1
    }

    /** 用户请求停止。返回 false 表示当前没有可停止的任务 */
    fun cancel(): Boolean {
        if (round == 0 || cancelled) return false
        cancelled = true
        return true
    }

    /** 是否还能进入下一轮决策：未被停止且未达轮次上限 */
    fun canContinue(): Boolean = !cancelled && round in 1 until maxRounds

    /** 进入下一轮，返回新的轮次号 */
    fun nextRound(): Int {
        if (cancelled) return round
        round++
        return round
    }

    /** 任务结束后清理轮次。停止标记保留到下一次 start()，避免在途协程继续执行 */
    fun reset() {
        round = 0
    }

    companion object {
        /** 多轮决策上限，防止死循环 */
        const val DEFAULT_MAX_ROUNDS = 5
    }
}
