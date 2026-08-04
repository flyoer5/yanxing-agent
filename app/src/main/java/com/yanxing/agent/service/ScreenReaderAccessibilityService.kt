package com.yanxing.agent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedList

/**
 * 无障碍服务：读取当前屏幕文字内容 + 执行辅助操作
 * 用于"替我行动"能力 —— 理解界面并模拟交互
 */
class ScreenReaderAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        lastScreenText = ""
        windowContentChanged(0, "")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val text = extractText(rootInActiveWindow)
                val pkg = event.packageName?.toString() ?: ""
                windowContentChanged(event.eventTime, pkg, text)
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val texts = event.text?.joinToString(" ") ?: ""
                if (texts.isNotBlank()) lastNotification = texts
            }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { isConnected = false; super.onDestroy() }

    // ===================== 公共操作接口（被 AI 调用） =====================

    /**
     * 点击某个 UI 元素（通过 text/id 描述定位）
     * @return 成功或失败描述
     */
    fun clickByText(query: String): String {
        return performClick(query, allowSearchRoot = true)
    }

    /**
     * 长按元素
     */
    fun longPressByText(query: String): String {
        return performLongPress(query)
    }

    /**
     * 滑动（相对坐标或方向）
     * @param direction "up", "down", "left", "right", or start/end coordinate ratio
     */
    fun swipe(direction: String, progressRatio: Float = 0.3f): String {
        try {
            var startX = 0f
            var startY = 0f
            var endX = 0f
            var endY = 0f
            when (direction.lowercase()) {
                "up" -> { val bounds = screenBounds(); startX = bounds.centerX(); startY = bounds.centerY(); endX = startX; endY = bounds.centerY() * (1 - progressRatio) }
                "down" -> { val bounds = screenBounds(); startX = bounds.centerX(); startY = bounds.centerY(); endX = startX; endY = bounds.centerY() * (1 + progressRatio) }
                "left" -> { val bounds = screenBounds(); startX = bounds.right.toFloat(); startY = bounds.centerY(); endX = bounds.left.toFloat(); endY = startY }
                "right" -> { val bounds = screenBounds(); startX = bounds.left.toFloat(); startY = bounds.centerY(); endX = bounds.right.toFloat(); endY = startY }
                else -> { startX = screenBounds().centerX().toFloat(); startY = boundsForDirection(direction).top; endX = startX; endY = boundsForDirection(direction).bottom }
            }
            performGesture(startX.toInt(), startY.toInt(), endX.toInt(), endY.toInt())
            return "已滑动 $direction"
        } catch (e: Exception) {
            return "滑动失败：${e.message}"
        }
    }

    /**
     * 输入文本到指定输入框
     */
    fun inputTextTo(query: String, text: String): String {
        return performInputText(query, text)
    }

    /**
     * 滚动页面上下
     */
    fun scrollPageUp(): String { swipe("down") }
    fun scrollPageDown(): String { swipe("up") }

    // ===================== 内部逻辑 =====================

    private fun windowContentChanged(timestamp: Long, packageStr: String? = "", text: String = "") {
        lastScreenPackage = packageStr.ifBlank { rootInActiveWindow?.packageName?.toString() ?: "" }
        lastScreenText = text.ifBlank { extractText(rootInActiveWindow) }
        lastUpdatedAt = timestamp.coerceAtLeast(System.currentTimeMillis())
    }

    private fun extractText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val builder = StringBuilder()
        collectText(node, builder, depth = 0)
        return builder.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo, builder: StringBuilder, depth: Int) {
        if (depth > 25) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append("\n") }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append("【描述】").append(it).append("\n") }
        for (i in 0 until node.childCount) { node.getChild(i)?.let { collectText(it, builder, depth + 1) } }
    }

    private fun screenBounds() = runCatching {
        displayMetrics.takeIf { it != null }?.run { Rect(0, 0, widthPixels, heightPixels) } ?: Rect()
    }.getOrElse { Rect() }

    private fun boundsForDirection(dir: String) = screenBounds().apply { center().let { centerX.set((it[0] + it[2]) / 2); centerY.set((it[1] + it[3]) / 2) }}

    private fun performClick(query: String, allowSearchRoot: Boolean = false): String {
        val node = searchNodeBy(query, rootInActiveWindow, allowSearchRoot) ?: return "未找到：$query"
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK).also {
            if (!it) "点击失败" else "已点击：$query"
        }
    }

    private fun performLongPress(query: String): String {
        val node = searchNodeBy(query, rootInActiveWindow, true) ?: return "未找到：$query"
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK).also {
            if (!it) "长按失败" else "已长按：$query"
        }
    }

    private fun performInputText(query: String, text: String): String {
        val node = searchNodeBy(query, rootInActiveWindow, true) ?: return "未找到输入框：$query"
        val bundle = android.os.Bundle().apply { putString(android.view.inputmethod.InputConnectionCompat.INPUT_METHOD_NODE, "") }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle).apply {
            if (!this) {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                sendText(text)
            }
        }.also { "已输入：$text" }
    }

    private fun performGesture(sx: Int, sy: Int, ex: Int, ey: Int): Boolean {
        val gesture = android.view.accessibility.AccessibilityGesture.Event.Builder("tap").apply {
            setGestureStartLocation(sx, sy.toFloat())
            addPointer(android.view.MotionEvent.ACTION_MOVE, listOf(floatArrayOf(ex.toFloat(), ey.toFloat())))
        }.build()
        try {
            serviceInfo = AccessibilityServiceInfo().apply { flags = AccessibilityServiceInfo.FLAG_DEFAULT }
            performGesture(gesture)
            return true
        } catch (e: Exception) {
            // Fallback: 尝试用传统方式模拟
            return false
        }
    }

    private fun sendText(text: String) {
        try {
            val raw = android.hardware.input.InputDevice.SOURCE_KEYBOARD or android.hardware.input.InputDevice.SOURCE_DPAD
            val metaState = 0
            val downTime = SystemClock.uptimeMillis()
            val eventTime = downTime
            val keyboard = android.hardware.input.InputManager.getInstance()
            if (keyboard == null) return
            val keys = text.toCharArray().map { android.view.KeyEvent.KeyCharacterMap.load(0).getKey(it) }
            // Simplified: use key emulation via UiAutomation instead
            uiAutomation?.performKeyEvent(android.view.KeyEvent(downTime, eventTime, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_0, 0))
        } catch (e: Exception) {}
    }

    private fun searchNodeBy(query: String, root: AccessibilityNodeInfo?, allowSearchRoot: Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        val q = query.trim().lowercase()
        if (allowSearchRoot && (q.contains(root.packageName?.replace(".", "")?.take(8) ?: "") || root.text?.lowercase()?.contains(q) == true)) return root
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val cur = queue.poll() ?: break
            if (cur.text?.lowercase()?.contains(q) == true || cur.contentDescription?.lowercase()?.contains(q) == true || cur.className?.contains(q) == true || cur.id?.contains(q) == true) return cur
            for (i in 0 until cur.childCount) queue.addLast(cur.getChild(i))
        }
        return null
    }

    companion object {
        @Volatile var isConnected: Boolean = false
        @Volatile var lastScreenText: String = ""
        @Volatile var lastScreenPackage: String = ""
        @Volatile var lastUpdatedAt: Long = 0L
        @Volatile var lastNotification: String = ""

        fun openSettings(context: android.content.Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
