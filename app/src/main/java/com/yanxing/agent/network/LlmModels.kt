package com.yanxing.agent.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

/**
 * 序列化适配 OpenAI 规范：
 * - 纯文本消息 → {"role": "...", "content": "文本"}
 * - 多模态消息 → {"role": "...", "content": [{"type":"text",...}, {"type":"image_url",...}]}
 *
 * 此前 parts 作为独立字段随请求发出——那不是规范字段，标准端点会直接忽略，
 * 导致图片内容实际上从未进入模型。此处经 JsonTransformingSerializer
 * 在序列化时把 parts 合并为 content 数组；反序列化（响应）时兼容 string 与数组两种 content。
 */
object ChatMessageDtoSerializer :
    JsonTransformingSerializer<ChatMessageDto>(ChatMessageDto.generatedSerializer()) {

    override fun transformSerialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        val parts = obj["parts"] ?: return obj
        if (parts is JsonNull) return JsonObject(obj.filterKeys { it != "parts" && it != "content" } + ("content" to (obj["content"] ?: JsonNull)))
        val array = parts.jsonArray
        if (array.isEmpty()) return JsonObject(obj.filterKeys { it != "parts" })
        // content 文本已在 parts[0]（Text），规范数组直接用 parts
        return JsonObject(obj.filterKeys { it != "parts" && it != "content" } + ("content" to array))
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        val content = obj["content"] ?: return obj
        return when {
            content is JsonArray -> {
                // 模型响应里的数组 content：抽取文本拼回字符串，原始数组挂到 parts 供按需解析
                val text = content.filterIsInstance<JsonObject>()
                    .filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                JsonObject(
                    obj + ("content" to (text.ifEmpty { null }?.let(::JsonPrimitive) ?: JsonNull)) +
                        ("parts" to content),
                )
            }
            else -> obj
        }
    }
}

@Serializable(with = ChatMessageDtoSerializer::class)
data class ChatMessageDto(
    val role: String,
    val content: String? = null,
    val parts: List<ContentPart>? = null,
) {
    companion object {
        fun text(role: String, text: String) = ChatMessageDto(role = role, content = text)

        fun withImage(role: String, text: String, imageBase64: String, mimeType: String = "image/jpeg") =
            withImages(role, text, listOf(imageBase64 to mimeType))

        /**
         * 多图消息：文本在前，图片依序排列。
         * @param images (base64, mimeType) 列表
         */
        fun withImages(role: String, text: String, images: List<Pair<String, String>>) =
            ChatMessageDto(
                role = role,
                content = text,
                parts = buildList {
                    add(ContentPart.Text(text))
                    images.forEach { (base64, mimeType) ->
                        add(ContentPart.ImageUrl("data:$mimeType;base64,$base64"))
                    }
                },
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
