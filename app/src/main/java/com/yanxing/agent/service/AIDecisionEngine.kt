package com.yanxing.agent.service

import com.yanxing.agent.network.ChatMessageDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

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

    /** 解析 LLM 回复：优先按标准 JSON 解析（正确处理转义与嵌套），失败再降级正则 */
    fun parseLLMResponse(jsonText: String): ActionSequence {
        return try {
            val cleaned = stripMarkdownFences(jsonText)
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start < 0 || end <= start) {
                return ActionSequence(emptyList(), false, "回复中未找到 JSON 内容")
            }
            val root = Json.parseToJsonElement(cleaned.substring(start, end + 1)) as? JsonObject
                ?: return ActionSequence(emptyList(), false, "JSON 结构异常")
            val done = (root["done"] as? JsonPrimitive)?.booleanOrNull ?: false
            val reason = (root["reason"] as? JsonPrimitive)?.contentOrNull
            val actions = (root["actions"] as? JsonArray)
                ?.mapNotNull(::parseActionElement)
                ?.take(5)
                .orEmpty()
            ActionSequence(actions, actions.isNotEmpty() || done, done = done, reason = reason)
        } catch (error: Exception) {
            // 降级：兼容 LLM 在 JSON 前后夹杂说明文字的情况
            return try {
                val actions = Regex("\\{[^{}]*\\}")
                    .findAll(jsonText)
                    .mapNotNull { match -> parseActionElement(Json.parseToJsonElement(match.value)) }
                    .take(5)
                    .toList()
                ActionSequence(actions, actions.isNotEmpty(), done = false)
            } catch (fallbackError: Exception) {
                ActionSequence(emptyList(), false, error.message ?: "解析失败")
            }
        }
    }

    /** 剥掉 ```json ... ``` 围栏与零散反引号 */
    private fun stripMarkdownFences(text: String): String =
        text.replace("```json", "").replace("```", "").replace("`", "").trim()

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
            lastResult?.let { appendLine("\n上一轮执行结果：\n${it.take(4000)}") }
            appendLine("\n当前屏幕内容：")
            appendLine(currentScreen.take(4000))
        }
        return ChatMessageDto(role = "system", content = content)
    }

    private fun parseActionElement(element: kotlinx.serialization.json.JsonElement): Action? {
        val obj = element as? JsonObject ?: return null
        val raw = obj.toString()
        val actionName = (obj["action"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank {
            when {
                raw.contains("click") -> "click"
                raw.contains("long_press") -> "long_press"
                raw.contains("swipe") -> "swipe"
                raw.contains("input_text") -> "input_text"
                raw.contains("back") || raw.contains("return") -> "back"
                raw.contains("clear_text") -> "clear_text"
                else -> ""
            }
        }
        fun str(key: String): String = (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return when (actionName.lowercase()) {
            "click" -> Action.Click(str("query"))
            "long_press" -> Action.LongPress(str("query"))
            "swipe" -> {
                val direction = runCatching {
                    SwipeDirection.valueOf(str("direction").uppercase())
                }.getOrNull() ?: return null
                Action.Swipe(direction)
            }
            "input_text" -> Action.InputText(
                str("query"),
                str("text"),
            )
            "back" -> Action.Back
            "clear_text" -> Action.ClearText(str("query"))
            else -> null
        }
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
