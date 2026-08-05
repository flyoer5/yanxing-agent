package com.yanxing.agent.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val groupId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val attachments: String = "[]", // JSON array of Attachment
    val createdAt: Long,
)

/**
 * 多模态附件：图片、文件、语音
 * type: "image" | "file" | "audio"
 * uri: 本地文件路径
 * mimeType: MIME 类型
 * name: 文件名
 * size: 文件大小（字节）
 * base64: base64 编码内容（用于 API 请求）
 */
data class Attachment(
    val type: String,      // "image" | "file" | "audio"
    val uri: String,       // 本地 URI
    val mimeType: String,  // image/jpeg, application/pdf, etc.
    val name: String,      // 文件名
    val size: Long = 0,    // 文件大小
    val base64: String? = null, // base64 编码（可选，用于 API）
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val category: String,
    val isSensitive: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun findAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET groupId = :groupId WHERE id = :conversationId")
    suspend fun setGroup(conversationId: String, groupId: String?)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun findForConversation(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}

@Dao
interface ActionLogDao {
    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_logs WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun observeForPackage(packageName: String): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_logs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ActionLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActionLogEntity)

    @Query("DELETE FROM action_logs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM action_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM action_logs WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}

@Database(
    entities = [GroupEntity::class, ConversationEntity::class, MessageEntity::class, MemoryEntity::class, ActionLogEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun actionLogDao(): ActionLogDao
}
