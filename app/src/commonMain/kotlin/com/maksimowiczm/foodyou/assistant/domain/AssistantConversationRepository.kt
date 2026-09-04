package com.maksimowiczm.foodyou.assistant.domain

import com.maksimowiczm.foodyou.assistant.infrastructure.openai.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

/** One row in the conversation list, before its full turns are loaded. */
data class ConversationSummary(
    val id: Long,
    val title: String?,
    val updatedAt: LocalDateTime,
)

/** A conversation's full content, as saved and as it needs to come back to resume it. */
data class ConversationSnapshot(val turns: List<ChatTurn>, val apiMessages: List<Message>)

/**
 * Where conversations live between sessions.
 *
 * A conversation is created the moment its first message is sent - not before, so an abandoned
 * empty chat never shows up as a saved row - and saved again after every turn so leaving mid-plan
 * never loses anything.
 */
interface AssistantConversationRepository {

    /** Creates a new row and returns its id. [title] is usually the first message, truncated. */
    suspend fun create(title: String?): Long

    /** Overwrites a conversation's content. [title] only fills in if the row does not have one yet. */
    suspend fun save(id: Long, title: String?, turns: List<ChatTurn>, apiMessages: List<Message>)

    suspend fun load(id: Long): ConversationSnapshot?

    fun observeAll(): Flow<List<ConversationSummary>>

    suspend fun delete(id: Long)
}
