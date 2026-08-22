package com.yanxing.agent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 操作日志记录
 */
@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,           // 执行时间戳
    val packageName: String,       // 目标应用包名
    val actionType: String,        // 动作类型："click", "swipe", "input_text", "input_key"
    val targetElement: String?,    // 目标元素描述（可选）
    val details: String,           // 详细操作内容
    val status: String,            // 结果状态："success", "failed", "cancelled"
    val errorMessage: String? = null,
)

/** 动作类型 → 中文标签 */
fun actionTypeLabel(type: String): String = when (type) {
    "click" -> "点击"
    "long_press" -> "长按"
    "swipe" -> "滑动"
    "input_text" -> "输入文本"
    "input_key" -> "按键"
    "rollback" -> "回滚"
    "back" -> "返回"
    "clear_text" -> "清空输入"
    else -> "操作"
}

/** 结果状态 → 中文标签 */
fun actionStatusLabel(status: String): String = when (status) {
    "success" -> "成功"
    "failed" -> "失败"
    "cancelled" -> "已取消"
    else -> "未知"
}

/**
 * 运行时 ActionStatus → 落库状态字符串（唯一权威映射）。
 * 此前 ChatRepository 与 BatchedLogWriter 各自维护一份 when，
 * 已出现分叉（取消被记成 unknown），统一收敛到这里。
 */
fun ActionStatus.toLogStatusLabel(): String = when (this) {
    is ActionStatus.Completed -> if (successCount == totalCount) "success" else "failed"
    is ActionStatus.Executing -> "running"
    is ActionStatus.PendingConfirm.Canceled -> "cancelled"
    else -> "unknown"
}

/**
 * 将操作日志列表格式化为可导出的纯文本。
 * 无 Android 依赖，纯函数可单测。
 */
fun formatActionLogs(logs: List<ActionLogEntity>): String {
    if (logs.isEmpty()) return "暂无操作日志"
    return buildString {
        appendLine("言行 Agent 操作日志")
        appendLine("共 ${logs.size} 条记录")
        appendLine("=".repeat(32))
        logs.forEachIndexed { index, log ->
            appendLine("[${index + 1}]")
            appendLine("时间：${formatLogTimestamp(log.timestamp)}")
            appendLine("应用：${log.packageName}")
            appendLine("动作：${actionTypeLabel(log.actionType)}")
            log.targetElement?.takeIf { it.isNotBlank() }?.let { appendLine("目标：$it") }
            if (log.details != log.targetElement && log.details.isNotBlank()) {
                appendLine("详情：${log.details}")
            }
            appendLine("状态：${actionStatusLabel(log.status)}")
            log.errorMessage?.takeIf { it.isNotBlank() }?.let { appendLine("错误：$it") }
            appendLine("-".repeat(24))
        }
    }.trimEnd()
}

/** 时间戳 → "yyyy-MM-dd HH:mm:ss" 可读文本（与导出格式统一，便于测试） */
fun formatLogTimestamp(timestamp: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))

/** 时间戳 → 今天 HH:mm，跨天 M-d HH:mm（纯 JVM 可测）。 */
fun formatMessageTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val nowCalendar = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val messageCalendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = nowCalendar.get(java.util.Calendar.YEAR) == messageCalendar.get(java.util.Calendar.YEAR) &&
        nowCalendar.get(java.util.Calendar.DAY_OF_YEAR) == messageCalendar.get(java.util.Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "M-d HH:mm"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}

/** 消息状态摘要：统一复制反馈与无障碍语义文案。 */
fun formatMessageStatus(
    createdAt: Long,
    isEdited: Boolean,
    now: Long = System.currentTimeMillis(),
): String = buildString {
    if (createdAt > 0L) {
        append("发送时间 ")
        append(formatMessageTime(createdAt, now))
    }
    if (isEdited) {
        if (isNotEmpty()) append("，")
        append("已编辑")
    }
}
fun formatLogTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val logCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = cal.get(java.util.Calendar.YEAR) == logCal.get(java.util.Calendar.YEAR) &&
        cal.get(java.util.Calendar.DAY_OF_YEAR) == logCal.get(java.util.Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm:ss" else "M-d HH:mm"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}
