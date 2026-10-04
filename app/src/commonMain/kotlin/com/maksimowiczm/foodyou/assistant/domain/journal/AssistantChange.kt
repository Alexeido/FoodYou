package com.maksimowiczm.foodyou.assistant.domain.journal

import kotlinx.datetime.LocalDateTime

/** One reversible thing the assistant did, as the history screen shows it. */
data class AssistantChange(
    val id: Long,
    val createdAt: LocalDateTime,
    val summary: String,
    val undone: Boolean,
    /** Null for changes recorded before conversations had an identity of their own. */
    val conversationId: Long? = null,
)

/**
 * The record of what the assistant changed, and the way back.
 *
 * Every mutating tool records here before returning. Nothing else in the app writes to it - a change
 * the user made by hand is not the assistant's to undo.
 */
interface ChangeJournal {

    /** Records a change and returns its id. [undo] must capture the state *before* the change. */
    suspend fun record(summary: String, undo: UndoAction): Long

    /**
     * Captures the current rows behind [entryIds] as an undo action.
     *
     * Taken from the database rather than from domain objects on purpose: the row carries the
     * productId the entry points at, and a restore without it would put back an entry whose food
     * is missing.
     */
    suspend fun snapshot(entryIds: List<Long>): UndoAction

    suspend fun recent(limit: Int = 20): List<AssistantChange>

    /**
     * Just [recent], filtered to one conversation - the Photoshop-style history panel shows only
     * what happened in the thread it is opened from, not the assistant's entire lifetime.
     */
    suspend fun recentForConversation(conversationId: Long, limit: Int = 50): List<AssistantChange> =
        recent(limit).filter { it.conversationId == conversationId }

    /** Reverts [changeId], or the most recent change that is still applied when null. */
    suspend fun undo(changeId: Long? = null): AssistantChange?

    /** Re-applies the change that was undone last. Invalidated by any new change. */
    suspend fun redo(): AssistantChange?
}
