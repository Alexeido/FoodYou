package com.maksimowiczm.foodyou.assistant.infrastructure.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The handful of things the user has told the assistant about themselves.
 *
 * Deliberately not a body-measurement domain: four loose fields, no history, no charts. It exists so
 * the assistant stops asking for your weight in every new conversation, and it becomes redundant the
 * day an app that really tracks this exists.
 */
@Entity(tableName = "AssistantMemory")
data class AssistantMemoryEntity(
    @PrimaryKey val key: String,
    val value: String,
    /** Epoch seconds, so a stale figure can be re-checked with the user. */
    val updatedAt: Long,
)
