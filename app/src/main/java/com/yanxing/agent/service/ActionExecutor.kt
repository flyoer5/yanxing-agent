package com.yanxing.agent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/** 安全的无障碍操作封装。实际服务实例由 ScreenReaderAccessibilityService 持有。 */
object ActionExecutor {
    data class ActionResult(
        val success: Boolean,
        val message: String,
        val foundDesc: String? = null,
    )

    fun extractText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val out = StringBuilder()
        collectText(node, out, 0)
        return out.toString().trim()
    }

    fun click(query: String): ActionResult = withService { service ->
        val node = findNode(service.rootInActiveWindow, query)
            ?: return@withService ActionResult(false, "未找到元素：$query")
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        ActionResult(clicked, if (clicked) "已点击：$query" else "点击失败：$query", node.contentDescription?.toString())
    }

    fun longPress(query: String): ActionResult = withService { service ->
        val node = findNode(service.rootInActiveWindow, query)
            ?: return@withService ActionResult(false, "未找到元素：$query")
        val pressed = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        ActionResult(pressed, if (pressed) "已长按：$query" else "长按失败：$query")
    }

    fun swipe(direction: AIDecisionEngine.SwipeDirection): ActionResult = withService { service ->
        val metrics = service.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val path = Path()
        val cx = width / 2f
        val cy = height / 2f
        when (direction) {
            AIDecisionEngine.SwipeDirection.UP -> { path.moveTo(cx, height * .75f); path.lineTo(cx, height * .25f) }
            AIDecisionEngine.SwipeDirection.DOWN -> { path.moveTo(cx, height * .25f); path.lineTo(cx, height * .75f) }
            AIDecisionEngine.SwipeDirection.LEFT -> { path.moveTo(width * .75f, cy); path.lineTo(width * .25f, cy) }
            AIDecisionEngine.SwipeDirection.RIGHT -> { path.moveTo(width * .25f, cy); path.lineTo(width * .75f, cy) }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 350L))
            .build()
        val accepted = service.dispatchGesture(gesture, null, null)
        ActionResult(accepted, if (accepted) "已滑动 ${direction.name}" else "滑动失败")
    }

    fun inputText(query: String, text: String): ActionResult = withService { service ->
        val node = findNode(service.rootInActiveWindow, query)
            ?: return@withService ActionResult(false, "未找到输入框：$query")
        val focused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (!focused) return@withService ActionResult(false, "无法聚焦输入框：$query")
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val changed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        ActionResult(changed, if (changed) "已输入文本" else "输入失败")
    }

    private fun withService(block: (ScreenReaderAccessibilityService) -> ActionResult): ActionResult {
        val service = ScreenReaderAccessibilityService.instance
            ?: return ActionResult(false, "无障碍服务未开启")
        return block(service)
    }

    private fun findNode(root: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val q = query.trim().lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            if (text.contains(q) || desc.contains(q) || id.contains(q)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 30) return
        node.text?.toString()?.takeIf(String::isNotBlank)?.let { out.append(it).append('\n') }
        node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let { out.append("【描述】").append(it).append('\n') }
        for (index in 0 until node.childCount) node.getChild(index)?.let { collectText(it, out, depth + 1) }
    }
}
