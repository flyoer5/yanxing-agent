package com.yanxing.agent.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * 语音输入封装：包装 Android 原生 SpeechRecognizer。
 *
 * 同时服务两个入口：聊天界面的麦克风按钮和悬浮窗快捷面板的语音按钮。
 * 识别是一次性的（partialResults 关闭），拿到结果或错误后立即释放识别器。
 */
class VoiceInputController(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    /** 部分 OEM ROM 不回调 onError，超时兜底避免 listening 永远为 true */
    private val timeoutRunnable = Runnable {
        if (listening) {
            finish(lastOnStateChanged)
            lastOnError("语音识别超时，请重试")
        }
    }
    private var lastOnError: (String) -> Unit = {}
    private var lastOnStateChanged: (Boolean) -> Unit = {}

    val isListening: Boolean get() = listening

    /**
     * 开始一次语音识别。
     *
     * @param onResult 识别成功，回传文本
     * @param onError 识别失败，回传中文可读的错误描述
     * @param onStateChanged 监听状态变化（true=开始录音，false=结束）
     */
    fun start(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onStateChanged: (Boolean) -> Unit = {},
    ) {
        if (listening) {
            onError("正在识别中，请稍候")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("当前设备不支持语音识别")
            return
        }

        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (created == null) {
            onError("语音识别初始化失败")
            return
        }
        recognizer = created
        listening = true
        lastOnError = onError
        lastOnStateChanged = onStateChanged
        onStateChanged(true)
        timeoutHandler.postDelayed(timeoutRunnable, LISTEN_TIMEOUT_MS)

        // 提前捕获到局部变量：监听器内部也有 onError 方法，避免同名解析歧义
        val emitResult = onResult
        val emitError = onError

        created.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                finish(onStateChanged)
                if (text.isBlank()) emitError("没有识别到内容") else emitResult(text)
            }

            override fun onError(error: Int) {
                finish(onStateChanged)
                emitError(describeError(error))
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        val started = runCatching { created.startListening(intent) }.isSuccess
        if (!started) {
            finish(onStateChanged)
            onError("无法启动语音识别")
        }
    }

    /** 主动取消识别 */
    fun cancel() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        runCatching { recognizer?.cancel() }
        release()
    }

    /** 释放识别器资源 */
    fun release() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        runCatching { recognizer?.destroy() }
        recognizer = null
        listening = false
    }

    private fun finish(onStateChanged: (Boolean) -> Unit) {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        release()
        onStateChanged(false)
    }

    companion object {
        private const val LISTEN_TIMEOUT_MS = 15_000L
        /** 把 SpeechRecognizer 错误码翻译成中文提示 */
        fun describeError(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "录音出错，请重试"
            SpeechRecognizer.ERROR_CLIENT -> "识别客户端错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限，请在系统设置中授权"
            SpeechRecognizer.ERROR_NETWORK -> "网络异常，语音识别失败"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时，语音识别失败"
            SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到内容"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，请稍后重试"
            SpeechRecognizer.ERROR_SERVER -> "识别服务出错"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到说话"
            else -> "语音识别失败（错误码 $error）"
        }
    }
}
