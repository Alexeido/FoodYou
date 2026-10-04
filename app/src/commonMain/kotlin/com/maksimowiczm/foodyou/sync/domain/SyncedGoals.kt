package com.maksimowiczm.foodyou.sync.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * The goals as a sync document. Unlike the diary they are not rows but settings, so instead of
 * table triggers the goals feature tells sync what they look like, how to apply the account's,
 * and when they change.
 */
interface SyncedGoals {
    suspend fun read(): Map<String, JsonElement>

    suspend fun apply(fields: Map<String, JsonElement>)

    /** Emits whenever the goals or the tracked nutrients change, including the current value. */
    val changes: Flow<Any>
}

/** The goals are one document per account, always with this id. */
const val GOALS_DOCUMENT_ID = "goals"

/** So is what the assistant remembers. */
const val MEMORY_DOCUMENT_ID = "memory"
