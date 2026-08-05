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
