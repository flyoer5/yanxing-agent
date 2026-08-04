package com.yanxing.agent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yanxing.agent.data.ChatMessage
import com.yanxing.agent.data.ChatRepository
import com.yanxing.agent.data.ModelSettingsStore
import com.yanxing.agent.network.ChatCompletionRequest
import com.yanxing.agent.network.ChatMessageDto
import com.yanxing.agent.network.LlmClient
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val settings: ModelSettingsStore,
    private val llmClient: LlmClient,
) : ViewModel() {
    private val conversationId = UUID.randomUUID().toString()
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureConversation(conversationId)
            repository.observeMessages(conversationId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
        loadSettings()
    }

    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrl = value) }
    fun updateApiKey(value: String) = _uiState.update { it.copy(apiKey = value) }
    fun updateModel(value: String) = _uiState.update { it.copy(model = value) }
    fun updateDraft(value: String) = _uiState.update { it.copy(draft = value) }

    fun saveSettings() {
        settings.baseUrl = uiState.value.baseUrl
        settings.model = uiState.value.model
        settings.saveApiKey(uiState.value.apiKey)
        _uiState.update { it.copy(settingsSaved = true) }
    }

    fun send() {
        val text = uiState.value.draft.trim()
        if (text.isEmpty() || uiState.value.isSending) return
        val current = uiState.value
        if (current.baseUrl.isBlank() || current.model.isBlank() || current.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请先在设置中填写 API 地址、Key 和模型") }
            return
        }
        _uiState.update { it.copy(draft = "", isSending = true, error = null) }
        viewModelScope.launch {
            repository.appendMessage(conversationId, "user", text)
            val history = uiState.value.messages + ChatMessage("draft", "user", text)
            val request = ChatCompletionRequest(
                model = current.model,
                messages = history.map { ChatMessageDto(it.role, it.content) },
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
                _uiState.update { it.copy(isSending = false, inProgressReply = "") }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSending = false, inProgressReply = "", error = error.message ?: "请求失败")
                }
            }
        }
    }

    fun toggleStreaming() = _uiState.update { it.copy(streaming = !it.streaming) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun loadSettings() {
        _uiState.update {
            it.copy(
                baseUrl = settings.baseUrl,
                model = settings.model,
                apiKey = settings.readApiKey(),
            )
        }
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val streaming: Boolean = true,
    val isSending: Boolean = false,
    val inProgressReply: String = "",
    val error: String? = null,
    val settingsSaved: Boolean = false,
)
