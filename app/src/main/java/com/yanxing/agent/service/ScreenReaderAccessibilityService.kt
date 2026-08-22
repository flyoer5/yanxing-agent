package com.yanxing.agent.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** 读取当前窗口文字，并提供给行动模式使用。 */
class ScreenReaderAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var textRefreshPending = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isConnected = true
        lastScreenText = extractText(rootInActiveWindow)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 窗口切换是低频强信号，立即刷新
            lastScreenPackage = event.packageName?.toString().orEmpty()
            refreshScreenText()
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // 内容变化在滚动/动画时每秒可触发数十上百次，全树遍历必须节流，
            // 否则目标应用会被读屏服务拖卡
            lastScreenPackage = event.packageName?.toString().orEmpty()
            if (!textRefreshPending) {
                textRefreshPending = true
                mainHandler.postDelayed({
                    textRefreshPending = false
                    refreshScreenText()
                }, CONTENT_CHANGED_THROTTLE_MS)
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            lastNotification = event.text?.joinToString(" ").orEmpty()
        }
    }

    private fun refreshScreenText() {
        lastScreenText = extractText(rootInActiveWindow)
        lastUpdatedAt = System.currentTimeMillis()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        // 系统重建服务时可能"新实例 connect → 旧实例 destroy"，
        // 只有当前注册实例才能清状态，否则会把新连接误标为断开
        if (instance === this) {
            instance = null
            isConnected = false
        }
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun extractText(node: AccessibilityNodeInfo?): String = ActionExecutor.extractText(node)

    companion object {
        private const val CONTENT_CHANGED_THROTTLE_MS = 300L

        @Volatile var instance: ScreenReaderAccessibilityService? = null
            private set
        @Volatile var isConnected: Boolean = false
            private set
        @Volatile var lastScreenText: String = ""
            private set
        @Volatile var lastScreenPackage: String = ""
            private set
        @Volatile var lastUpdatedAt: Long = 0L
            private set
        @Volatile var lastNotification: String = ""
            private set

        fun openSettings(context: android.content.Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
