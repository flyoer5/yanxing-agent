package com.yanxing.agent.service

/**
 * 回滚策略控制器：根据已执行的 Action，生成逆操作建议。
 *
 * v1.0 范围：
 * - click → 全局返回 (back)
 * - input_text → 清空输入框 (clearText)
 * - swipe → 反方向滑动 (反向 Swipe)
 * - long_press → 无法自动撤销（返回 null）
 */
object RollbackController {

    /**
     * 根据动作生成逆操作建议
     * @param action 已执行的原始动作
     * @param currentPackage 当前应用包名（用于提示上下文）
     * @return Suggestion 或 null（无合适回滚策略）
     */
    fun suggestRollback(action: AIDecisionEngine.Action, currentPackage: String): Suggestion? = runCatching {
        val suggestion = when (action) {
            is AIDecisionEngine.Action.Click -> Suggestion(
                description = "返回上一页",
                actions = listOf(AIDecisionEngine.Action.Back),
                confidence = 0.85f,
                warning = null,
            )

            is AIDecisionEngine.Action.LongPress -> Suggestion(
                description = "长按效果复杂，建议手动恢复",
                actions = emptyList(),
                confidence = 0.2f,
                warning = "长按可能触发菜单/弹出窗口，无法简单逆转",
            )

            is AIDecisionEngine.Action.Swipe -> Suggestion(
                description = "反向滑动 ${oppositeDirection(action.direction).name}",
                actions = listOf(AIDecisionEngine.Action.Swipe(oppositeDirection(action.direction))),
                confidence = 0.9f,
                warning = null,
            )

            is AIDecisionEngine.Action.InputText -> Suggestion(
                description = "清空输入框 \"${action.text.take(30)}${if (action.text.length > 30) "..." else ""}\"",
                actions = listOf(AIDecisionEngine.Action.ClearText(action.query)),
                confidence = 0.95f,
                warning = null,
            )

            is AIDecisionEngine.Action.Back -> Suggestion(
                description = "返回动作无法自动撤销",
                actions = emptyList(),
                confidence = 0.2f,
                warning = "返回已改变页面栈，无法简单逆转，建议手动恢复",
            )

            is AIDecisionEngine.Action.ClearText -> Suggestion(
                description = "需要重新输入原文本",
                actions = emptyList(),
                confidence = 0.2f,
                warning = "原文本未保存，无法自动恢复输入内容，建议手动重输",
            )
        }
        suggestion ?: null
    }.getOrNull()

    /** 获取相反方向的滑动 */
    private fun oppositeDirection(direction: AIDecisionEngine.SwipeDirection): AIDecisionEngine.SwipeDirection =
        when (direction) {
            AIDecisionEngine.SwipeDirection.UP -> AIDecisionEngine.SwipeDirection.DOWN
            AIDecisionEngine.SwipeDirection.DOWN -> AIDecisionEngine.SwipeDirection.UP
            AIDecisionEngine.SwipeDirection.LEFT -> AIDecisionEngine.SwipeDirection.RIGHT
            AIDecisionEngine.SwipeDirection.RIGHT -> AIDecisionEngine.SwipeDirection.LEFT
        }

    data class Suggestion(
        val description: String,          // 用户可读的说明
        val actions: List<AIDecisionEngine.Action>,  // 逆操作序列（可能是多步）
        val confidence: Float,             // 置信度 (0-1)
        val warning: String? = null,       // 额外提示
    )
}