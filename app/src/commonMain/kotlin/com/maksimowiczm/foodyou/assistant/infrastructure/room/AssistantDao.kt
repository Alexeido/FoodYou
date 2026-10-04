package com.maksimowiczm.foodyou.assistant.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AssistantDao {

    @Insert abstract suspend fun insertChange(change: AssistantChangeEntity): Long

    @Query("SELECT * FROM AssistantChange ORDER BY id DESC LIMIT :limit")
    abstract suspend fun recentChanges(limit: Int): List<AssistantChangeEntity>

    @Query("SELECT * FROM AssistantChange ORDER BY id DESC LIMIT :limit")
    abstract fun observeRecentChanges(limit: Int): Flow<List<AssistantChangeEntity>>

    @Query("SELECT * FROM AssistantChange WHERE id = :id")
    abstract suspend fun changeById(id: Long): AssistantChangeEntity?

    @Query(
        "SELECT * FROM AssistantChange WHERE conversationId = :conversationId ORDER BY id DESC LIMIT :limit"
    )
    abstract suspend fun recentChangesForConversation(
        conversationId: Long,
        limit: Int,
    ): List<AssistantChangeEntity>

    @Query("SELECT * FROM AssistantChange WHERE undone = 0 ORDER BY id DESC LIMIT 1")
    abstract suspend fun lastAppliedChange(): AssistantChangeEntity?

    @Query("SELECT * FROM AssistantChange WHERE undone = 1 ORDER BY id DESC LIMIT 1")
    abstract suspend fun lastUndoneChange(): AssistantChangeEntity?

    @Query("UPDATE AssistantChange SET undone = :undone, redoPayload = :redoPayload WHERE id = :id")
    abstract suspend fun setUndone(id: Long, undone: Boolean, redoPayload: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putMemory(memory: AssistantMemoryEntity)

    @Query("SELECT * FROM AssistantMemory")
    abstract suspend fun allMemory(): List<AssistantMemoryEntity>

    @Query("DELETE FROM AssistantMemory WHERE key = :key")
    abstract suspend fun deleteMemory(key: String)

    @Insert abstract suspend fun insertConversation(conversation: AssistantConversationEntity): Long

    @Query(
        "UPDATE AssistantConversation SET title = COALESCE(title, :title), updatedAt = :updatedAt, " +
            "turnsJson = :turnsJson, apiMessagesJson = :apiMessagesJson WHERE id = :id"
    )
    abstract suspend fun updateConversation(
        id: Long,
        title: String?,
        updatedAt: Long,
        turnsJson: String,
        apiMessagesJson: String,
    )

    @Query("SELECT * FROM AssistantConversation WHERE id = :id")
    abstract suspend fun conversationById(id: Long): AssistantConversationEntity?

    @Query("SELECT * FROM AssistantConversation ORDER BY updatedAt DESC")
    abstract fun observeConversations(): Flow<List<AssistantConversationEntity>>

    @Query("DELETE FROM AssistantConversation WHERE id = :id")
    abstract suspend fun deleteConversation(id: Long)
}
