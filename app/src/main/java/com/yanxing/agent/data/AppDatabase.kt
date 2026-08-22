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
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "messages", indices = [androidx.room.Index("conversationId")])
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

    @Query("SELECT * FROM groups WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    suspend fun findAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET groupId = :groupId WHERE id = :conversationId")
    suspend fun setGroup(conversationId: String, groupId: String?)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :conversationId")
    suspend fun setPinned(conversationId: String, pinned: Boolean)

    @Query("UPDATE conversations SET archived = :archived WHERE id = :conversationId")
    suspend fun setArchived(conversationId: String, archived: Boolean)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun setTimestampAndTitle(conversationId: String, title: String, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    /** 删除会话及其全部消息（孤儿消息会导致搜索结果错乱，必须级联清理） */
    @androidx.room.Transaction
    suspend fun deleteWithMessages(id: String) {
        deleteMessagesForConversation(id)
        delete(id)
    }
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun findForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT DISTINCT conversationId FROM messages WHERE content LIKE '%' || :keyword || '%'")
    suspend fun findConversationsByContent(keyword: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String): Int

    /** 旧版本把附件 base64 写进了库，启动时一次性清洗用 */
    @Query("SELECT * FROM messages WHERE attachments LIKE '%\"base64\"%'")
    suspend fun findMessagesWithLegacyBase64(): List<MessageEntity>

    @Query("UPDATE messages SET attachments = :attachments WHERE id = :id")
    suspend fun updateAttachments(id: String, attachments: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}

@Dao
interface ActionLogDao {
    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC LIMIT 500")
    fun observeAll(): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_logs WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun observeForPackage(packageName: String): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_logs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ActionLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActionLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ActionLogEntity>)

    @Query("DELETE FROM action_logs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM action_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM action_logs WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}

@Database(
    entities = [GroupEntity::class, ConversationEntity::class, MessageEntity::class, MemoryEntity::class, ActionLogEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun actionLogDao(): ActionLogDao

    companion object {
        /** v5 → v6：messages.conversationId 建索引（按会话查询/订阅避免全表扫描） */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_conversationId ON messages(conversationId)")
            }
        }

        /** v4 → v5：conversations 表新增 archived 归档标记（默认 0） */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4：conversations 表新增 pinned 置顶标记（默认 0） */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
