package com.yanxing.agent.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
 * 手写序列化器，对齐 OpenAI 规范：
 * - 纯文本消息 → {"role": "...", "content": "文本"}
 * - 多模态消息 → {"role": "...", "content": [{"type":"text",...}, {"type":"image_url",...}]}
 *
 * 此前 parts 作为独立字段随请求发出——那不是规范字段，标准端点会直接忽略，
 * 导致图片内容实际上从未进入模型。这里在 JSON 层直接构造规范结构；
 * 反序列化（响应）兼容 string 与数组两种 content。
 */
object ChatMessageDtoSerializer : KSerializer<ChatMessageDto> {

    @Serializable
    private data class Surrogate(
        val role: String,
        val content: String? = null,
        val parts: List<ContentPart>? = null,
    )

    private val surrogateSerializer = Surrogate.serializer()
    override val descriptor: SerialDescriptor = surrogateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: ChatMessageDto) {
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder == null) {
            // 非 JSON 输出退化为代理结构（实际只会走 JSON）
            encoder.encodeSerializableValue(surrogateSerializer, Surrogate(value.role, value.content, value.parts))
            return
        }
        val parts = value.parts
        val json = if (parts.isNullOrEmpty()) {
            buildJsonObject {
                put("role", value.role)
                put("content", value.content)
            }
        } else {
            buildJsonObject {
                put("role", value.role)
                put("content", partsToJsonArray(parts))
            }
        }
        jsonEncoder.encodeJsonElement(json)
    }

    override fun deserialize(decoder: Decoder): ChatMessageDto {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ChatMessageDto 仅支持 JSON 反序列化")
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: ""
        val content = when (val raw = obj["content"]) {
            null, is JsonNull -> null
            is JsonPrimitive -> raw.contentOrNull
            is JsonArray -> {
                // 模型响应中的数组 content：抽取文本段拼为字符串
                raw.filterIsInstance<JsonObject>()
                    .filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                    .ifEmpty { null }
            }
            else -> null
        }
        return ChatMessageDto(role = role, content = content)
    }

    private fun partsToJsonArray(parts: List<ContentPart>): JsonArray = buildJsonArray {
        parts.forEach { part ->
            when (part) {
                is ContentPart.Text -> add(buildJsonObject {
                    put("type", "text")
                    put("text", part.text)
                })
                is ContentPart.ImageUrl -> add(buildJsonObject {
                    put("type", "image_url")
                    put("url", part.url)
                    put("detail", part.detail)
                })
            }
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
