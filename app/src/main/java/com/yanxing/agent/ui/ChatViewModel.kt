package com.yanxing.agent.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.yanxing.agent.service.FloatingWindowService
import com.yanxing.agent.service.RootShell
import com.yanxing.agent.service.ScreenReaderAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val currentConversationId = MutableStateFlow("")
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

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

    // ===== 设置语音输入状态 =====
    fun setVoiceInputMode(enabled: Boolean) {
        _uiState.update { it.copy(voiceInputMode = enabled) }
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
        _uiState.update { it.copy(actionStatus = ActionStatus.Readying) }
        
        viewModelScope.launch {
            val screenText = extractScreenText()
            _uiState.update { 
                it.copy(
                    actionStatus = ActionStatus.Ready(screenText),
                    draft = "",
                ) 
            }
        }
    }

    private suspend fun extractScreenText(): String {
        val root = ScreenReaderAccessibilityService.rootInActiveWindow
            ?: return "无法读取当前界面"
        val pkg = ScreenReaderAccessibilityService.lastScreenPackage
        val text = runCatching {
            com.yanxing.agent.service.ActionExecutor.extractText(root).take(1000)
        }.getOrElse { "读取失败：$it" }
        return "当前界面：$pkg\n内容:\n$text"
    }

    fun executeAction(prompt: String, actions: List<AIDecisionEngine.Action>) {
        if (!ScreenReaderAccessibilityService.isConnected) {
            _uiState.update { it.copy(error = "无障碍服务未开启") }
            return
        }

        _uiState.update { it.copy(actionStatus = ActionStatus.Executing(actions.size)) }
        
        viewModelScope.launch {
            var successCount = 0
            
            for ((index, action) in actions.withIndex()) {
                _uiState.update { 
                    it.copy(actionStatus = ActionStatus.Executing(index + 1, actions.size, action.toDesc())) 
                }
                
                val result = when (action) {
                    is AIDecisionEngine.Action.Click -> com.yanxing.agent.service.ActionExecutor.click(action.query)
                    is AIDecisionEngine.Action.LongPress -> com.yanxing.agent.service.ActionExecutor.longPress(action.query)
                    is AIDecisionEngine.Action.Swipe -> com.yanxing.agent.service.ActionExecutor.swipe(action.direction)
                    is AIDecisionEngine.Action.InputText -> com.yanxing.agent.service.ActionExecutor.inputText(action.query, action.text)
                }
                
                if (result.success) successCount++
            }
            
            _uiState.update { 
                it.copy(
                    actionStatus = ActionStatus.Completed(successCount, actions.size),
                    error = null
                ) 
            }
        }
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

    /** 将 ChatMessage 转换为 API 请求的 ChatMessageDto，支持多模态 */
    private fun ChatMessage.toChatMessageDto(): ChatMessageDto {
        val images = attachments.filter { it.type == "image" && it.base64 != null }
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
    val accessibilityEnabled: Boolean = false,
    val actionModeEnabled: Boolean = false, // 替我行动模式开关
    val actionStatus: ActionStatus = ActionStatus.Idle, // 当前行动状态
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val streaming: Boolean = true,
    val isSending: Boolean = false,
    val inProgressReply: String = "",
    val error: String? = null,
    val settingsSaved: Boolean = false,
)
