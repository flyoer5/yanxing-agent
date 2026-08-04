package com.yanxing.agent.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {
    fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { list -> list.map(ConversationEntity::toDomain) }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messageDao.observeForConversation(conversationId).map { list -> list.map(MessageEntity::toDomain) }

    suspend fun ensureConversation(id: String, title: String = "新对话") {
        if (conversationDao.findById(id) == null) {
            val now = System.currentTimeMillis()
            conversationDao.upsert(ConversationEntity(id, title, now, now))
        }
    }

    suspend fun appendMessage(conversationId: String, role: String, content: String) {
        val now = System.currentTimeMillis()
        messageDao.insert(MessageEntity(UUID.randomUUID().toString(), conversationId, role, content, now))
        val current = conversationDao.findById(conversationId)
        if (current != null) {
            conversationDao.upsert(current.copy(updatedAt = now))
        }
    }
}

data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
)

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
)

private fun ConversationEntity.toDomain() = Conversation(id, title, updatedAt)
private fun MessageEntity.toDomain() = ChatMessage(id, role, content)
