package com.yanxing.agent.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** 读取当前窗口文字，并提供给行动模式使用。 */
class ScreenReaderAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isConnected = true
        lastScreenText = extractText(rootInActiveWindow)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            lastScreenPackage = event.packageName?.toString().orEmpty()
            lastScreenText = extractText(rootInActiveWindow)
            lastUpdatedAt = System.currentTimeMillis()
        } else if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            lastNotification = event.text?.joinToString(" ").orEmpty()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        isConnected = false
        super.onDestroy()
    }

    private fun extractText(node: AccessibilityNodeInfo?): String = ActionExecutor.extractText(node)

    companion object {
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
