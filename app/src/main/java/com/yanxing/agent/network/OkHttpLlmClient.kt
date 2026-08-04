package com.yanxing.agent.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.util.concurrent.TimeUnit
import javax.inject.Inject

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
            val response = execute(baseUrl, apiKey, request.copy(stream = false))
            response.use {
                check(it.isSuccessful) { "请求失败：HTTP ${it.code}" }
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
                val response = execute(baseUrl, apiKey, request.copy(stream = true))
                response.use {
                    check(it.isSuccessful) { "请求失败：HTTP ${it.code}" }
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

    private fun execute(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest,
    ) = httpClient.newCall(
        Request.Builder()
            .url(endpoint(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(request).toRequestBody(JSON))
            .build()
    ).execute()

    private suspend fun readServerSentEvents(
        source: BufferedSource,
        onToken: suspend (String) -> Unit,
    ) {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            runCatching {
                json.decodeFromString<ChatCompletionResponse>(payload)
                    .choices.firstOrNull()?.delta?.content
            }.getOrNull()?.let { if (it.isNotEmpty()) onToken(it) }
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
        .build()

    @dagger.Provides
    @javax.inject.Singleton
    fun provideLlmClient(client: OkHttpClient, json: Json): LlmClient =
        OkHttpLlmClient(client, json)
}
