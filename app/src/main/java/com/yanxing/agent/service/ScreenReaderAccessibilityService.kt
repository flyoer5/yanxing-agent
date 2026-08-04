package com.yanxing.agent.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：读取当前屏幕文字内容
 * 用于"替我行动"能力的基础 —— 理解用户当前看到的界面
 */
class ScreenReaderAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        lastScreenText = ""
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val text = extractText(rootInActiveWindow)
                if (text.isNotBlank()) {
                    lastScreenText = text
                    lastScreenPackage = event.packageName?.toString() ?: ""
                    lastUpdatedAt = System.currentTimeMillis()
                }
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // 捕获通知文本
                val texts = event.text?.joinToString(" ") ?: ""
                if (texts.isNotBlank()) lastNotification = texts
            }
        }
    }

    override fun onInterrupt() {
        // 服务被中断时不做特殊处理
    }

    override fun onDestroy() {
        isConnected = false
        super.onDestroy()
    }

    private fun extractText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val builder = StringBuilder()
        collectText(node, builder, depth = 0)
        return builder.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo, builder: StringBuilder, depth: Int) {
        if (depth > 20) return // 防止过深递归
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            builder.append(it).append("\n")
        }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
            builder.append("【描述】").append(it).append("\n")
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, builder, depth + 1) }
        }
    }

    companion object {
        /** 服务是否已连接 */
        @Volatile var isConnected: Boolean = false
            private set

        /** 最近一次读取的屏幕文字 */
        @Volatile var lastScreenText: String = ""
            private set

        /** 最近一次读取的屏幕所属包名 */
        @Volatile var lastScreenPackage: String = ""
            private set

        /** 最近更新时间 */
        @Volatile var lastUpdatedAt: Long = 0L
            private set

        /** 最近通知文本 */
        @Volatile var lastNotification: String = ""
            private set

        /** 打开无障碍服务设置页 */
        fun openSettings(context: android.content.Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
