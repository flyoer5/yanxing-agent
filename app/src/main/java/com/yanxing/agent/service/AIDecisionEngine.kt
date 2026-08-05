package com.yanxing.agent.service

import com.yanxing.agent.network.ChatMessageDto

/** AI 驱动的替我行动决策与动作解析。 */
object AIDecisionEngine {

    fun generateSystemPrompt(
        currentScreen: String,
        lastAction: String? = null,
        constraints: List<String> = DEFAULT_CONSTRAINTS,
    ): ChatMessageDto {
        val content = buildString {
            appendLine("你是言行 Agent 的替我行动模式，负责根据当前屏幕和用户请求规划无障碍操作。")
            appendLine("只返回 JSON，不要返回 Markdown。格式：{\"actions\":[{\"action\":\"click|long_press|swipe|input_text\",\"query\":\"元素文本\",\"direction\":\"UP|DOWN|LEFT|RIGHT\",\"text\":\"输入内容\"}]}")
            appendLine("当前屏幕：")
            appendLine(currentScreen.take(4000))
            lastAction?.let { appendLine("上一步操作：$it") }
            appendLine("约束：")
            constraints.forEachIndexed { index, rule -> appendLine("${index + 1}. $rule") }
        }
        return ChatMessageDto(role = "system", content = content)
    }

    fun parseLLMResponse(jsonText: String): ActionSequence {
        return try {
            val actions = Regex("\\{[^{}]*\\}")
                .findAll(jsonText)
                .mapNotNull { match -> parseAction(match.value) }
                .take(5)
                .toList()
            ActionSequence(actions, actions.isNotEmpty())
        } catch (error: Exception) {
            ActionSequence(emptyList(), false, error.message ?: "解析失败")
        }
    }

    private fun parseAction(item: String): Action? {
        val actionName = extractString(item, "action").ifBlank {
            when {
                item.contains("click") -> "click"
                item.contains("long_press") -> "long_press"
                item.contains("swipe") -> "swipe"
                item.contains("input_text") -> "input_text"
                else -> ""
            }
        }
        return when (actionName.lowercase()) {
            "click" -> Action.Click(extractString(item, "query"))
            "long_press" -> Action.LongPress(extractString(item, "query"))
            "swipe" -> {
                val direction = runCatching {
                    SwipeDirection.valueOf(extractString(item, "direction").uppercase())
                }.getOrNull() ?: return null
                Action.Swipe(direction)
            }
            "input_text" -> Action.InputText(
                extractString(item, "query"),
                extractString(item, "text"),
            )
            else -> null
        }
    }

    private fun extractString(json: String, key: String): String {
        val pattern = Regex("\\\"$key\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        return pattern.find(json)?.groupValues?.getOrNull(1).orEmpty()
    }

    data class ActionSequence(
        val actions: List<Action>,
        val success: Boolean,
        val error: String? = null,
        val reason: String? = null,
    )

    sealed class Action {
        data class Click(val query: String) : Action()
        data class LongPress(val query: String) : Action()
        data class Swipe(val direction: SwipeDirection) : Action()
        data class InputText(val query: String, val text: String) : Action()

        fun toDesc(): String = when (this) {
            is Click -> "点击 [$query]"
            is LongPress -> "长按 [$query]"
            is Swipe -> "滑动 ${direction.name}"
            is InputText -> "输入文本 \"$text\""
        }
    }

    enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

    private val DEFAULT_CONSTRAINTS = listOf(
        "优先选择明确可见的按钮、关闭按钮和输入框",
        "不要访问密码、支付、验证码或其他敏感信息",
        "最多返回 2 个动作",
        "找不到目标时返回空 actions",
        "输入文本前确认目标是输入框",
    )
}
