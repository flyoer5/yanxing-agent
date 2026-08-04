package com.yanxing.agent.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** 联网搜索结果条目 */
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

/** 搜索客户端接口 */
interface WebSearchClient {
    suspend fun search(query: String, apiKey: String): Result<List<SearchResult>>
}

/**
 * Tavily Search API 客户端
 * 专为 LLM 设计，返回干净的摘要内容
 */
class TavilySearchClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WebSearchClient {

    override suspend fun search(query: String, apiKey: String): Result<List<SearchResult>> {
        return try {
            if (apiKey.isBlank()) return Result.failure(IllegalStateException("未配置搜索 API Key"))
            withContext(ioDispatcher) {
                val body = json.encodeToString(
                    TavilyRequest.serializer(),
                    TavilyRequest(
                        api_key = apiKey,
                        query = query,
                        max_results = 5,
                        search_depth = "basic",
                        include_answer = false,
                    ),
                ).toRequestBody(JSON)

                val request = Request.Builder()
                    .url(TAVILY_ENDPOINT)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "搜索失败：HTTP ${response.code}" }
                    val parsed = json.decodeFromString<TavilyResponse>(response.body?.string().orEmpty())
                    parsed.results.map { result ->
                        SearchResult(
                            title = result.title,
                            url = result.url,
                            snippet = result.content,
                        )
                    }
                }
            }.let { Result.success(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val TAVILY_ENDPOINT = "https://api.tavily.com/search"
    }
}

@Serializable
data class TavilyRequest(
    @SerialName("api_key") val api_key: String,
    val query: String,
    @SerialName("max_results") val max_results: Int = 5,
    @SerialName("search_depth") val search_depth: String = "basic",
    @SerialName("include_answer") val include_answer: Boolean = false,
)

@Serializable
data class TavilyResponse(
    val results: List<TavilyResult> = emptyList(),
)

@Serializable
data class TavilyResult(
    val title: String = "",
    val url: String = "",
    val content: String = "",
    val score: Double = 0.0,
)
