package com.yanxing.agent.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String? = null,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChoiceDto> = emptyList(),
)

@Serializable
data class ChoiceDto(
    val message: ChatMessageDto? = null,
    val delta: ChatMessageDto? = null,
)

interface LlmClient {
    suspend fun complete(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest,
    ): Result<String>

    suspend fun stream(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest,
        onToken: suspend (String) -> Unit,
    ): Result<Unit>
}
