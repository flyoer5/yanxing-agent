package com.yanxing.agent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import java.util.ArrayDeque

/** 安全的无障碍操作封装 - 增强版：智能搜索 + 自动重试 */
object ActionExecutor {
    data class ActionResult(
        val success: Boolean,
        val message: String,
        val foundDesc: String? = null,
        val retryCount: Int = 0, // 记录重试次数
    )

    // ===== 配置参数 =====
    private const val MAX_RETRY_COUNT = 3       // 最大重试次数
    private const val RETRY_DELAY_MS = 200L     // 重试间隔（毫秒）
    private const val NODE_DEPTH_LIMIT = 50     // 最大搜索深度
    
    /** 相似度阈值 (0-1) */
    private const val HIGH_SIMILARITY_THRESHOLD = 0.9f
    private const val MEDIUM_SIMILARITY_THRESHOLD = 0.7f

    fun extractText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val out = StringBuilder()
        collectText(node, out, 0)
        return out.toString().trim()
    }

    // ===== 点击操作（带重试）=====
    suspend fun click(query: String): ActionResult = withRetrySupport {
        withService { service ->
            val node = findSmartNode(service.rootInActiveWindow, query)
                ?: return@withService ActionResult(false, "未找到元素：$query")

            val clicked = performSafeClick(node)
            ActionResult(
                clicked,
                if (clicked) "已点击：${node.text ?: node.contentDescription ?: query}" else "点击失败：$query",
                node.contentDescription?.toString(),
                0,
            )
        }
    }

    // ===== 长按操作（带重试）=====
    suspend fun longPress(query: String): ActionResult = withRetrySupport {
        withService { service ->
            val node = findSmartNode(service.rootInActiveWindow, query)
                ?: return@withService ActionResult(false, "未找到元素：$query")

            val pressed = performSafeLongPress(node)
            ActionResult(
                pressed,
                if (pressed) "已长按：$query" else "长按失败：$query",
                null,
                0,
            )
        }
    }

    // ===== 滑动操作（单次执行，不重试）=====
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

    // ===== 全局返回（撤销当前页面）=====
    suspend fun back(): ActionResult = withService { service ->
        // performGlobalAction 是无障碍服务的内置能力，无需额外权限
        // GLOBAL_ACTION_BACK 会触发系统级返回动作（适用于多数应用）
        val result = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        ActionResult(result, if (result) "已执行返回" else "返回操作不可用")
    }

    // ===== 清空输入框文本 =====
    suspend fun clearText(query: String): ActionResult = withRetrySupport {
        withService { service ->
            val node = findSmartNode(service.rootInActiveWindow, query)
                ?: return@withService ActionResult(false, "未找到输入框：$query")

            val focused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (!focused) return@withService ActionResult(false, "无法聚焦输入框：$query")

            val changed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            })
            ActionResult(
                changed,
                if (changed) "已清空输入框：${node.text ?: query}" else "清空失败：$query",
                null,
                0,
            )
        }
    }

    // ===== 文本输入（带重试）=====
    suspend fun inputText(query: String, text: String): ActionResult = withRetrySupport {
        withService { service ->
            val node = findSmartNode(service.rootInActiveWindow, query)
                ?: return@withService ActionResult(false, "未找到输入框：$query")

            val focused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (!focused) return@withService ActionResult(false, "无法聚焦输入框：$query")

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val changed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            ActionResult(
                changed,
                if (changed) "已输入文本到：${node.text ?: query}" else "输入失败：$query",
                null,
                0,
            )
        }
    }

    // ===== 重试包装器（协程版，响应取消）=====
    suspend fun withRetrySupport(operation: suspend () -> ActionResult): ActionResult {
        var lastResult = operation()
        var attempt = 1
        
        while (!lastResult.success && attempt < MAX_RETRY_COUNT) {
            attempt++
            delay(RETRY_DELAY_MS) // 等待界面稳定，可被取消
            
            // 每次重试重新查找元素（可能之前的节点已失效）
            lastResult = operation()
            
            // 如果重试成功，返回成功结果
            if (lastResult.success) break
        }
        
        return lastResult
    }

    // ===== 安全点击（带延迟保护）=====
    private suspend fun performSafeClick(node: AccessibilityNodeInfo): Boolean {
        // 轻微延迟，确保 UI 状态稳定（可被取消）
        delay(50)
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // ===== 安全长按（带延迟保护）=====
    private suspend fun performSafeLongPress(node: AccessibilityNodeInfo): Boolean {
        delay(50)
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    // ===== 与服务的交互 =====
    private suspend fun withService(block: (ScreenReaderAccessibilityService) -> ActionResult): ActionResult {
        val service = ScreenReaderAccessibilityService.instance
            ?: return ActionResult(false, "无障碍服务未开启")
        return block(service)
    }

    // ===== 智能搜索算法（核心改进）=====
    
    /**
     * 增强版的节点查找方法
     * 策略优先级：
     * 1. 精确匹配（完全相等）
     * 2. 唯一关键词匹配（简短且唯一的词如"设置""保存"）
     * 3. 相似度最高的候选（使用 Levenshtein 距离）
     * 4. 降级为普通模糊匹配
     */
    private fun findSmartNode(root: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (root == null || query.isBlank()) return null
        
        val q = query.trim().lowercase()
        
        // 第一步：尝试精确匹配
        val exactMatch = traverseTree(root, q) { text, desc, id, fullQuery ->
            text.equals(fullQuery, ignoreCase = true) ||
                desc.equals(fullQuery, ignoreCase = true) ||
                id.contains(fullQuery)
        }
        if (exactMatch != null) return exactMatch
        
        // 第二步：尝试唯一关键词匹配（适用于简短且唯一的词）
        val uniqueMatch = findUniqueKeywordNode(root, q)
        if (uniqueMatch != null) return uniqueMatch
        
        // 第三步：相似度评分最高的候选
        val bestMatch = findBestSimilarityMatch(root, q)
        if (bestMatch != null) return bestMatch
        
        // 第四步：降级为普通模糊匹配
        return traverseTree(root, q) { text, desc, id, fullQuery ->
            text.contains(fullQuery) || desc.contains(fullQuery) || id.contains(fullQuery)
        }
    }

    /**
     * 查找唯一关键词匹配的节点
     * 适用于像"设置""保存""取消"这样简短且唯一的词
     */
    private fun findUniqueKeywordNode(root: AccessibilityNodeInfo?, keyword: String): AccessibilityNodeInfo? {
        val candidates = mutableListOf<Pair<AccessibilityNodeInfo, Float>>()
        traverseTreeExhaustive(root, keyword.lowercase()) { node, text, desc, _, _ ->
            // 完全匹配优先级最高
            if (text.equals(keyword, ignoreCase = true)) {
                candidates.add(Pair(node, 1.0f))
            } else if (desc.equals(keyword, ignoreCase = true)) {
                candidates.add(Pair(node, 0.98f))
            } else if (text.equals(keyword, ignoreCase = true) || desc.equals(keyword, ignoreCase = true)) {
                candidates.add(Pair(node, 0.95f))
            }
        }
        
        // 根据候选数量选择最佳节点
        return when {
            candidates.isEmpty() -> null
            candidates.size == 1 -> candidates[0].first // 只有一个候选，直接返回
            else -> {
                // 多个候选，选择相似度最高的第一个
                candidates.maxByOrNull { it.second }?.first
            }
        }
    }

    /**
     * 基于相似度的最佳匹配
     * 使用 Levenshtein 距离计算相似度
     */
    private fun findBestSimilarityMatch(root: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        var bestMatch: AccessibilityNodeInfo? = null
        var highestScore = 0.0f
        
        traverseTreeExhaustive(root, query) { node, text, desc, _, _ ->
            val textScore = calculateSimilarity(query, text.lowercase())
            val descScore = calculateSimilarity(query, desc.lowercase())
            val score = maxOf(textScore, descScore)

            if (score > highestScore && score >= HIGH_SIMILARITY_THRESHOLD) {
                highestScore = score
                bestMatch = node
            }
        }
        
        return bestMatch
    }

    /**
     * 计算两个字符串的相似度 (0-1)
     * 使用 Levenshtein Distance 算法
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        
        val smaller = if (s1.length < s2.length) s1 else s2
        val larger = if (s1.length >= s2.length) s1 else s2
        
        val previousRow = IntArray(smaller.length + 1) { it }

        for (i in larger.indices) {
            val currentRow = IntArray(smaller.length + 1)
            currentRow[0] = i + 1

            for (j in smaller.indices) {
                val insertions = currentRow[j] + 1
                val deletions = previousRow[j + 1] + 1
                val substitutions = previousRow[j] + if (smaller[j] == larger[i]) 0 else 1
                currentRow[j + 1] = minOf(insertions, deletions, substitutions)
            }

            for (index in currentRow.indices) previousRow[index] = currentRow[index]
        }

        val distance = previousRow[smaller.length]
        return 1.0f - (distance.toDouble() / larger.length).toFloat()
    }

    // ===== 树遍历工具 =====
    
    /**
     * 树遍历并返回第一个匹配的节点
     */
    private fun traverseTree(root: AccessibilityNodeInfo?, query: String, matcher: (String, String, String, String) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            
            // 跳过不可见的节点
            if (!isVisibleAndClickable(node)) continue
            
            // 检查文本、描述、ID
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            
            if (matcher(text, desc, id, query)) return node
            
            // 添加子节点（限制深度）
            var depth = 0
            var tempNode = node.parent
            while (tempNode != null) {
                tempNode = tempNode.parent
                depth++
            }
            
            if (depth <= NODE_DEPTH_LIMIT) {
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let {
                        queue.addLast(it)
                    }
                }
            }
        }
        
        return null
    }

    /**
     * 完整遍历所有节点，对每个节点调用回调函数
     */
    private fun traverseTreeExhaustive(root: AccessibilityNodeInfo?, query: String, callback: (AccessibilityNodeInfo, String, String, String, String) -> Unit) {
        if (root == null) return
        
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            
            if (!isVisibleAndClickable(node)) continue
            
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            val id = node.viewIdResourceName?.toString().orEmpty()
            
            callback(node, text, desc, id, query)
            
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
    }

    /**
     * 判断节点是否可见且可交互
     */
    private fun isVisibleAndClickable(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser && (!node.isEnabled || !node.isFocusable)) return false
        if (node.isVisibleToUser) return true
        
        // 跳过对话框和 Toast
        return !node.className.endsWith(".Dialog") && 
               !node.className.endsWith(".Toast") &&
               node.isEnabled &&
               node.isFocusable
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 30) return
        node.text?.toString()?.takeIf(String::isNotBlank)?.let { out.append(it).append('\n') }
        node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let { out.append("【描述】").append(it).append('\n') }
        for (index in 0 until node.childCount) node.getChild(index)?.let { collectText(it, out, depth + 1) }
    }
}
