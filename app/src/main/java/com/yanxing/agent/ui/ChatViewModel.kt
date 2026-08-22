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
import com.yanxing.agent.data.capHistoryForRequest
import com.yanxing.agent.data.nextAvailableConversation
import com.yanxing.agent.network.ChatCompletionRequest
import com.yanxing.agent.network.ChatMessageDto
import com.yanxing.agent.network.LlmClient
import com.yanxing.agent.network.SearchResult
import com.yanxing.agent.network.WebSearchClient
import com.yanxing.agent.service.AIDecisionEngine
import com.yanxing.agent.service.BatchedLogWriter
import com.yanxing.agent.service.FloatingWindowService
import com.yanxing.agent.service.RootCommandExecutor
import com.yanxing.agent.service.RootShell
import com.yanxing.agent.service.ScreenReaderAccessibilityService
import com.yanxing.agent.service.VoiceInputController
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

/** 校验模型服务 BaseUrl 格式（必须 http/https 且非空），纯函数可测 */
fun isValidBaseUrl(url: String): Boolean =
    url.trim().let { trimmed ->
        trimmed.isNotEmpty() &&
            (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
    }

/** 单次最多附件数量 */
const val MAX_ATTACHMENTS = 9

/** 记忆相关性排序（纯函数可测）：关键词匹配 + 项目/偏好分类加权，最多取 5 条 */
fun relevantMemories(query: String, memories: List<Memory>): List<Memory> {
    val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 2 }
        .toSet()
    return memories.filter { memory ->
        terms.any { term -> memory.content.lowercase().contains(term) } ||
            (query.contains("项目") && memory.category == "项目") ||
            (query.contains("喜欢") && memory.category == "偏好")
    }.take(5)
}

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

    /** 已编辑过的消息 ID 集合，用于显示「已编辑」标记 */
    private val _editedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val editedMessageIds: StateFlow<Set<String>> = _editedMessageIds.asStateFlow()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // ===== 批量日志写入器 =====
    private val batchedLogWriter = BatchedLogWriter(repository)

    // ===== 语音输入 =====
    private val voiceInput = VoiceInputController(_context)

    // ===== 替我行动编排（读屏→决策→确认→执行→多轮续判→撤销）=====
    private val actionSession by lazy {
        ActionSessionController(
            scope = viewModelScope,
            context = _context,
            repository = repository,
            llmClient = llmClient,
            logWriter = batchedLogWriter,
            uiState = _uiState,
            currentConversationId = currentConversationId,
        )
    }

    // ===== 流式生成控制 =====
    @Volatile private var generationJob: kotlinx.coroutines.Job? = null

    /** 停止当前流式回复生成 */
    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isSending = false) }
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
        val current = uiState.value.pendingAttachments
        if (current.size >= MAX_ATTACHMENTS) {
            _uiState.update { it.copy(error = "最多同时发送 $MAX_ATTACHMENTS 个附件") }
            return
        }
        _uiState.update { it.copy(pendingAttachments = current + attachment) }
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
        val url = uiState.value.baseUrl
        if (!isValidBaseUrl(url)) {
            _uiState.update { it.copy(error = "Base URL 需以 http:// 或 https:// 开头") }
            return
        }
        if (uiState.value.searchEnabled && uiState.value.searchApiKey.isBlank()) {
            _uiState.update { it.copy(error = "开启联网搜索需填写 Tavily API Key") }
            return
        }
        settings.baseUrl = url
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
        if (id == currentConversationId.value) return
        // isSending 在等待逐动作确认时已是 false，必须连同 actionStatus 一起拦截，
        // 否则行动结果会写进切换后的新会话
        if (uiState.value.isSending || uiState.value.actionStatus !is ActionStatus.Idle) {
            _uiState.update { it.copy(error = "行动执行中或生成中，请先停止再切换会话") }
            return
        }
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
                val replacement = nextAvailableConversation(uiState.value.conversations, id)
                if (replacement != null) switchConversation(replacement.id) else newConversation()
            }
        }
    }

    /** 重命名会话 */
    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            val ok = repository.renameConversation(conversationId, newTitle)
            if (!ok) _uiState.update { it.copy(error = "重命名失败（标题为空或会话不存在）") }
        }
    }

    /** 置顶/取消置顶会话 */
    fun togglePinConversation(conversationId: String) {
        viewModelScope.launch {
            val current = uiState.value.conversations.find { it.id == conversationId } ?: return@launch
            val ok = repository.setConversationPinned(conversationId, !current.pinned)
            if (!ok) _uiState.update { it.copy(error = "置顶失败（会话不存在）") }
        }
    }

    /** 归档/取消归档会话 */
    fun toggleArchiveConversation(conversationId: String) {
        viewModelScope.launch {
            val current = uiState.value.conversations.find { it.id == conversationId } ?: return@launch
            val nextArchived = !current.archived
            val ok = repository.setConversationArchived(conversationId, nextArchived)
            if (!ok) {
                _uiState.update { it.copy(error = "归档失败（会话不存在）") }
                return@launch
            }
            if (nextArchived && conversationId == currentConversationId.value) {
                val replacement = nextAvailableConversation(
                    uiState.value.conversations,
                    conversationId,
                )
                if (replacement != null) {
                    switchConversation(replacement.id)
                } else {
                    newConversation()
                }
            }
        }
    }

    /** 按消息内容搜索会话 id（供会话搜索框使用） */
    fun searchConversationsByContent(keyword: String, onResult: (List<String>) -> Unit) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            val ids = repository.searchConversationIdsByContent(keyword)
            onResult(ids)
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch { repository.createGroup(name) }
    }

    /** 删除分组（会话保留为未分组） */
    fun deleteGroup(id: String) {
        viewModelScope.launch {
            repository.deleteGroup(id)
            // 该分组下的会话置为未分组
            val affected = uiState.value.conversations.filter { it.groupId == id }
            affected.forEach { repository.setConversationGroup(it.id, null) }
        }
    }

    /** 重命名分组 */
    fun renameGroup(id: String, newName: String) {
        viewModelScope.launch {
            val ok = repository.renameGroup(id, newName)
            if (!ok) _uiState.update { it.copy(error = "重命名分组失败") }
        }
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

    /** 编辑既有记忆（内容 + 分类） */
    fun updateMemory(id: String, content: String, category: String) {
        viewModelScope.launch {
            val ok = repository.updateMemory(id, content, category)
            if (!ok) {
                _uiState.update { it.copy(error = "记忆更新失败（不存在或内容为空）") }
            }
        }
    }

    /** 手动新增记忆 */
    fun addMemory(content: String, category: String) {
        if (content.isBlank()) {
            _uiState.update { it.copy(error = "记忆内容不能为空") }
            return
        }
        viewModelScope.launch {
            repository.saveMemory(content, category)
        }
    }

    fun clearAllActionLogs() {
        viewModelScope.launch { repository.deleteAllActionLogs() }
    }

    fun clearActionLogsForPackage(packageName: String) {
        viewModelScope.launch { repository.deleteActionLogsByPackage(packageName) }
    }

    fun dismissMemoryNotice() = _uiState.update { it.copy(memoryNotice = null) }

    /** 重发用户消息：复用其内容与附件重新走发送链路（AI 重新回复） */
    fun resendMessage(message: ChatMessage) {
        // 与 switchConversation 同理：等待确认时 isSending 为 false，必须连 actionStatus 一起拦截
        if (uiState.value.isSending || uiState.value.actionStatus !is ActionStatus.Idle) {
            _uiState.update { it.copy(error = "正在生成回复或行动执行中，请稍后再重发") }
            return
        }
        _uiState.update {
            it.copy(draft = message.content, pendingAttachments = message.attachments)
        }
        send()
    }

    /** 编辑消息内容（就地覆盖；空内容拒绝） */
    fun editMessage(messageId: String, newContent: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.editMessage(messageId, newContent)
            if (ok) _editedMessageIds.update { it + messageId }
            else _uiState.update { it.copy(error = "编辑失败（消息不存在或内容为空）") }
            onResult(ok)
        }
    }

    fun send() {
        val text = uiState.value.draft.trim()
        val attachments = uiState.value.pendingAttachments
        if (text.isEmpty() && attachments.isEmpty()) return
        // 行动执行中允许插话（发送普通对话），仅普通生成中拦截
        if (uiState.value.isSending && uiState.value.actionStatus is ActionStatus.Idle) return
        val current = uiState.value
        if (current.baseUrl.isBlank() || current.model.isBlank() || current.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请先在设置中填写 API 地址、Key 和模型") }
            return
        }
        // 替我行动模式：消息作为任务指令，走 AI 自动操作链路（读屏 → 决策 → 确认 → 执行 → 多轮续判）
        // 行动进行中发送的消息视为普通对话（插入对话，不打断当前行动）
        if (current.actionModeEnabled && text.isNotBlank() && attachments.isEmpty() &&
            current.actionStatus is ActionStatus.Idle
        ) {
            actionSession.startTask(text)
            return
        }
        _uiState.update { it.copy(draft = "", isSending = true, error = null, pendingAttachments = emptyList()) }
        generationJob = viewModelScope.launch {
            val conversationId = currentConversationId.value
            // 先取历史再落库：历史消息不含本次内容；附件 base64 已不入库，
            // 本次消息的内容在下方从内存附件现读构建；超长会话按窗口截断
            val history = capHistoryForRequest(repository.messagesForRequest(conversationId))
            repository.appendMessage(conversationId, "user", text, attachments)
            if (text.isNotBlank()) extractMemory(text)
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
                // 本次发送的用户消息：图片 base64 从本地附件文件现读（IO）
                add(buildCurrentUserDto(text, attachments))
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
                generationJob = null
                // 行动执行中插话完成：恢复行动的发送态，不误清
                _uiState.update {
                    val stillActing = it.actionStatus !is ActionStatus.Idle
                    it.copy(
                        isSending = stillActing,
                        inProgressReply = "",
                        searchResultCount = 0,
                    )
                }
            }.onFailure { error ->
                generationJob = null
                _uiState.update {
                    val stillActing = it.actionStatus !is ActionStatus.Idle
                    it.copy(
                        isSending = stillActing,
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

    fun startActionMode() = actionSession.startMode()

    /** 供外部直接注入动作序列（悬浮窗等入口预留） */
    fun executeAction(prompt: String, actions: List<AIDecisionEngine.Action>) =
        actionSession.executeAction(prompt, actions)

    /** 停止当前行动任务 */
    fun stopAction() = actionSession.stopAction()

    /** 用户点击确认按钮，批准/拒绝当前动作 */
    fun confirmCurrentAction(approved: Boolean) = actionSession.confirmCurrentAction(approved)

    /** 撤销上一个成功执行的行动 */
    fun undoLastAction() = actionSession.undoLastAction()

    // ===== Root 增强命令入口 =====

    /** 设置 Root 增强授权；关闭时立即撤销内存中的授权。 */
    fun setRootAuthorization(authorized: Boolean) {
        settings.rootAuthorized = authorized
        RootShell.setAuthorized(authorized)
        _uiState.update { it.copy(rootAuthorized = authorized) }
    }

    fun readBatteryLevel(): String? = RootCommandExecutor.readBatteryLevel()
    fun readScreenBrightness(): Int? = RootCommandExecutor.readScreenBrightness()
    fun setScreenBrightness(value: Int): Boolean = RootCommandExecutor.setScreenBrightness(value)
    fun wakeScreen(): Boolean = RootCommandExecutor.wakeScreen()
    fun goHome(): Boolean = RootCommandExecutor.goHome()
    fun getAppList(): String? = RootCommandExecutor.getAppList()
    fun getDeviceInfo(): String? = RootCommandExecutor.getDeviceInfo()

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


    private fun loadSettings() {
        RootShell.setAuthorized(settings.rootAuthorized)
        // 先回填与子进程无关的轻量设置，避免阻塞启动
        _uiState.update {
            it.copy(
                baseUrl = settings.baseUrl,
                model = settings.model,
                apiKey = settings.readApiKey(),
                searchApiKey = settings.readSearchApiKey(),
                searchEnabled = settings.searchEnabled,
                floatingWindowEnabled = settings.floatingWindowEnabled,
                accessibilityEnabled = isAccessibilityEnabled(),
                rootAuthorized = settings.rootAuthorized,
            )
        }
        // Root 探测与 su 子进程执行（各最长数秒）必须离开主线程，否则启动 ANR
        viewModelScope.launch(Dispatchers.IO) {
            val rootAvailable = RootShell.isRootAvailable()
            val battery = if (settings.rootAuthorized && rootAvailable) {
                runCatching {
                    RootShell.batteryLevel()?.let { level -> "$level%" }
                }.getOrNull().orEmpty()
            } else ""
            _uiState.update {
                it.copy(rootAvailable = rootAvailable, batteryLevel = battery)
            }
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

    override fun onCleared() {
        super.onCleared()
        // 清理资源
        batchedLogWriter.shutdown()
        voiceInput.release()
        RootShell.setAuthorized(false) // ViewModel 销毁时撤销内存授权
        RootShell.resetCache() // 清理 root 可用性缓存
        actionSession.destroy()
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

    /** 构建本次发送的用户消息 DTO：附件已拷贝到私有目录，base64 发送时现读 */
    private suspend fun buildCurrentUserDto(text: String, attachments: List<Attachment>): ChatMessageDto {
        val image = attachments.firstOrNull { it.type == "image" } ?: return ChatMessageDto.text("user", text)
        val base64 = withContext(Dispatchers.IO) { readLocalFileBase64(image.uri) }
            ?: return ChatMessageDto.text("user", text)
        return ChatMessageDto.withImage(
            role = "user",
            text = text,
            imageBase64 = base64,
            mimeType = image.mimeType,
        )
    }

    private fun readLocalFileBase64(uri: String): String? = runCatching {
        val file = java.io.File(java.net.URI(uri).takeIf { it.scheme == "file" } ?: return null)
        if (!file.exists()) null
        else android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
    }.getOrNull()
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
    val actionGoal: String = "",   // 当前行动任务目标（行动中显示）
    val voiceInputMode: Boolean = false,
    val searchEnabled: Boolean = false,
    val searching: Boolean = false,
    val searchResultCount: Int = 0,
    val searchApiKey: String = "",
    val floatingWindowEnabled: Boolean = false,
    val rootAvailable: Boolean? = null,
    val rootAuthorized: Boolean = false,
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
