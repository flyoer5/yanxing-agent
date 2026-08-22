package com.yanxing.agent.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmMessageSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `纯文本消息序列化为 content 字符串`() {
        val encoded = json.encodeToString(ChatMessageDto.serializer(), ChatMessageDto.text("user", "你好"))
        val obj = json.parseToJsonElement(encoded).jsonObject

        assertEquals("user", obj["role"]!!.jsonPrimitive.content)
        assertEquals("你好", obj["content"]!!.jsonPrimitive.content)
        assertFalse(obj.containsKey("parts"))
    }

    @Test
    fun `多模态消息把 parts 合并为 content 数组`() {
        val dto = ChatMessageDto.withImages(
            role = "user",
            text = "看这两张图",
            images = listOf("QUJD" to "image/png", "REVG" to "image/jpeg"),
        )
        val encoded = json.encodeToString(ChatMessageDto.serializer(), dto)
        val obj = json.parseToJsonElement(encoded).jsonObject

        assertFalse(obj.containsKey("parts"))
        val content = obj["content"]!!.jsonArray
        assertEquals(3, content.size) // 1 文本 + 2 图
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("看这两张图", content[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("image_url", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("data:image/png;base64,QUJD", content[1].jsonObject["url"]!!.jsonPrimitive.content)
        assertEquals("data:image/jpeg;base64,REVG", content[2].jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `响应里的字符串 content 正常解析`() {
        val payload = """{"choices":[{"message":{"role":"assistant","content":"回复内容"}}]}"""
        val response = json.decodeFromString<ChatCompletionResponse>(payload)

        assertEquals("回复内容", response.choices.first().message?.content)
    }

    @Test
    fun `响应里的数组 content 不崩溃且抽取文本`() {
        val payload = """{"choices":[{"message":{"role":"assistant","content":[{"type":"text","text":"段落一"},{"type":"text","text":"段落二"}]}}]}"""
        val response = json.decodeFromString<ChatCompletionResponse>(payload)

        assertEquals("段落一段落二", response.choices.first().message?.content)
    }

    @Test
    fun `流式 delta 缺失 content 为 null`() {
        val payload = """{"choices":[{"delta":{"role":"assistant"}}]}"""
        val response = json.decodeFromString<ChatCompletionResponse>(payload)

        assertTrue(response.choices.first().delta?.content == null)
    }
}
