package com.yanxing.agent.service

import com.yanxing.agent.network.ChatMessageDto

/**
 * 替我行动 - AI 决策引擎
 * 通过构造特殊的系统消息，引导 LLM 分析界面并返回操作指令
 */
object AIDecisionEngine {

    /**
     * 生成"替我行动"的完整系统提示词
     */
    fun generateSystemPrompt(
        currentScreen: String,      // 当前屏幕文字内容
        lastAction: String? = null, // 上一步做了什么（可选）
        constraints: List<String> = DEFAULT_CONSTRAINTS,
    ): ChatMessageDto {
        val instructions = buildString {
            append("""你叫"言行 Agent-替我行动模式"，负责通过无障碍 API 控制其他 App。
你的输入是：
1. **当前屏幕内容**（从 UI 树提取的文字描述，含元素类型和文本）
2. **用户的自然语言请求**（例如："帮我把这个弹窗关掉"）
3. **可用的操作列表**（点击/长按/滑动/输入文本）

你的任务：基于屏幕内容理解当前状态，根据用户需求选择最合适的操作执行。

""")
            append("【注意事项】\n")
            instructions.forEachIndexed { i, c -> append("${i+1}. $c\n") }
            append("\n")
        }

        return ChatMessageDto(
            role = "system",
            content = instructions.trim(),
        )
    }

    /**
     * 解析 LLM 返回的 JSON，转换成可执行的 Action 列表
     */
    fun parseLLMResponse(jsonText: String): ActionSequence {
        return try {
            // 简单解析 JSON 格式（实际可用 kotlinx.serialization）
            val actions = mutableListOf<Action>()
            
            // 检查是否包含 "actions": [...] 结构
            val arrayStart = jsonText.indexOf("[")
            val arrayEnd = jsonText.lastIndexOf("]")
            if (arrayStart >= 0 && arrayEnd > arrayStart) {
                val jsonArray = jsonText.substring(arrayStart, arrayEnd + 1)
                // 简单分割动作项
                val items = jsonArray.split(",(?=\{)").map { it.trim() }
                items.forEach { item ->
                    when {
                        item.contains("\"click\"") -> {
                            val query = extractString(item, "query")
                            actions.add(Action.Click(query))
                        }
                        item.contains("\"long_press\"") -> {
                            val query = extractString(item, "query")
                            actions.add(Action.LongPress(query))
                        }
                        item.contains("\"swipe\"") -> {
                            val direction = extractString(item, "direction").let { 
                                SwipeDirection.valueOf(it.uppercase().replace("-", "_")) 
                            }
                            actions.add(Action.Swipe(direction))
                        }
                        item.contains("\"input_text\"") -> {
                            val query = extractString(item, "query")
                            val text = extractString(item, "text")
                            actions.add(Action.InputText(query, text))
                        }
                    }
                }
            }
            
            ActionSequence(actions, success = actions.isNotEmpty())
        } catch (e: Exception) {
            ActionSequence(emptyList(), false, error = e.message ?: "解析失败")
        }
    }

    // ===================== 数据结构 =====================

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
            is Click -> "点击 [${query}]"
            is LongPress -> "长按 [${query}]"
            is Swipe -> "滑动 ${direction.name}"
            is InputText -> "输入文本 \"${text}\""
        }
    }

    enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

    // ===================== 私有工具函数 =====================

    private fun extractString(objStr: String, key: String): String {
        val pattern = "\"$key\\s*:\\s*\"(.+?)\""
        val match = Regex(pattern).find(objStr)
        return match?.groups?.get(1)?.value ?: ""
    }

    companion object {
        private val DEFAULT_CONSTRAINTS = listOf(
            "优先查找明确可见的按钮、开关、关闭按钮",
            "不要尝试访问敏感信息或未经授权的数据",
            "每次最多执行 2 个动作，避免过度操作",
            "找不到目标时明确告知用户",
            "先读取屏幕再决策，避免盲目操作",
            "遇到输入框前先确认类型是否正确",
            "滑动前判断是否有更多内容可展示",
        )
    }
}
