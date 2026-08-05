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

@Singleton
class ChatRepository @Inject constructor(
    private val groupDao: GroupDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
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
            conversationDao.upsert(ConversationEntity(id, title, null, now, now))
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

    suspend fun deleteConversation(id: String) = conversationDao.delete(id)

    suspend fun createGroup(name: String) {
        if (name.isNotBlank()) {
            groupDao.upsert(GroupEntity(UUID.randomUUID().toString(), name.trim(), System.currentTimeMillis()))
        }
    }

    suspend fun deleteGroup(id: String) = groupDao.delete(id)

    suspend fun saveMemory(content: String, category: String, sensitive: Boolean = false): Memory {
        val now = System.currentTimeMillis()
        val memory = MemoryEntity(UUID.randomUUID().toString(), content.trim(), category, sensitive, now, now)
        memoryDao.upsert(memory)
        return memory.toDomain()
    }

    suspend fun deleteMemory(id: String) = memoryDao.delete(id)

    suspend fun deleteAllMemories() = memoryDao.deleteAll()

    suspend fun messagesForRequest(conversationId: String): List<ChatMessage> =
        messageDao.findForConversation(conversationId).map(MessageEntity::toDomain)
}

data class Conversation(
    val id: String,
    val title: String,
    val groupId: String?,
    val updatedAt: Long,
)

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

sealed class ActionStatus {
    data object Idle : ActionStatus()
    data object Readying : ActionStatus()
    data class Ready(val screenText: String) : ActionStatus()
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

private fun ConversationEntity.toDomain() = Conversation(id, title, groupId, updatedAt)
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
