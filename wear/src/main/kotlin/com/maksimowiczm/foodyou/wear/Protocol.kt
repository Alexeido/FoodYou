package com.maksimowiczm.foodyou.wear

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/* docs/sync/protocol.md, lo que el reloj necesita de él. */

@Serializable data class Field(val value: JsonElement, val clock: Long, val device: String? = null)

@Serializable
data class Doc(
    val kind: String,
    val id: String,
    val seq: Long = 0,
    val deleted: Boolean = false,
    val fields: Map<String, Field>,
)

@Serializable data class ChangeField(val value: JsonElement, val clock: Long)

@Serializable data class Change(val kind: String, val id: String, val fields: Map<String, ChangeField>)

@Serializable data class SyncRequest(val cursor: Long, val changes: List<Change>)

@Serializable data class SyncResponse(val cursor: Long, val more: Boolean, val documents: List<Doc>)

@Serializable data class PairRequest(val code: String, val name: String)

@Serializable data class PairResponse(val account: String, val token: String)

const val MEAL = "meal"
const val FOOD_ENTRY = "food_entry"
const val MANUAL_ENTRY = "manual_entry"
