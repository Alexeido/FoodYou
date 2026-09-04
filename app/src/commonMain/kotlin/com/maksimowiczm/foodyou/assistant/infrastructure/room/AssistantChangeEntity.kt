package com.maksimowiczm.foodyou.assistant.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "AssistantChange", indices = [Index(value = ["createdAt"])])
data class AssistantChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Epoch seconds. */
    val createdAt: Long,

    /** What the user sees in the history and next to the undo button. */
    val summary: String,

    /** Serialized [com.maksimowiczm.foodyou.assistant.domain.journal.UndoAction]. */
    val undoPayload: String,

    val undone: Boolean = false,

    /**
     * Serialized action that re-applies the change, filled in when it is undone so redo has
     * something to work with. Null until then.
     */
    val redoPayload: String? = null,

    /** Which conversation made this change. Null on rows recorded before conversations existed. */
    val conversationId: Long? = null,
)
