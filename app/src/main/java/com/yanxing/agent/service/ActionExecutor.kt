package com.yanxing.agent.service

import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedList
import com.yanxing.agent.service.AIDecisionEngine.SwipeDirection

/**
 * ActionExecutor v2 - 基于无障碍服务的自动化执行引擎
 * 支持：点击/长按/滑动/文本输入 + AI 驱动的决策
 */
object ActionExecutor {

    private var service: ScreenReaderAccessibilityService? = null

    /**
     * 初始化（需在主线程调用）
     */
    fun initialize(context: android.content.Context) {
        // TODO: 通过 AccessibilityManager 获取实例
    }

    /**
     * 刷新当前窗口 UI 树
     */
    fun refreshScreen(): String {
        service ?: return "未开启无障碍服务"
        val root = service?.rootInActiveWindow ?: return "无法读取界面"
        val pkgName = service?.lastScreenPackage ?: "未知"
        val text = extractText(root).take(800)
        return "当前界面：$pkgName\n内容:\n$text"
    }

    /**
     * 查找元素并点击
     */
    fun click(query: String): ActionResult {
        service ?: return ActionResult(false, "未开启无障碍服务")
        val target = findElement(service?.rootInActiveWindow, query)
            ?: return ActionResult(false, "未找到元素：$query")
        val success = target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        return ActionResult(success, if (success) "已点击：$query" else "点击失败：$query", target.contentDescription.toString())
    }

    /**
     * 长按元素
     */
    fun longPress(query: String): ActionResult {
        service ?: return ActionResult(false, "未开启无障碍服务")
        val target = findElement(service?.rootInActiveWindow, query)
            ?: return ActionResult(false, "未找到元素：$query")
        val success = target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK)
        return ActionResult(success, if (success) "已长按：$query" else "长按失败：$query")
    }

    /**
     * 滑动页面（方向：UP/DOWN/LEFT/RIGHT）
     */
    fun swipe(direction: SwipeDirection, progressRatio: Float = 0.3f): ActionResult {
        service ?: return ActionResult(false, "未开启无障碍服务")
        val metrics = service?.displayMetrics ?: return ActionResult(false, "无法获取显示信息")
        val bounds = android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        
        val (sx, sy, ex, ey) = when (direction) {
            SwipeDirection.UP -> Pair(bounds.centerX(), bounds.centerY()) to Pair(bounds.centerX(), (bounds.top + bounds.centerY()) * 0.7f)
            SwipeDirection.DOWN -> Pair(bounds.centerX(), bounds.centerY()) to Pair(bounds.centerX(), (bounds.bottom + bounds.centerY()) * 0.7f)
            SwipeDirection.LEFT -> Pair(bounds.right.toFloat(), bounds.centerY()) to Pair((bounds.left + bounds.right) * 0.3f, bounds.centerY())
            SwipeDirection.RIGHT -> Pair((bounds.left + bounds.right) * 0.3f, bounds.centerY()) to Pair(bounds.right.toFloat(), bounds.centerY())
        }
        
        val success = performGesture(sx.toInt(), sy.toInt(), ex.toInt(), ey.toInt())
        return ActionResult(success, if (success) "已滑动 ${direction.name}" else "滑动失败")
    }

    /**
     * 输入文本到指定输入框
     */
    fun inputText(query: String, text: String): ActionResult {
        service ?: return ActionResult(false, "未开启无障碍服务")
        val target = findElement(service?.rootInActiveWindow, query)
            ?: return ActionResult(false, "未找到输入框：$query")
        
        // 尝试通过焦点+发送按键事件
        val focusSuccess = target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
        val result = if (focusSuccess) {
            injectKeyEvents(text)
            true
        } else false
        
        return ActionResult(result, if (result) "已输入：$text" else "输入失败：无法聚焦输入框")
    }

    // ===================== 内部工具 =====================

    private fun findElement(node: AccessibilityNodeInfo?, query: String, maxDepth: Int = 50): AccessibilityNodeInfo? {
        node ?: return null
        val q = query.lowercase()
        
        // 检查当前节点
        if (node.text?.lowercase()?.contains(q) == true || 
            node.contentDescription?.lowercase()?.contains(q) == true ||
            node.className?.contains(q) == true ||
            (q.contains("button") && (node.isClickable || node.isEnabled)) ||
            (q.contains("input") && (node.isEditable || node.isEnabled))) {
            return node
        }
        
        // BFS 搜索（避免过深递归）
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.addLast(node)
        
        for (depth in 1..maxDepth) {
            repeat(queue.size) {
                val cur = queue.pollFirst() ?: return@repeat
                for (i in 0 until cur.childCount) {
                    val child = cur.getChild(i) ?: return@repeat
                    if (child.text?.lowercase()?.contains(q) == true || 
                        child.contentDescription?.lowercase()?.contains(q) == true) {
                        return child
                    }
                    queue.addLast(child)
                }
            }
        }
        return null
    }

    private fun performGesture(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        // 使用 AccessibilityGesture.Event API（API 29+）
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            // 降级：简单延迟模拟
            Thread.sleep(100)
            return false
        }
        
        try {
            val gesture = android.view.accessibility.AccessibilityGesture.Event.Builder("swipe")
                .setGestureStartLocation(x1.toFloat(), y1.toFloat())
                .addPoint(android.view.MotionEvent.ACTION_MOVE, listOf(floatArrayOf(x2.toFloat(), y2.toFloat())))
                .build()
            service?.performGesture(gesture)
            return true
        } catch (e: Exception) {
            // Fallback
            return false
        }
    }

    private fun injectKeyEvents(text: String) {
        // 简化：通过 UiAutomation 发送按键序列
        // 实际需要在无障碍服务中持有 UiAutomation 实例
    }

    fun extractText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        buildText(node, sb, depth = 0)
        return sb.toString().trim()
    }

    private fun buildText(node: AccessibilityNodeInfo, builder: StringBuilder, depth: Int) {
        if (depth > 30) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append("\n") }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append("【描述】").append(it).append("\n") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { buildText(it, builder, depth + 1) }
        }
    }

    data class ActionResult(val success: Boolean, val message: String, val foundDesc: String? = null) {
        companion object {
            fun Success(msg: String, foundDesc: String? = null) = ActionResult(true, msg, foundDesc)
            fun Error(msg: String) = ActionResult(false, msg)
        }
    }


    sealed class ActionType {
        data class CLICK(val query: String) : ActionType()
        data class LONG_PRESS(val query: String) : ActionType()
        data class SWIPE(val direction: SwipeDirection) : ActionType()
        data class INPUT_TEXT(val query: String, val text: String) : ActionType()
    }
