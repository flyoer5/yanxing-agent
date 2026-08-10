package com.yanxing.agent.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject
import com.yanxing.agent.service.AIDecisionEngine
import com.yanxing.agent.data.ActionStatus

@Singleton
class ChatRepository @Inject constructor(
    private val groupDao: GroupDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
    private val actionLogDao: ActionLogDao,
) {
    fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { list -> list.map(ConversationEntity::toDomain) }

    suspend fun conversationsSnapshot(): List<Conversation> =
        conversationDao.findAll().map(ConversationEntity::toDomain)

    fun observeGroups(): Flow<List<ConversationGroup>> =
        groupDao.observeAll().map { list -> list.map(GroupEntity::toDomain) }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messageDao.observeForConversation(conversationId).map { list -> list.map(MessageEntity::toDomain) }

    fun observeMemories(): Flow<List<Memory>> =
        memoryDao.observeAll().map { list -> list.map(MemoryEntity::toDomain) }

    suspend fun ensureConversation(id: String, title: String = "新对话") {
        if (conversationDao.findById(id) == null) {
            val now = System.currentTimeMillis()
            conversationDao.upsert(ConversationEntity(id = id, title = title, createdAt = now, updatedAt = now))
        }
    }

    suspend fun createConversation(title: String = "新对话"): String {
        val id = UUID.randomUUID().toString()
        ensureConversation(id, title)
        return id
    }

    suspend fun appendMessage(conversationId: String, role: String, content: String, attachments: List<Attachment> = emptyList()) {
        val now = System.currentTimeMillis()
        val attachmentsJson = JSONArray().apply {
            attachments.forEach { att ->
                put(JSONObject().apply {
                    put("type", att.type)
                    put("uri", att.uri)
                    put("mimeType", att.mimeType)
                    put("name", att.name)
                    put("size", att.size)
                    att.base64?.let { put("base64", it) }
                })
            }
        }.toString()
        messageDao.insert(MessageEntity(UUID.randomUUID().toString(), conversationId, role, content, attachmentsJson, now))
        conversationDao.findById(conversationId)?.let { current ->
            val nextTitle = if (current.title == "新对话" && role == "user") {
                content.take(24).ifBlank { current.title }
            } else current.title
            conversationDao.upsert(current.copy(title = nextTitle, updatedAt = now))
        }
    }

    suspend fun setConversationGroup(conversationId: String, groupId: String?) =
        conversationDao.setGroup(conversationId, groupId)

    /** 编辑消息内容（就地覆盖历史消息文本，保留时间戳；空内容拒绝） */
    suspend fun editMessage(messageId: String, newContent: String): Boolean {
        val content = newContent.trim()
        if (content.isBlank()) return false
        return messageDao.updateContent(messageId, content) > 0
    }

    /** 重命名会话（标题为空时忽略） */
    suspend fun renameConversation(conversationId: String, newTitle: String): Boolean {
        val title = newTitle.trim()
        if (title.isBlank()) return false
        val current = conversationDao.findById(conversationId) ?: return false
        conversationDao.upsert(current.copy(title = title, updatedAt = System.currentTimeMillis()))
        return true
    }

    /** 置顶/取消置顶会话 */
    suspend fun setConversationPinned(conversationId: String, pinned: Boolean): Boolean {
        if (conversationDao.findById(conversationId) == null) return false
        conversationDao.setPinned(conversationId, pinned)
        return true
    }

    /** 归档/取消归档会话 */
    suspend fun setConversationArchived(conversationId: String, archived: Boolean): Boolean {
        if (conversationDao.findById(conversationId) == null) return false
        conversationDao.setArchived(conversationId, archived)
        return true
    }

    suspend fun deleteConversation(id: String) = conversationDao.delete(id)

    /** 按消息内容搜索会话 id 列表（标题搜索之外的补充） */
    suspend fun searchConversationIdsByContent(keyword: String): List<String> =
        messageDao.findConversationsByContent(keyword.trim())

    suspend fun createGroup(name: String) {
        if (name.isNotBlank()) {
            groupDao.upsert(GroupEntity(UUID.randomUUID().toString(), name.trim(), System.currentTimeMillis()))
        }
    }

    suspend fun deleteGroup(id: String) = groupDao.delete(id)

    /** 重命名分组（空名称忽略） */
    suspend fun renameGroup(id: String, newName: String): Boolean {
        val name = newName.trim()
        if (name.isBlank()) return false
        val existing = groupDao.findById(id) ?: return false
        groupDao.upsert(existing.copy(name = name))
        return true
    }

    suspend fun saveMemory(content: String, category: String, sensitive: Boolean = false): Memory {
        val now = System.currentTimeMillis()
        val memory = MemoryEntity(UUID.randomUUID().toString(), content.trim(), category, sensitive, now, now)
        memoryDao.upsert(memory)
        return memory.toDomain()
    }

    suspend fun deleteMemory(id: String) = memoryDao.delete(id)

    /** 更新既有记忆的内容/分类（upsert 覆盖，保留原 id） */
    suspend fun updateMemory(id: String, content: String, category: String): Boolean {
        val existing = memoryDao.findById(id) ?: return false
        if (content.isBlank()) return false
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            content = content.trim(),
            category = category,
            updatedAt = now,
        )
        memoryDao.upsert(updated)
        return true
    }

    suspend fun deleteAllMemories() = memoryDao.deleteAll()

    // ===== 操作日志管理 =====

    fun observeActionLogs(): Flow<List<ActionLogEntity>> =
        actionLogDao.observeAll().map { list -> list.sortedByDescending { it.timestamp } }

    fun observeActionLogsByPackage(packageName: String): Flow<List<ActionLogEntity>> =
        actionLogDao.observeForPackage(packageName)

    suspend fun addActionLog(
        packageName: String,
        actionType: String,
        targetElement: String?,
        details: String,
        status: ActionStatus,
        errorMessage: String? = null,
    ) {
        val log = ActionLogEntity(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            actionType = actionType,
            targetElement = targetElement?.take(200),
            details = details.take(1000),
            status = when (status) {
                is ActionStatus.Completed -> if (status.successCount == status.totalCount) "success" else "failed"
                is ActionStatus.Executing -> "running"
                else -> "unknown"
            },
            errorMessage = errorMessage,
        )
        actionLogDao.insert(log)
    }

    /** 批量插入日志（性能优化） */
    suspend fun addBatchActionLogs(logs: List<ActionLogEntity>) {
        actionLogDao.insertAll(logs)
    }

    suspend fun deleteActionLog(id: String) = actionLogDao.delete(id)
    suspend fun deleteAllActionLogs() = actionLogDao.deleteAll()
    suspend fun deleteActionLogsByPackage(packageName: String) = actionLogDao.deleteByPackage(packageName)

    suspend fun messagesForRequest(conversationId: String): List<ChatMessage> =
        messageDao.findForConversation(conversationId).map(MessageEntity::toDomain)
}

