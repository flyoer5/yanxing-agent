package com.yanxing.agent.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容的多模态消息内容项
 * 支持纯文本、图片（base64）、文件等
 */
@Serializable
sealed class ContentPart {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentPart()

    @Serializable
    @SerialName("image_url")
    data class ImageUrl(
        val url: String,  // base64 data URI 或 HTTP URL
        @SerialName("detail") val detail: String = "auto",
    ) : ContentPart()
}

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String? = null,
    val parts: List<ContentPart>? = null,
) {
    companion object {
        fun text(role: String, text: String) = ChatMessageDto(role = role, content = text)
        fun withImage(role: String, text: String, imageBase64: String, mimeType: String = "image/jpeg") =
            ChatMessageDto(
                role = role,
                content = text,
                parts = listOf(
                    ContentPart.Text(text),
                    ContentPart.ImageUrl("data:$mimeType;base64,$imageBase64"),
                ),
            )
    }
}

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
