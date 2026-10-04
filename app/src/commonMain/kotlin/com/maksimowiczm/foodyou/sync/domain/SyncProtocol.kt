package com.maksimowiczm.foodyou.sync.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * The wire format of docs/sync/protocol.md. Kept apart from the engine so a different transport
 * (or a fake server in tests) only has to speak these shapes.
 */

@Serializable data class SyncFieldChange(val value: JsonElement, val clock: Long)

@Serializable
data class SyncChange(val kind: String, val id: String, val fields: Map<String, SyncFieldChange>)

@Serializable data class SyncRequest(val cursor: Long, val changes: List<SyncChange>)

@Serializable
data class SyncField(val value: JsonElement, val clock: Long, val device: String? = null)

@Serializable
data class SyncDocument(
    val kind: String,
    val id: String,
    val seq: Long,
    val deleted: Boolean = false,
    val fields: Map<String, SyncField>,
)

@Serializable
data class SyncResponse(val cursor: Long, val more: Boolean, val documents: List<SyncDocument>)

@Serializable
data class SyncServerStatus(
    val account: String,
    val cursor: Long,
    val documents: Long,
    val serverTime: Long,
)

/** The field that marks a document as deleted. */
const val DELETED_FIELD = "_deleted"

@Serializable data class PairingCode(val code: String, val expiresInSeconds: Int)

/** What the live connection says. */
sealed interface SyncEvent {
    /** Connected: from now on changes arrive as they happen. */
    data object Ready : SyncEvent

    /** Something in the account changed. */
    data object Changed : SyncEvent
}

/** Talks to the sync server. */
interface SyncApi {
    suspend fun sync(config: SyncConfig, request: SyncRequest): SyncResponse

    suspend fun status(config: SyncConfig): SyncServerStatus

    /** A 6-digit code to pair a device (a watch) with this account. */
    suspend fun pairingCode(config: SyncConfig): PairingCode

    /**
     * The live connection (`GET /v1/events`). Emits while it stays open; ends or throws when it
     * drops, and the caller decides when to reconnect.
     */
    fun events(config: SyncConfig): kotlinx.coroutines.flow.Flow<SyncEvent>
}

/** The server answered, but not with data: wrong password, account disabled, bad request. */
class SyncHttpException(val status: Int, message: String) : Exception(message)
