package com.yanxing.agent.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun appendMessage(conversationId: String, role: String, content: String) {
        val now = System.currentTimeMillis()
        messageDao.insert(MessageEntity(UUID.randomUUID().toString(), conversationId, role, content, now))
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
)

data class Memory(
    val id: String,
    val content: String,
    val category: String,
    val isSensitive: Boolean,
    val updatedAt: Long,
)

private fun ConversationEntity.toDomain() = Conversation(id, title, groupId, updatedAt)
private fun GroupEntity.toDomain() = ConversationGroup(id, name)
private fun MessageEntity.toDomain() = ChatMessage(id, role, content)
private fun MemoryEntity.toDomain() = Memory(id, content, category, isSensitive, updatedAt)
