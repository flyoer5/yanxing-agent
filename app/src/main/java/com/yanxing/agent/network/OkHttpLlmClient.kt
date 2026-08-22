package com.yanxing.agent.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OkHttpLlmClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LlmClient {
    override suspend fun complete(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest,
    ): Result<String> = try {
        withContext(ioDispatcher) {
            val response = awaitCall(buildCall(baseUrl, apiKey, request.copy(stream = false)))
            response.use {
                check(it.isSuccessful) { httpError("请求失败", it) }
                val body = it.body?.string().orEmpty()
                json.decodeFromString<ChatCompletionResponse>(body)
                    .choices.firstOrNull()?.message?.content
                    .orEmpty()
            }
        }.let { Result.success(it) }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun stream(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest,
        onToken: suspend (String) -> Unit,
    ): Result<Unit> {
        return try {
            withContext(ioDispatcher) {
                // 流式响应改用独立的长 readTimeout：长思考模型首 token 可能超过 90 秒
                val streamClient = httpClient.newBuilder()
                    .readTimeout(10, TimeUnit.MINUTES)
                    .build()
                val response = awaitCall(streamClient, buildCall(streamClient, baseUrl, apiKey, request.copy(stream = true)))
                response.use {
                    check(it.isSuccessful) { httpError("请求失败", it) }
                    val source = it.body?.source() ?: error("响应为空")
                    readServerSentEvents(source, onToken)
                }
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /** 非阻塞执行并挂起等待；协程取消时同步取消底层 Call，释放连接与线程 */
    private suspend fun awaitCall(client: OkHttpClient, call: Call): Response =
        suspendCancellableCoroutine { cont ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
            })
            cont.invokeOnCancellation { call.cancel() }
        }

    private suspend fun awaitCall(call: Call): Response = awaitCall(httpClient, call)

    private fun buildCall(
        client: OkHttpClient = httpClient,
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest,
    ): Call = client.newCall(
        Request.Builder()
            .url(endpoint(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(json.encodeToString<ChatCompletionRequest>(request).toRequestBody(JSON))
            .build()
    )

    /**
     * 读取错误响应体再拼异常消息：4xx/5xx 的 body 里通常有
     * "invalid api key" / "insufficient quota" 等具体原因，只报状态码会把信息丢光
     */
    private fun httpError(prefix: String, response: Response): String {
        val detail = runCatching { response.body?.string() }.getOrNull()
            ?.take(300)
            ?.replace("\n", " ")
            .orEmpty()
        return if (detail.isBlank()) "$prefix：HTTP ${response.code}" else "$prefix：HTTP ${response.code} $detail"
    }

    private suspend fun readServerSentEvents(
        source: BufferedSource,
        onToken: suspend (String) -> Unit,
    ) {
        var parseFailures = 0
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            // SSE 允许多行 data 拼接；OpenAI 兼容网关偶发把单条 JSON 拆行，先积累再解析
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            runCatching {
                json.decodeFromString<ChatCompletionResponse>(payload)
                    .choices.firstOrNull()?.delta?.content
            }.onFailure { parseFailures++ }
                .getOrNull()?.let { if (it.isNotEmpty()) onToken(it) }
        }
        // 静默吞掉解析错误会让内容缺失无从排查，超阈值时至少在结果里暴露
        if (parseFailures > 5) {
            check(false) { "流式响应有 $parseFailures 条数据解析失败，内容可能缺失" }
        }
    }

    private fun endpoint(baseUrl: String): String {
        val normalized = baseUrl.trim().removeSuffix("/")
        return if (normalized.endsWith("/v1")) "$normalized/chat/completions"
        else "$normalized/v1/chat/completions"
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object NetworkModule {
    @dagger.Provides
    @javax.inject.Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @dagger.Provides
    @javax.inject.Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // enqueue 触发的连接池清理等后台任务
        .build()

    @dagger.Provides
    @javax.inject.Singleton
    fun provideLlmClient(
        client: OkHttpClient,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): LlmClient = OkHttpLlmClient(client, json, ioDispatcher)

    @dagger.Provides
    @javax.inject.Singleton
    fun provideWebSearchClient(
        client: OkHttpClient,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): WebSearchClient = TavilySearchClient(client, json, ioDispatcher)
}
