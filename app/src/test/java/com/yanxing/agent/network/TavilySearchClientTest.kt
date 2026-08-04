package com.yanxing.agent.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TavilySearchClientTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parse tavily response`() {
        val payload = """
            {
              "query": "test query",
              "results": [
                {
                  "title": "Example Result",
                  "url": "https://example.com",
                  "content": "This is a snippet about the result.",
                  "score": 0.95
                }
              ],
              "answer": ""
            }
        """.trimIndent()

        val response = json.decodeFromString<TavilyResponse>(payload)

        assertEquals(1, response.results.size)
        assertEquals("Example Result", response.results[0].title)
        assertEquals("https://example.com", response.results[0].url)
        assertEquals("This is a snippet about the result.", response.results[0].content)
        assertTrue(response.results[0].score > 0.9)
    }

    @Test
    fun `tavily request serializes api key`() {
        val request = TavilyRequest(
            api_key = "tvly-test-key",
            query = "Android 最新版本",
            max_results = 5,
            search_depth = "basic",
        )
        val payload = json.encodeToString(TavilyRequest.serializer(), request)
        assertTrue(payload.contains("tvly-test-key"))
        assertTrue(payload.contains("Android 最新版本"))
    }

    @Test
    fun `empty results allowed`() {
        val response = json.decodeFromString<TavilyResponse>("""{"results":[]}""")
        assertTrue(response.results.isEmpty())
    }
}
