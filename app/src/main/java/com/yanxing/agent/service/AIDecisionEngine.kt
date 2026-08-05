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
            appendLine("""你是言行 Agent 的替我行动模式，负责根据当前屏幕和用户请求规划无障碍操作。
【核心目标】
1. 只返回 JSON，不要 Markdown
2. 生成的 query 必须是屏幕上确实存在的文本或描述
3. 对于模糊描述，尝试找出最接近的唯一元素""")

            appendLine("\n输出格式示例:")
            appendLine("""{"actions":[{"action":"click","query":"确切的按钮文本"},{"action":"swipe","direction":"DOWN"}]}""")

            appendLine("\n当前屏幕内容:")
            appendLine(currentScreen.take(4000))

            lastAction?.let { appendLine("\n上一步操作：$it") }

            appendLine("\n约束规则:")
            constraints.forEachIndexed { index, rule -> appendLine("${index + 1}. $rule") }

            appendLine("\n重要提示:")
            appendLine("✓ 优先使用屏幕上实际显示的文本")
            appendLine("✓ 如果找不到完全匹配的，使用部分匹配或语义相似的描述")
            appendLine("✗ 不要凭空想象不存在的元素")
        }

        return ChatMessageDto(role = "system", content = content)
    }

    fun parseLLMResponse(jsonText: String): ActionSequence {
        return try {
            val done = extractBool(jsonText, "done")
            val actions = Regex("\\{[^{}]*\\}")
                .findAll(jsonText)
                .mapNotNull { match -> parseAction(match.value) }
                .take(5)
                .toList()
            ActionSequence(actions, actions.isNotEmpty() || done, done = done)
        } catch (error: Exception) {
            ActionSequence(emptyList(), false, error.message ?: "解析失败")
        }
    }

    /**
     * 生成"继续决策"提示词：执行完一组动作后，根据新屏幕判断任务是否完成或继续操作。
     */
    fun generateContinuationPrompt(
        goal: String,
        currentScreen: String,
        lastResult: String? = null,
        round: Int = 1,
        maxRounds: Int = 5,
    ): ChatMessageDto {
        val content = buildString {
            appendLine("""你是言行 Agent 的替我行动模式，正在执行用户任务。这是第 $round 轮决策（上限 $maxRounds 轮）。
【输出格式】只返回 JSON，不要 Markdown：
- 任务已完成时：{"done":true,"reason":"完成原因"}
- 需要继续操作时：{"done":false,"actions":[{"action":"click","query":"确切的按钮文本"}]}
【规则】
1. 根据当前屏幕内容判断任务目标是否已达成
2. 已达成必须返回 done:true，禁止重复执行已成功的动作
3. 未达成时最多返回 2 个下一步动作
4. query 必须是当前屏幕上真实存在的文本或描述
5. 屏幕内容无法判断进展时返回 done:true 并说明原因，避免死循环""")
            appendLine("\n任务目标：$goal")
            lastResult?.let { appendLine("\n上一轮执行结果：\n$it") }
            appendLine("\n当前屏幕内容：")
            appendLine(currentScreen.take(4000))
        }
        return ChatMessageDto(role = "system", content = content)
    }

    private fun parseAction(item: String): Action? {
        val actionName = extractString(item, "action").ifBlank {
            when {
                item.contains("click") -> "click"
                item.contains("long_press") -> "long_press"
                item.contains("swipe") -> "swipe"
                item.contains("input_text") -> "input_text"
                item.contains("back") || item.contains("return") -> "back"
                item.contains("clear_text") -> "clear_text"
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
            "back" -> Action.Back
            "clear_text" -> Action.ClearText(extractString(item, "query"))
            else -> null
        }
    }

    private fun extractString(json: String, key: String): String {
        val pattern = Regex("\\\"$key\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        return pattern.find(json)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun extractBool(json: String, key: String): Boolean {
        val pattern = Regex("\\\"$key\\\"\\s*:\\s*(true|false)")
        return pattern.find(json)?.groupValues?.getOrNull(1) == "true"
    }

    data class ActionSequence(
        val actions: List<Action>,
        val success: Boolean,
        val error: String? = null,
        val reason: String? = null,
        val done: Boolean = false, // LLM 是否声明任务已完成
    )

    sealed class Action {
        data class Click(val query: String) : Action()
        data class LongPress(val query: String) : Action()
        data class Swipe(val direction: SwipeDirection) : Action()
        data class InputText(val query: String, val text: String) : Action()
        object Back : Action()                // 全局返回（回滚专用）
        data class ClearText(val query: String) : Action()  // 清空输入框（回滚专用）

        fun toDesc(): String = when (this) {
            is Click -> "点击 [$query]"
            is LongPress -> "长按 [$query]"
            is Swipe -> "滑动 ${direction.name}"
            is InputText -> "输入文本 \"$text\""
            is Back -> "执行返回"
            is ClearText -> "清空 \"${query}\""
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
