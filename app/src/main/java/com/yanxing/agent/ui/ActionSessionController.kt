package com.yanxing.agent.ui

import android.content.Context
import com.yanxing.agent.data.ActionStatus
import com.yanxing.agent.data.ChatRepository
import com.yanxing.agent.network.ChatCompletionRequest
import com.yanxing.agent.network.ChatMessageDto
import com.yanxing.agent.network.LlmClient
import com.yanxing.agent.service.AIDecisionEngine
import com.yanxing.agent.service.ActionExecutor
import com.yanxing.agent.service.ActionRunController
import com.yanxing.agent.service.BatchedLogWriter
import com.yanxing.agent.service.FloatingProgressOverlay
import com.yanxing.agent.service.RollbackController
import com.yanxing.agent.service.ScreenReaderAccessibilityService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 替我行动的会话编排器（从 ChatViewModel 拆出）：
 * 负责读屏 → LLM 决策 → 确认 → 执行 → 多轮续判的完整链路，以及撤销与悬浮进度窗。
 *
 * 依赖通过构造注入，UI 状态经共享的 [uiState]（MutableStateFlow）回写，
 * 会话 id 经 [currentConversationId] 读取——行动结果总是写入发起任务时的会话。
 */
class ActionSessionController(
    private val scope: CoroutineScope,
    context: Context,
    private val repository: ChatRepository,
    private val llmClient: LlmClient,
    private val logWriter: BatchedLogWriter,
    private val uiState: MutableStateFlow<ChatUiState>,
    private val currentConversationId: StateFlow<String>,
) {
    private val appContext = context.applicationContext

    private var actionGoal: String = ""        // 当前任务目标
    private val actionRunner = ActionRunController(MAX_ACTION_ROUNDS) // 轮次与停止控制

    /** 当前行动任务的代际号，旧任务在途协程据此退出，避免写错状态/轮次 */
    @Volatile private var actionGeneration: Long = 0L
    private val actionHistory = StringBuilder() // 已执行动作摘要
    private val executedActions = mutableListOf<AIDecisionEngine.Action>() // 已执行的原始动作（用于回滚）

    // ===== 悬浮窗进度显示 =====
    private var progressOverlay: FloatingProgressOverlay? = null

    /** 进入行动模式：读屏并置为 Ready */
    fun startMode() {
        if (!ScreenReaderAccessibilityService.isConnected) {
            uiState.update { it.copy(error = "请先在设置中开启无障碍服务") }
            return
        }
        uiState.update { it.copy(actionStatus = ActionStatus.Readying, lastScreenPackage = "") }

        scope.launch {
            val screenText = extractScreenText()
            uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Ready(screenText),
                    lastScreenPackage = ScreenReaderAccessibilityService.lastScreenPackage,
                    draft = "",
                )
            }
        }
    }

    private suspend fun extractScreenText(): String {
        val root = ScreenReaderAccessibilityService.instance?.rootInActiveWindow
            ?: return "无法读取当前界面"
        val pkg = ScreenReaderAccessibilityService.lastScreenPackage
        val text = runCatching {
            ActionExecutor.extractText(root).take(1000)
        }.getOrElse { "读取失败：$it" }
        // 屏幕无可见文本时明确告知 AI，避免基于空屏乱猜动作
        return if (text.isBlank()) {
            "当前界面：$pkg\n内容:\n（当前屏幕无可见文本，若需要执行动作请先与用户确认目标）"
        } else {
            "当前界面：$pkg\n内容:\n$text"
        }
    }

    /** 行动模式入口：把用户消息作为任务，读屏后让 AI 规划动作（第一轮决策） */
    fun startTask(goal: String) {
        if (!ScreenReaderAccessibilityService.isConnected) {
            // 保留用户输入，仅提示错误
            uiState.update { it.copy(error = "请先在设置中开启无障碍服务") }
            return
        }
        actionGoal = goal
        actionGeneration = actionRunner.start()
        actionHistory.clear()
        uiState.update { it.copy(draft = "", isSending = true, error = null, pendingAttachments = emptyList(), actionGoal = goal) }

        scope.launch {
            val conversationId = currentConversationId.value
            repository.appendMessage(conversationId, "user", goal)
            val screenText = extractScreenText()
            val current = uiState.value
            val systemPrompt = AIDecisionEngine.generateSystemPrompt(screenText, lastAction = null)
            val request = ChatCompletionRequest(
                model = current.model,
                messages = listOf(
                    systemPrompt,
                    ChatMessageDto.text("user", "任务目标：$goal"),
                ),
                stream = false,
            )
            val result = llmClient.complete(current.baseUrl, current.apiKey, request)
            uiState.update { it.copy(isSending = false) }
            // 请求期间用户可能已停止、或已开始新任务（代际变化），此时不再进入确认流程
            if (actionRunner.isCancelled || actionRunner.isStale(actionGeneration)) return@launch
            result.onSuccess { reply ->
                val sequence = AIDecisionEngine.parseLLMResponse(reply)
                if (sequence.actions.isEmpty()) {
                    val message = sequence.error ?: "AI 未规划出可执行的操作"
                    uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Completed(0, 0),
                            error = if (sequence.error != null) message else null,
                        )
                    }
                    repository.appendMessage(conversationId, "assistant", "任务完成：无需执行操作。$message")
                    resetActionContext()
                } else {
                    uiState.update {
                        it.copy(actionStatus = ActionStatus.PendingConfirm.Waiting(sequence.actions, 0))
                    }
                }
            }.onFailure { error ->
                uiState.update {
                    it.copy(
                        actionStatus = ActionStatus.Completed(0, 0),
                        error = "行动决策失败：${error.message ?: "未知错误"}",
                    )
                }
                repository.appendMessage(conversationId, "assistant", "行动决策失败：${error.message ?: "未知错误"}")
                resetActionContext()
            }
        }
    }

    /** 供外部直接注入动作序列（悬浮窗等入口预留） */
    fun executeAction(prompt: String, actions: List<AIDecisionEngine.Action>) {
        if (!ScreenReaderAccessibilityService.isConnected) {
            uiState.update { it.copy(error = "无障碍服务未开启") }
            return
        }
        actionGoal = prompt.ifBlank { actionGoal }
        actionGeneration = actionRunner.start()
        actionHistory.clear()

        if (actions.isEmpty()) {
            uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Completed(0, 0),
                    error = "没有检测到可执行的操作",
                )
            }
            progressOverlay?.hide()
            progressOverlay = null
            resetActionContext()
        } else {
            uiState.update {
                it.copy(actionStatus = ActionStatus.PendingConfirm.Waiting(actions, 0))
            }
        }
    }

    /**
     * 用户请求停止执行（来自悬浮窗停止按钮或界面按钮）。
     * 已在途的动作无法从系统层面撤回，这里保证不再发起新的动作和新的决策轮。
     */
    fun stopAction() {
        if (!actionRunner.cancel()) return
        progressOverlay?.markStopped()
        val executed = actionHistory.toString().trim()
        val summary = if (executed.isEmpty()) {
            "已停止执行，没有动作被执行。"
        } else {
            "已停止执行。已完成的动作：\n$executed"
        }
        uiState.update { it.copy(actionStatus = ActionStatus.PendingConfirm.Canceled) }
        val conversationId = currentConversationId.value
        scope.launch {
            logWriter.addLog(
                packageName = uiState.value.lastScreenPackage,
                actionType = "stop",
                targetElement = null,
                details = summary,
                status = ActionStatus.PendingConfirm.Canceled,
                errorMessage = null,
            )
            logWriter.forceFlush()
            repository.appendMessage(conversationId, "assistant", summary)
        }
        progressOverlay?.hide()
        progressOverlay = null
        actionRunner.reset()
        actionGoal = ""
        uiState.update { it.copy(actionGoal = "") }
        actionHistory.clear()
        executedActions.clear()
    }

    /**
     * 撤销上一个成功执行的行动。
     * 从栈中弹出最近动作 → RollbackController 生成逆操作 → 确认执行 → 记 rollback 日志
     */
    fun undoLastAction() {
        val lastAction = executedActions.removeLastOrNull() ?: run {
            progressOverlay?.toast("没有可撤销的动作")
            uiState.update { it.copy(error = "没有可撤销的操作记录") }
            return
        }

        val suggestion = RollbackController.suggestRollback(lastAction, uiState.value.lastScreenPackage.orEmpty())
        val conversationId = currentConversationId.value

        // 无法自动逆转：展示手动引导步骤，不静默丢弃
        if (suggestion == null || suggestion.actions.isEmpty()) {
            val guidance = buildString {
                append("「${lastAction.toDesc()}」无法自动撤销。\n")
                suggestion?.warning?.let { append("⚠️ $it\n") }
                if (suggestion?.manualSteps?.isNotEmpty() == true) {
                    append("手动恢复步骤：\n")
                    suggestion.manualSteps.forEachIndexed { i, step -> append("${i + 1}. $step\n") }
                }
                append("\n该动作已从撤销队列中移除，请按引导手动处理。")
            }
            scope.launch {
                repository.appendMessage(conversationId, "assistant", guidance)
            }
            uiState.update { it.copy(error = null, actionStatus = ActionStatus.Idle) }
            progressOverlay?.setUndoButton(executedActions.isNotEmpty())
            progressOverlay?.toast("无法自动撤销，已给出手动引导")
            return
        }

        // 展示撤销说明
        val undoSummary = "撤销 ${lastAction.toDesc()}：\n${suggestion.description}"
        uiState.update { it.copy(actionStatus = ActionStatus.Thinking(actionRunner.round)) }

        scope.launch {
            logWriter.addLog(
                packageName = uiState.value.lastScreenPackage.orEmpty(),
                actionType = "rollback",
                targetElement = when (lastAction) {
                    is AIDecisionEngine.Action.Click -> lastAction.query
                    is AIDecisionEngine.Action.InputText -> lastAction.query
                    is AIDecisionEngine.Action.ClearText -> lastAction.query
                    else -> null
                },
                details = undoSummary,
                status = ActionStatus.Completed(suggestion.actions.size, suggestion.actions.size),
                errorMessage = null,
            )
            repository.appendMessage(conversationId, "assistant", undoSummary)

            // 顺序执行逆操作
            var successCount = 0
            for (undoAction in suggestion.actions) {
                val result = withContext(Dispatchers.Default) {
                    when (undoAction) {
                        is AIDecisionEngine.Action.Click -> ActionExecutor.click(undoAction.query)
                        is AIDecisionEngine.Action.LongPress -> ActionExecutor.longPress(undoAction.query)
                        is AIDecisionEngine.Action.Swipe -> ActionExecutor.swipe(undoAction.direction)
                        is AIDecisionEngine.Action.InputText -> ActionExecutor.inputText(undoAction.query, undoAction.text)
                        is AIDecisionEngine.Action.Back -> ActionExecutor.back()
                        is AIDecisionEngine.Action.ClearText -> ActionExecutor.clearText(undoAction.query)
                    }
                }
                if (result.success) successCount++

                // 每次操作都写一条 rollback 日志
                logWriter.addLog(
                    packageName = uiState.value.lastScreenPackage.orEmpty(),
                    actionType = "rollback",
                    targetElement = when (undoAction) {
                        is AIDecisionEngine.Action.Back -> null
                        is AIDecisionEngine.Action.ClearText -> undoAction.query
                        else -> null
                    },
                    details = "${undoAction.toDesc()}：${result.message}",
                    status = if (result.success) ActionStatus.Completed(1, 1) else ActionStatus.Idle,
                    errorMessage = if (!result.success) result.message else null,
                )
            }

            val finalSummary = "撤销完成：$successCount/${suggestion.actions.size} 步成功。\n原操作：${lastAction.toDesc()}\n逆操作：${suggestion.description}"
            repository.appendMessage(conversationId, "assistant", finalSummary)
            progressOverlay?.toast("撤销完成")
            progressOverlay?.setUndoButton(executedActions.isNotEmpty())
        }
    }

    /** 用户点击确认按钮，批准当前动作 */
    fun confirmCurrentAction(approved: Boolean) {
        if (actionRunner.isCancelled) return
        val current = uiState.value.actionStatus

        if (current !is ActionStatus.PendingConfirm.Waiting) return

        val actions = current.actions
        val index = current.index

        if (approved) {
            // 切换到执行状态
            uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Executing(
                        index + 1,
                        actions.size,
                        actions[index].toDesc(),
                        confirmed = true,
                        userApproved = true
                    )
                )
            }

            // 初始化悬浮窗进度：每组动作从头计数，避免跨轮累加导致 "3 / 2"
            ensureProgressOverlay()
            if (index == 0) progressOverlay?.resetProgress()
            progressOverlay?.setTotalActions(actions.size)
            progressOverlay?.setCurrentAction(actions[index].toDesc())
            progressOverlay?.show()

            // 开始执行这个动作
            executePendingAction(actions, index + 1)
        } else {
            // 拒绝动作，跳过并询问下一个
            uiState.update {
                it.copy(actionStatus = ActionStatus.PendingConfirm.Waiting(actions, index))
            }
            executePendingActionSkipping(actions, index + 1)
        }
    }

    private fun executePendingAction(actions: List<AIDecisionEngine.Action>, nextIndex: Int) {
        scope.launch {
            // 边界保护：nextIndex-1 必须是有效动作索引
            if (nextIndex <= 0 || nextIndex > actions.size) return@launch
            // 用户已停止或任务已换代：不再触碰系统 UI
            if (actionRunner.isCancelled || actionRunner.isStale(actionGeneration)) return@launch

            val action = actions[nextIndex - 1]

            // 在 Default dispatcher 上执行无障碍操作（避免阻塞主线程）
            val result = withContext(Dispatchers.Default) {
                when (action) {
                    is AIDecisionEngine.Action.Click -> ActionExecutor.click(action.query)
                    is AIDecisionEngine.Action.LongPress -> ActionExecutor.longPress(action.query)
                    is AIDecisionEngine.Action.Swipe -> ActionExecutor.swipe(action.direction)
                    is AIDecisionEngine.Action.InputText -> ActionExecutor.inputText(action.query, action.text)
                    is AIDecisionEngine.Action.Back -> ActionExecutor.back()
                    is AIDecisionEngine.Action.ClearText -> ActionExecutor.clearText(action.query)
                }
            }

            // 记录执行历史（供下一轮决策参考）
            actionHistory.appendLine("${action.toDesc()} → ${if (result.success) "成功" else "失败"}：${result.message}")

            // 更新悬浮窗状态
            progressOverlay?.setActionModeEnabled(true)
            if (result.success) {
                progressOverlay?.incrementSuccess()
                // 记录到回滚栈
                executedActions.add(action)
                progressOverlay?.setUndoButton(executedActions.isNotEmpty())
            } else {
                progressOverlay?.incrementFailed()
            }
            // 悬浮窗内实时展示本次执行结果
            progressOverlay?.showResult(result.message, result.success)

            // 批量写入操作日志（性能优化）
            scope.launch {
                val packageName = uiState.value.lastScreenPackage.orEmpty()
                logWriter.addLog(
                    packageName = packageName,
                    actionType = when (action) {
                        is AIDecisionEngine.Action.Click -> "click"
                        is AIDecisionEngine.Action.LongPress -> "long_press"
                        is AIDecisionEngine.Action.Swipe -> "swipe"
                        is AIDecisionEngine.Action.InputText -> "input_text"
                        is AIDecisionEngine.Action.Back -> "back"
                        is AIDecisionEngine.Action.ClearText -> "clear_text"
                    },
                    targetElement = when (action) {
                        is AIDecisionEngine.Action.Click -> action.query
                        is AIDecisionEngine.Action.LongPress -> action.query
                        is AIDecisionEngine.Action.InputText -> action.query
                        is AIDecisionEngine.Action.ClearText -> action.query
                        else -> null
                    },
                    details = when (action) {
                        is AIDecisionEngine.Action.Click, is AIDecisionEngine.Action.LongPress,
                        is AIDecisionEngine.Action.Swipe -> result.message
                        is AIDecisionEngine.Action.InputText -> action.text
                        is AIDecisionEngine.Action.Back -> "执行返回动作"
                        is AIDecisionEngine.Action.ClearText -> "清空输入框"
                    }.orEmpty(),
                    status = if (result.success) ActionStatus.Completed(1, 1) else ActionStatus.Idle,
                    errorMessage = if (!result.success) result.message else null,
                )
            }

            // 动作执行期间用户点了停止或任务已换代：保留已写入的日志，不再推进
            if (actionRunner.isCancelled || actionRunner.isStale(actionGeneration)) return@launch

            if (result.success && nextIndex < actions.size) {
                // 继续下一个动作
                uiState.update {
                    it.copy(
                        actionStatus = ActionStatus.PendingConfirm.Waiting(actions, nextIndex)
                    )
                }
            } else if (result.success) {
                // 本组动作全部执行完成 → 多轮决策：回传结果让 AI 根据新屏幕续判
                continueDecision()
            } else if (nextIndex < actions.size) {
                // 失败后询问下一个动作；停留在原地（nextIndex-1）会反复重试同一个必失败动作
                uiState.update {
                    it.copy(
                        actionStatus = ActionStatus.PendingConfirm.Waiting(actions, nextIndex),
                        error = "操作${action.toDesc()}失败，已跳过该操作"
                    )
                }
            } else {
                // 本动作是最后一个且失败 → 直接交由 AI 复判下一步
                continueDecision()
            }
        }
    }

    private fun executePendingActionSkipping(actions: List<AIDecisionEngine.Action>, skippedIndex: Int) {
        if (skippedIndex >= actions.size) {
            // 全部跳过，任务结束
            finishAction("用户跳过了所有动作，任务结束")
            return
        }

        uiState.update {
            it.copy(
                actionStatus = ActionStatus.PendingConfirm.Waiting(actions, skippedIndex)
            )
        }
    }

    /**
     * 多轮决策：一组动作执行完后，读取新屏幕并回传 LLM，
     * 由 AI 判断任务完成（done）或继续规划下一组动作。
     */
    private fun continueDecision() {
        if (actionRunner.isCancelled || actionRunner.isStale(actionGeneration)) return
        if (!actionRunner.canContinue()) {
            finishAction("已达最大决策轮次（$MAX_ACTION_ROUNDS），任务停止", isError = true)
            return
        }
        val goal = actionGoal
        uiState.update { it.copy(actionStatus = ActionStatus.Thinking(actionRunner.round)) }

        scope.launch {
            val screenText = extractScreenText()
            if (actionRunner.isCancelled || actionRunner.isStale(actionGeneration)) return@launch
            val current = uiState.value
            val systemPrompt = AIDecisionEngine.generateContinuationPrompt(
                goal = goal,
                currentScreen = screenText,
                lastResult = actionHistory.toString().ifBlank { null },
                round = actionRunner.round,
                maxRounds = MAX_ACTION_ROUNDS,
            )
            val request = ChatCompletionRequest(
                model = current.model,
                messages = listOf(systemPrompt),
                stream = false,
            )
            val result = llmClient.complete(current.baseUrl, current.apiKey, request)
            if (actionRunner.isCancelled || actionRunner.isStale(actionGeneration)) return@launch
            result.onSuccess { reply ->
                val sequence = AIDecisionEngine.parseLLMResponse(reply)
                when {
                    sequence.done -> finishAction(sequence.reason ?: "任务完成")
                    sequence.actions.isEmpty() -> finishAction(sequence.error ?: "任务完成")
                    else -> {
                        actionRunner.nextRound()
                        uiState.update {
                            it.copy(actionStatus = ActionStatus.PendingConfirm.Waiting(sequence.actions, 0))
                        }
                    }
                }
            }.onFailure { error ->
                finishAction("继续决策失败：${error.message ?: "未知错误"}", isError = true)
            }
        }
    }

    /** 结束行动任务：写入总结消息并清理上下文 */
    private fun finishAction(message: String, isError: Boolean = false) {
        val conversationId = currentConversationId.value
        uiState.update {
            it.copy(
                actionStatus = ActionStatus.Completed(0, 0),
                error = if (isError) message else null,
            )
        }
        scope.launch {
            repository.appendMessage(conversationId, "assistant", message)
        }
        progressOverlay?.hide()
        progressOverlay = null
        resetActionContext()
    }

    private fun resetActionContext() {
        actionGoal = ""
        uiState.update { it.copy(actionGoal = "") }
        actionRunner.reset()
        actionHistory.clear()
    }

    /** 确保悬浮窗已初始化 */
    private fun ensureProgressOverlay() {
        if (progressOverlay == null) {
            progressOverlay = FloatingProgressOverlay(appContext).apply {
                onStopRequested = { stopAction() }
                onUndoRequested = { undoLastAction() }
            }
        }
    }

    /** ViewModel 销毁时调用：移除悬浮窗 */
    fun destroy() {
        progressOverlay?.hide()
        progressOverlay = null
    }

    private companion object {
        const val MAX_ACTION_ROUNDS = 5 // 多轮决策上限，防止死循环
    }
}