data class Conversation(
    val id: String,
    val title: String,
    val groupId: String?,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val updatedAt: Long,
)

/** 会话置顶优先排序（置顶在前，内部按更新时间倒序；顶层纯函数可单测） */
fun sortConversations(conversations: List<Conversation>): List<Conversation> =
    conversations.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })

data class ConversationGroup(
    val id: String,
    val name: String,
)

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
)

/** 角色 → 中文标签 */
fun roleLabel(role: String): String = when (role) {
    "user" -> "我"
    "assistant" -> "言行"
    "system" -> "系统"
    else -> role
}

/**
 * 将当前会话格式化为可导出的纯文本（无 Android 依赖，可单测）。
 * 格式：会话标题 → 消息列表（角色 + 内容），图片/文件附件单独标注。
 */
fun formatConversation(title: String, messages: List<ChatMessage>): String {
    if (messages.isEmpty()) return "（空会话）"
    val safeTitle = title.trim().ifBlank { "未命名会话" }
    return buildString {
        appendLine("会话：$safeTitle")
        appendLine("共 ${messages.size} 条消息")
        appendLine("=".repeat(32))
        messages.forEach { message ->
            val role = roleLabel(message.role)
            val attachmentNote = if (message.attachments.isEmpty()) "" else
                " [附件 ${message.attachments.size} 个]"
            appendLine("【$role】$attachmentNote")
            if (message.content.isNotBlank()) {
                appendLine(message.content)
            }
            appendLine("-".repeat(24))
        }
    }.trimEnd()
}

sealed class ActionStatus {
    data object Idle : ActionStatus()
    data object Readying : ActionStatus()
    data class Ready(val screenText: String) : ActionStatus()
    data class Thinking(val round: Int) : ActionStatus() // AI 正在根据执行结果做下一轮决策
    data class Executing(
        val current: Int,
        val total: Int,
        val actionDesc: String? = null,
        val confirmed: Boolean = false, // 是否已确认执行当前动作
        val userApproved: Boolean = true, // 用户是否批准（true=允许，false=拒绝）
    ) : ActionStatus()

    data class Completed(val successCount: Int, val totalCount: Int) : ActionStatus()

    // 用于 Pending Actions 的中间状态
    sealed class PendingConfirm : ActionStatus() {
        data class Waiting(val actions: List<AIDecisionEngine.Action>, val index: Int) : PendingConfirm()
        data object Canceled : PendingConfirm()
    }
}

data class Memory(
    val id: String,
    val content: String,
    val category: String,
    val isSensitive: Boolean,
    val updatedAt: Long,
)

/**
 * 将长期记忆列表格式化为可导出的纯文本（无 Android 依赖，可单测）。
 * 格式：标题 → 条数 → 每条内容 + 分类（敏感记忆标注）。
 */
fun formatMemories(memories: List<Memory>): String {
    if (memories.isEmpty()) return "暂无长期记忆"
    return buildString {
        appendLine("言行 Agent 长期记忆")
        appendLine("共 ${memories.size} 条记忆")
        appendLine("=".repeat(32))
        memories.forEachIndexed { index, memory ->
            appendLine("[${index + 1}] ${memory.content.trim().ifBlank { "（无内容）" }}")
            appendLine("分类：${memory.category}")
            if (memory.isSensitive) appendLine("⚠️ 敏感记忆")
            appendLine("-".repeat(24))
        }
    }.trimEnd()
}

private fun ConversationEntity.toDomain() = Conversation(id, title, groupId, pinned, archived, updatedAt)
private fun GroupEntity.toDomain() = ConversationGroup(id, name)
private fun MessageEntity.toDomain(): ChatMessage {
    val atts = mutableListOf<Attachment>()
    try {
        val jsonArray = JSONArray(attachments)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            atts.add(Attachment(
                type = obj.optString("type"),
                uri = obj.optString("uri"),
                mimeType = obj.optString("mimeType"),
                name = obj.optString("name"),
                size = obj.optLong("size"),
                base64 = if (obj.has("base64")) obj.getString("base64") else null,
            ))
        }
    } catch (_: Exception) { /* ignore parse errors */ }
    return ChatMessage(id, role, content, atts)
}
private fun MemoryEntity.toDomain() = Memory(id, content, category, isSensitive, updatedAt)
