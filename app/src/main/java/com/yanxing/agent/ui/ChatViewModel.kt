package com.yanxing.agent.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yanxing.agent.data.ActionLogEntity
import com.yanxing.agent.data.ActionStatus
import com.yanxing.agent.data.Attachment
import com.yanxing.agent.data.ChatMessage
import com.yanxing.agent.data.ChatRepository
import com.yanxing.agent.data.Conversation
import com.yanxing.agent.data.ConversationGroup
import com.yanxing.agent.data.Memory
import com.yanxing.agent.data.ModelSettingsStore
import com.yanxing.agent.network.ChatCompletionRequest
import com.yanxing.agent.network.ChatMessageDto
import com.yanxing.agent.network.LlmClient
import com.yanxing.agent.network.SearchResult
import com.yanxing.agent.network.WebSearchClient
import com.yanxing.agent.service.AIDecisionEngine
import com.yanxing.agent.service.ActionRunController
import com.yanxing.agent.service.BatchedLogWriter
import com.yanxing.agent.service.FloatingProgressOverlay
import com.yanxing.agent.service.FloatingWindowService
import com.yanxing.agent.service.RootShell
import com.yanxing.agent.service.RollbackController
import com.yanxing.agent.service.ScreenReaderAccessibilityService
import com.yanxing.agent.service.VoiceInputController
import com.yanxing.agent.service.ActionExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val _context: Context,
    private val repository: ChatRepository,
    private val settings: ModelSettingsStore,
    private val llmClient: LlmClient,
    private val webSearchClient: WebSearchClient,
) : ViewModel() {

    // ===== 状态管理 =====
    private val currentConversationId = MutableStateFlow("")
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // ===== 批量日志写入器 =====
    private val batchedLogWriter = BatchedLogWriter(repository)

    // ===== 悬浮窗进度显示 =====
    private var progressOverlay: FloatingProgressOverlay? = null

    // ===== 语音输入 =====
    private val voiceInput = VoiceInputController(_context)
    private val rootShell = RootShell


    // ===== 多轮行动决策上下文 =====
    private var actionGoal: String = ""        // 当前任务目标
    private val actionRunner = ActionRunController(MAX_ACTION_ROUNDS) // 轮次与停止控制
    private val actionHistory = StringBuilder() // 已执行动作摘要
    private val executedActions = mutableListOf<AIDecisionEngine.Action>() // 已执行的原始动作（用于回滚）

    private companion object {
        const val MAX_ACTION_ROUNDS = 5 // 多轮决策上限，防止死循环
    }

    init {
        viewModelScope.launch {
            val conversations = repository.conversationsSnapshot()
            val initialId = conversations.firstOrNull()?.id ?: repository.createConversation()
            currentConversationId.value = initialId
            currentConversationId.flatMapLatest(repository::observeMessages).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            repository.observeConversations().collect { updated ->
                _uiState.update { state ->
                    state.copy(
                        conversations = updated,
                        selectedConversationId = currentConversationId.value,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeGroups().collect { groups -> _uiState.update { it.copy(groups = groups) } }
        }
        viewModelScope.launch {
            repository.observeMemories().collect { memories -> _uiState.update { it.copy(memories = memories) } }
        }
        viewModelScope.launch {
            repository.observeActionLogs().collect { logs -> _uiState.update { it.copy(actionLogs = logs) } }
        }
        loadSettings()
    }

    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrl = value) }
    fun updateApiKey(value: String) = _uiState.update { it.copy(apiKey = value) }
    fun updateModel(value: String) = _uiState.update { it.copy(model = value) }
    fun updateDraft(value: String) = _uiState.update { it.copy(draft = value) }
    fun updateSearchApiKey(value: String) = _uiState.update { it.copy(searchApiKey = value) }

    // ===== 附件管理 =====

    fun addAttachment(attachment: Attachment) {
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + attachment) }
    }

    fun removeAttachment(index: Int) {
        _uiState.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.toMutableList().apply {
                if (index in indices) removeAt(index)
            })
        }
    }

    fun clearAttachments() {
        _uiState.update { it.copy(pendingAttachments = emptyList()) }
    }

    // ===== 语音输入 =====

    /** 开始语音识别，识别结果追加到当前草稿 */
    fun startVoiceInput() {
        if (uiState.value.voiceInputMode) return
        voiceInput.start(
            onResult = { text ->
                _uiState.update { state ->
                    val draft = if (state.draft.isBlank()) text else "${state.draft} $text"
                    state.copy(draft = draft, voiceInputMode = false)
                }
            },
            onError = { message ->
                _uiState.update { it.copy(voiceInputMode = false, error = message) }
            },
            onStateChanged = { listening ->
                _uiState.update { it.copy(voiceInputMode = listening) }
            },
        )
    }

    /** 取消进行中的语音识别 */
    fun cancelVoiceInput() {
        voiceInput.cancel()
        _uiState.update { it.copy(voiceInputMode = false) }
    }

    fun saveSettings() {
        settings.baseUrl = uiState.value.baseUrl
        settings.model = uiState.value.model
        settings.saveApiKey(uiState.value.apiKey)
        settings.saveSearchApiKey(uiState.value.searchApiKey)
        settings.searchEnabled = uiState.value.searchEnabled
        _uiState.update { it.copy(settingsSaved = true) }
    }

    fun newConversation() {
        viewModelScope.launch {
            val id = repository.createConversation()
            switchConversation(id)
        }
    }

    fun switchConversation(id: String) {
        if (id == currentConversationId.value || uiState.value.isSending) return
        currentConversationId.value = id
        _uiState.update {
            it.copy(
                selectedConversationId = id,
                draft = "",
                inProgressReply = "",
                memoryReferenceCount = 0,
                pendingAttachments = emptyList(),
            )
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (id == currentConversationId.value) {
                val replacement = uiState.value.conversations.firstOrNull { it.id != id }
                if (replacement != null) switchConversation(replacement.id) else newConversation()
            }
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch { repository.createGroup(name) }
    }

    fun assignCurrentConversation(groupId: String?) {
        viewModelScope.launch { repository.setConversationGroup(currentConversationId.value, groupId) }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { repository.deleteMemory(id) }
    }

    fun clearAllMemories() {
        viewModelScope.launch { repository.deleteAllMemories() }
    }

    fun clearAllActionLogs() {
        viewModelScope.launch { repository.deleteAllActionLogs() }
    }

    fun clearActionLogsForPackage(packageName: String) {
        viewModelScope.launch { repository.deleteActionLogsByPackage(packageName) }
    }

    fun dismissMemoryNotice() = _uiState.update { it.copy(memoryNotice = null) }

    fun send() {
        val text = uiState.value.draft.trim()
        val attachments = uiState.value.pendingAttachments
        if (text.isEmpty() && attachments.isEmpty()) return
        if (uiState.value.isSending) return
        val current = uiState.value
        if (current.baseUrl.isBlank() || current.model.isBlank() || current.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请先在设置中填写 API 地址、Key 和模型") }
            return
        }
        // 替我行动模式：消息作为任务指令，走 AI 自动操作链路（读屏 → 决策 → 确认 → 执行 → 多轮续判）
        if (current.actionModeEnabled && text.isNotBlank() && attachments.isEmpty()) {
            startActionTask(text)
            return
        }
        _uiState.update { it.copy(draft = "", isSending = true, error = null, pendingAttachments = emptyList()) }
        viewModelScope.launch {
            val conversationId = currentConversationId.value
            repository.appendMessage(conversationId, "user", text, attachments)
            if (text.isNotBlank()) extractMemory(text)
            val history = repository.messagesForRequest(conversationId)
            val memoryContext = relevantMemories(text, current.memories)
            _uiState.update { it.copy(memoryReferenceCount = memoryContext.size) }

            // 联网搜索：开启且配置了 Key 时，将搜索结果注入上下文
            val searchResults: List<SearchResult> = if (current.searchEnabled && text.isNotBlank()) {
                _uiState.update { it.copy(searching = true) }
                val searchKey = settings.readSearchApiKey()
                if (searchKey.isBlank()) {
                    _uiState.update { it.copy(searching = false, error = "未配置搜索 API Key（Tavily）") }
                    emptyList()
                } else {
                    val result = webSearchClient.search(text, searchKey)
                    _uiState.update { it.copy(searching = false) }
                    result.getOrElse { error ->
                        _uiState.update { it.copy(error = "联网搜索失败：${error.message ?: "未知错误"}") }
                        emptyList()
                    }
                }
            } else emptyList()

            val requestMessages = buildList {
                if (memoryContext.isNotEmpty()) {
                    add(ChatMessageDto(
                        role = "system",
                        content = "以下是与当前问题相关的用户长期记忆，仅在有帮助时使用：\n" +
                            memoryContext.joinToString("\n") { "- ${it.content}" },
                    ))
                }
                if (searchResults.isNotEmpty()) {
                    add(ChatMessageDto(
                        role = "system",
                        content = buildString {
                            append("以下是针对用户问题的联网搜索结果（仅供参考，请优先基于这些信息回答，并标注来源）：\n\n")
                            searchResults.forEachIndexed { index, result ->
                                append("${index + 1}. ${result.title}\n")
                                append("   来源：${result.url}\n")
                                append("   摘要：${result.snippet}\n\n")
                            }
                        },
                    ))
                }
                addAll(history.map { it.toChatMessageDto() })
            }
            _uiState.update { it.copy(searchResultCount = searchResults.size) }
            val request = ChatCompletionRequest(
                model = current.model,
                messages = requestMessages,
                stream = current.streaming,
            )
            val assistant = StringBuilder()
            val result = if (current.streaming) {
                llmClient.stream(current.baseUrl, current.apiKey, request) { token ->
                    assistant.append(token)
                    _uiState.update { it.copy(inProgressReply = assistant.toString()) }
                }
            } else {
                llmClient.complete(current.baseUrl, current.apiKey, request).onSuccess {
                    assistant.append(it)
                    _uiState.update { state -> state.copy(inProgressReply = it) }
                }.map { }
            }
            result.onSuccess {
                repository.appendMessage(conversationId, "assistant", assistant.toString())
                _uiState.update { it.copy(isSending = false, inProgressReply = "", searchResultCount = 0) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        inProgressReply = "",
                        searchResultCount = 0,
                        error = error.message ?: "请求失败",
                    )
                }
            }
        }
    }

    fun toggleStreaming() = _uiState.update { it.copy(streaming = !it.streaming) }

    /** 切换联网搜索开关 */
    fun toggleSearchEnabled() {
        val newValue = !uiState.value.searchEnabled
        settings.searchEnabled = newValue
        _uiState.update { it.copy(searchEnabled = newValue) }
    }

    /** 切换悬浮窗模式 */
    fun toggleFloatingWindow() {
        val context = _context
        val newValue = !uiState.value.floatingWindowEnabled
        if (newValue) {
            if (!FloatingWindowService.hasOverlayPermission(context)) {
                _uiState.update { it.copy(error = "需要先授予悬浮窗权限") }
                openOverlaySettings(context)
                return
            }
            FloatingWindowService.start(context)
            settings.floatingWindowEnabled = true
            _uiState.update { it.copy(floatingWindowEnabled = true) }
        } else {
            FloatingWindowService.stop(context)
            settings.floatingWindowEnabled = false
            _uiState.update { it.copy(floatingWindowEnabled = false) }
        }
    }

    // ===== 替我行动模式 =====

    fun toggleActionMode() {
        _uiState.update { it.copy(actionModeEnabled = !it.actionModeEnabled) }
    }

    fun startActionMode() {
        if (!ScreenReaderAccessibilityService.isConnected) {
            _uiState.update { it.copy(error = "请先在设置中开启无障碍服务") }
            return
        }
        _uiState.update { it.copy(actionStatus = ActionStatus.Readying, lastScreenPackage = "") }

        viewModelScope.launch {
            val screenText = extractScreenText()
            _uiState.update {
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
            com.yanxing.agent.service.ActionExecutor.extractText(root).take(1000)
        }.getOrElse { "读取失败：$it" }
        return "当前界面：$pkg\n内容:\n$text"
    }

    /** 行动模式入口：把用户消息作为任务，读屏后让 AI 规划动作（第一轮决策） */
    private fun startActionTask(goal: String) {
        if (!ScreenReaderAccessibilityService.isConnected) {
            _uiState.update { it.copy(draft = "", error = "请先在设置中开启无障碍服务") }
            return
        }
        actionGoal = goal
        actionRunner.start()
        actionHistory.clear()
        _uiState.update { it.copy(draft = "", isSending = true, error = null, pendingAttachments = emptyList()) }

        viewModelScope.launch {
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
            _uiState.update { it.copy(isSending = false) }
            // 请求期间用户可能已点停止，此时不再进入确认流程
            if (actionRunner.isCancelled) return@launch
            result.onSuccess { reply ->
                val sequence = AIDecisionEngine.parseLLMResponse(reply)
                if (sequence.actions.isEmpty()) {
                    val message = sequence.error ?: "AI 未规划出可执行的操作"
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Completed(0, 0),
                            error = if (sequence.error != null) message else null,
                        )
                    }
                    repository.appendMessage(conversationId, "assistant", "任务完成：无需执行操作。$message")
                    resetActionContext()
                } else {
                    _uiState.update {
                        it.copy(actionStatus = ActionStatus.PendingConfirm.Waiting(sequence.actions, 0))
                    }
                }
            }.onFailure { error ->
                _uiState.update {
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
            _uiState.update { it.copy(error = "无障碍服务未开启") }
            return
        }
        actionGoal = prompt.ifBlank { actionGoal }
        actionRunner.start()
        actionHistory.clear()

        if (actions.isEmpty()) {
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Completed(0, 0),
                    error = "没有检测到可执行的操作",
                )
            }
            progressOverlay?.hide()
            progressOverlay = null
            resetActionContext()
        } else {
            _uiState.update {
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
        _uiState.update { it.copy(actionStatus = ActionStatus.PendingConfirm.Canceled) }
        val conversationId = currentConversationId.value
        viewModelScope.launch {
            batchedLogWriter.addLog(
                packageName = uiState.value.lastScreenPackage,
                actionType = "stop",
                targetElement = null,
                details = summary,
                status = ActionStatus.PendingConfirm.Canceled,
                errorMessage = null,
            )
            batchedLogWriter.forceFlush()
            repository.appendMessage(conversationId, "assistant", summary)
        }
        progressOverlay?.hide()
        progressOverlay = null
        actionRunner.reset()
        actionGoal = ""
        actionHistory.clear()
        executedActions.clear()
    }

    // ===== Root 增强命令入口 =====

    /** 读取电池百分比 */
    fun readBatteryLevel(): String? {
        if (!rootShell.isRootAvailable()) return null
        val level = runCatching { rootShell.batteryLevel() }.getOrNull() ?: return null
        return "${level}%"
    }

    /** 读取当前屏幕亮度 */
    fun readScreenBrightness(): Int? {
        if (!rootShell.isRootAvailable()) return null
        return runCatching { rootShell.screenBrightness() }.getOrNull()
    }

    /** 设置屏幕亮度 */
    fun setScreenBrightness(value: Int): Boolean {
        if (!rootShell.isRootAvailable()) return false
        return runCatching { rootShell.setScreenBrightness(value) }.getOrDefault(false)
    }

    /** 点亮屏幕 */
    fun wakeScreen(): Boolean {
        if (!rootShell.isRootAvailable()) return false
        return runCatching { rootShell.wakeScreen() }.getOrDefault(false)
    }

    /** 获取设备信息 */
    fun getDeviceInfo(): String? {
        if (!rootShell.isRootAvailable()) return null
        return runCatching { rootShell.deviceInfo() }.getOrNull()?.replace("&&", "\n")
    }

    /**
     * 撤销上一个成功执行的行动。
     * 从栈中弹出最近动作 → RollbackController 生成逆操作 → 确认执行 → 记 rollback 日志
     */
    fun undoLastAction() {
        val lastAction = executedActions.removeLastOrNull() ?: run {
            progressOverlay?.toast("没有可撤销的动作")
            _uiState.update { it.copy(error = "没有可撤销的操作记录") }
            return
        }

        val suggestion = RollbackController.suggestRollback(lastAction, uiState.value.lastScreenPackage.orEmpty())
        val conversationId = currentConversationId.value
        
        if (suggestion == null || suggestion.actions.isEmpty()) {
            progressOverlay?.toast("该动作暂不支持自动撤销：${lastAction.toDesc()}")
            _uiState.update { it.copy(error = "无法自动生成撤销操作") }
            // 保留动作在栈里（没删），但提示手动恢复
            return
        }

        // 展示撤销说明
        val undoSummary = "撤销 ${lastAction.toDesc()}：\n${suggestion.description}"
        _uiState.update { it.copy(actionStatus = ActionStatus.Thinking(actionRunner.round)) }
        
        viewModelScope.launch {
            batchedLogWriter.addLog(
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
                batchedLogWriter.addLog(
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
            _uiState.update {
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
            _uiState.update {
                it.copy(actionStatus = ActionStatus.PendingConfirm.Waiting(actions, index))
            }
            executePendingActionSkipping(actions, index + 1)
        }
    }

    private fun executePendingAction(actions: List<AIDecisionEngine.Action>, nextIndex: Int) {
        viewModelScope.launch {
            // 边界保护：nextIndex-1 必须是有效动作索引
            if (nextIndex <= 0 || nextIndex > actions.size) return@launch
            // 用户已停止：不再触碰系统 UI
            if (actionRunner.isCancelled) return@launch

            val action = actions[nextIndex - 1]

            // 在 Default dispatcher 上执行无障碍操作（避免阻塞主线程）
            val result = withContext(Dispatchers.Default) {
                when (action) {
                    is AIDecisionEngine.Action.Click -> com.yanxing.agent.service.ActionExecutor.click(action.query)
                    is AIDecisionEngine.Action.LongPress -> com.yanxing.agent.service.ActionExecutor.longPress(action.query)
                    is AIDecisionEngine.Action.Swipe -> com.yanxing.agent.service.ActionExecutor.swipe(action.direction)
                    is AIDecisionEngine.Action.InputText -> com.yanxing.agent.service.ActionExecutor.inputText(action.query, action.text)
                    is AIDecisionEngine.Action.Back -> com.yanxing.agent.service.ActionExecutor.back()
                    is AIDecisionEngine.Action.ClearText -> com.yanxing.agent.service.ActionExecutor.clearText(action.query)
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

            // 批量写入操作日志（性能优化）
            viewModelScope.launch {
                val packageName = uiState.value.lastScreenPackage.orEmpty()
                batchedLogWriter.addLog(
                    packageName = packageName,
                    actionType = when (action) {
                        is AIDecisionEngine.Action.Click -> "click"
                        is AIDecisionEngine.Action.LongPress -> "click"
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

            // 动作执行期间用户点了停止：保留已写入的日志，不再推进
            if (actionRunner.isCancelled) return@launch

            if (result.success && nextIndex < actions.size) {
                // 继续下一个动作
                _uiState.update {
                    it.copy(
                        actionStatus = ActionStatus.PendingConfirm.Waiting(actions, nextIndex)
                    )
                }
            } else if (result.success) {
                // 本组动作全部执行完成 → 多轮决策：回传结果让 AI 根据新屏幕续判
                continueDecision()
            } else {
                // 失败，提示用户但继续询问下一个
                _uiState.update {
                    it.copy(
                        actionStatus = ActionStatus.PendingConfirm.Waiting(actions, nextIndex - 1),
                        error = "操作${action.toDesc()}失败，请重试"
                    )
                }
            }
        }
    }

    private fun executePendingActionSkipping(actions: List<AIDecisionEngine.Action>, skippedIndex: Int) {
        if (skippedIndex >= actions.size) {
            // 全部跳过，任务结束
            finishAction("用户跳过了所有动作，任务结束")
            return
        }

        _uiState.update {
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
        if (actionRunner.isCancelled) return
        if (!actionRunner.canContinue()) {
            finishAction("已达最大决策轮次（$MAX_ACTION_ROUNDS），任务停止", isError = true)
            return
        }
        val goal = actionGoal
        _uiState.update { it.copy(actionStatus = ActionStatus.Thinking(actionRunner.round)) }

        viewModelScope.launch {
            val screenText = extractScreenText()
            if (actionRunner.isCancelled) return@launch
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
            if (actionRunner.isCancelled) return@launch
            result.onSuccess { reply ->
                val sequence = AIDecisionEngine.parseLLMResponse(reply)
                when {
                    sequence.done -> finishAction(sequence.reason ?: "任务完成")
                    sequence.actions.isEmpty() -> finishAction(sequence.error ?: "任务完成")
                    else -> {
                        actionRunner.nextRound()
                        _uiState.update {
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
        _uiState.update {
            it.copy(
                actionStatus = ActionStatus.Completed(0, 0),
                error = if (isError) message else null,
            )
        }
        viewModelScope.launch {
            repository.appendMessage(conversationId, "assistant", message)
        }
        progressOverlay?.hide()
        progressOverlay = null
        resetActionContext()
    }

    private fun resetActionContext() {
        actionGoal = ""
        actionRunner.reset()
        actionHistory.clear()
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private suspend fun extractMemory(text: String) {
        val rules = listOf(
            "我喜欢" to "偏好",
            "我偏好" to "偏好",
            "请记住" to "用户资料",
            "我正在" to "项目",
            "我的项目是" to "项目",
        )
        val match = rules.firstOrNull { text.contains(it.first) } ?: return
        if (listOf("api key", "密码", "验证码", "token", "密钥").any { text.contains(it, ignoreCase = true) }) return
        val memory = repository.saveMemory(text, match.second)
        _uiState.update { it.copy(memoryNotice = memory) }
    }

    private fun relevantMemories(query: String, memories: List<Memory>): List<Memory> {
        val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }
            .toSet()
        return memories.filter { memory ->
            terms.any { term -> memory.content.lowercase().contains(term) } ||
                (query.contains("项目") && memory.category == "项目") ||
                (query.contains("喜欢") && memory.category == "偏好")
        }.take(5)
    }

    private fun loadSettings() {
        _uiState.update {
            it.copy(
                baseUrl = settings.baseUrl,
                model = settings.model,
                apiKey = settings.readApiKey(),
                searchApiKey = settings.readSearchApiKey(),
                searchEnabled = settings.searchEnabled,
                floatingWindowEnabled = settings.floatingWindowEnabled,
                accessibilityEnabled = isAccessibilityEnabled(),
                rootAvailable = RootShell.isRootAvailable(),
                batteryLevel = if (RootShell.isRootAvailable()) {
                    runCatching { 
                        val level = RootShell.batteryLevel() 
                        if (level != null) "${level}%" else ""
                    }.getOrNull().orEmpty()
                } else "",
            )
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expectedId = "${_context.packageName}/${ScreenReaderAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            _context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(expectedId, ignoreCase = true) }
    }

    private fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            // 某些设备可能没有该 Intent，忽略
        }
    }

    /** 确保悬浮窗已初始化 */
    private fun ensureProgressOverlay() {
        if (progressOverlay == null) {
            progressOverlay = FloatingProgressOverlay(_context).apply {
                onStopRequested = { stopAction() }
                onUndoRequested = { undoLastAction() }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 清理资源
        batchedLogWriter.shutdown()
        voiceInput.release()
        RootShell.resetCache() // 清理 root 可用性缓存
        progressOverlay?.hide()
        progressOverlay = null
    }

    /** 将 ChatMessage 转换为 API 请求的 ChatMessageDto，支持多模态 */
    private fun ChatMessage.toChatMessageDto(): ChatMessageDto {        val images = attachments.filter { it.type == "image" && it.base64 != null }
        return if (images.isNotEmpty()) {
            ChatMessageDto.withImage(
                role = role,
                text = content,
                imageBase64 = images.first().base64!!,
                mimeType = images.first().mimeType,
            )
        } else {
            ChatMessageDto.text(role, content)
        }
    }
}

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val groups: List<ConversationGroup> = emptyList(),
    val selectedConversationId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val memoryNotice: Memory? = null,
    val memoryReferenceCount: Int = 0,
    val draft: String = "",
    val pendingAttachments: List<Attachment> = emptyList(), // 待发送的附件
    val voiceInputMode: Boolean = false,
    val searchEnabled: Boolean = false,
    val searching: Boolean = false,
    val searchResultCount: Int = 0,
    val searchApiKey: String = "",
    val floatingWindowEnabled: Boolean = false,
    val rootAvailable: Boolean? = null,
    val batteryLevel: String = "", // 电池百分比（Root 增强）
    val accessibilityEnabled: Boolean = false,
    val actionModeEnabled: Boolean = false, // 替我行动模式开关
    val actionStatus: ActionStatus = ActionStatus.Idle, // 当前行动状态
    val lastScreenPackage: String = "", // 最近读取的屏幕包名（用于显示）
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val streaming: Boolean = true,
    val isSending: Boolean = false,
    val inProgressReply: String = "",
    val error: String? = null,
    val settingsSaved: Boolean = false,
    val actionLogs: List<ActionLogEntity> = emptyList(), // 操作日志列表
)
