package com.maksimowiczm.foodyou.sync.infrastructure

import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.sync.domain.DELETED_FIELD
import com.maksimowiczm.foodyou.sync.domain.GOALS_DOCUMENT_ID
import com.maksimowiczm.foodyou.sync.domain.MEMORY_DOCUMENT_ID
import com.maksimowiczm.foodyou.sync.domain.SyncApi
import com.maksimowiczm.foodyou.sync.domain.SyncChange
import com.maksimowiczm.foodyou.sync.domain.SyncConfig
import com.maksimowiczm.foodyou.sync.domain.SyncConfigRepository
import com.maksimowiczm.foodyou.sync.domain.SyncDocument
import com.maksimowiczm.foodyou.sync.domain.SyncFieldChange
import com.maksimowiczm.foodyou.sync.domain.SyncHttpException
import com.maksimowiczm.foodyou.sync.domain.SyncRequest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class SyncFailure {
    /** No connection, or the server did not answer. */
    Network,

    /** Wrong username or password. */
    Unauthorized,

    /** The account is disabled. */
    Forbidden,

    /** The server answered with an error. */
    Server,
}

data class SyncStatus(
    val running: Boolean = false,
    /** Epoch milliseconds of the last sync that finished well. */
    val lastSuccess: Long? = null,
    val lastFailure: SyncFailure? = null,
)

sealed interface SyncOutcome {
    data object NotConfigured : SyncOutcome

    data object Disabled : SyncOutcome

    data class Done(val sent: Int, val received: Int) : SyncOutcome

    data class Failed(val reason: SyncFailure) : SyncOutcome
}

/**
 * Keeps the local diary and the account in step (docs/sync/protocol.md).
 *
 * Every change is already in the local database when this runs - the app never waits for it. A
 * round sends what changed since the server last confirmed it (comparing each row against its
 * shadow, field by field) and applies whatever the server has that is newer. Only one sync runs at
 * a time.
 */
internal class SyncEngine(
    private val executor: SqlExecutor,
    private val store: SyncLocalStore,
    private val api: SyncApi,
    private val configRepository: SyncConfigRepository,
    private val now: () -> Long,
    private val logger: Logger,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    suspend fun sync(): SyncOutcome =
        mutex.withLock {
            val config = configRepository.observe().first() ?: return SyncOutcome.NotConfigured
            if (!config.enabled) return SyncOutcome.Disabled

            _status.update { it.copy(running = true) }
            try {
                val firstTime =
                    executor.transaction {
                        with(store) {
                            setTracking(true)
                            assignMissingIds()
                            state(CURSOR) == null
                        }
                    }
                var sent = 0
                var received = 0
                if (firstTime) received += initialSync(config)
                var rounds = 0
                do {
                    val round = round(config)
                    sent += round.sent
                    received += round.received
                    rounds++
                } while ((round.more || round.dirtyLeft) && rounds < MAX_ROUNDS)

                _status.update { SyncStatus(running = false, lastSuccess = now()) }
                SyncOutcome.Done(sent, received)
            } catch (e: CancellationException) {
                _status.update { it.copy(running = false) }
                throw e
            } catch (e: SyncHttpException) {
                val reason =
                    when (e.status) {
                        401 -> SyncFailure.Unauthorized
                        403 -> SyncFailure.Forbidden
                        else -> SyncFailure.Server
                    }
                logger.w(TAG, e) { "Sync rejected: ${e.status}" }
                fail(reason)
            } catch (e: Exception) {
                logger.w(TAG, e) { "Sync failed" }
                fail(SyncFailure.Network)
            }
        }

    private fun fail(reason: SyncFailure): SyncOutcome {
        _status.update { it.copy(running = false, lastFailure = reason) }
        return SyncOutcome.Failed(reason)
    }

    /**
     * Stops tracking and forgets everything about the account. Turning sync on again starts with
     * a first sync, in which the account wins for every document it already has.
     */
    suspend fun disable() =
        mutex.withLock {
            executor.transaction {
                with(store) {
                    setTracking(false)
                    exec("DELETE FROM SyncDirty")
                    exec("DELETE FROM SyncShadow")
                    exec("DELETE FROM SyncPending")
                    setState(CURSOR, null)
                }
            }
            _status.value = SyncStatus()
        }

    /**
     * The goals changed here (or were just applied from the account - then nothing differs from
     * what the server confirmed and the next round sends nothing). Only while syncing.
     */
    suspend fun goalsChanged() {
        if (!store.syncsGoals) return
        executor.transaction {
            with(store) {
                if (isTracking()) markDirty(SyncKind.Goals, GOALS_DOCUMENT_ID)
            }
        }
    }

    /** Local changes not confirmed by the server yet. */
    suspend fun pendingChanges(): Int =
        executor.transaction {
            (query("SELECT COUNT(DISTINCT kind || ':' || syncId) AS n FROM SyncDirty")
                    .first()["n"] as Long)
                .toInt()
        }

    // --- A normal round ----------------------------------------------------------------------

    private data class Round(val sent: Int, val received: Int, val more: Boolean, val dirtyLeft: Boolean)

    private suspend fun round(config: SyncConfig): Round {
        val (maxSeq, changes) =
            executor.transaction {
                with(store) {
                    val maxSeq = query("SELECT MAX(seq) AS m FROM SyncDirty").first()["m"] as Long? ?: 0L
                    val dirty =
                        query(
                            "SELECT kind, syncId, MAX(changedAt) AS at FROM SyncDirty " +
                                "WHERE seq <= ? GROUP BY kind, syncId",
                            maxSeq,
                        )
                    maxSeq to
                        dirty.mapNotNull { row ->
                            val kind = SyncKind.of(row["kind"] as String) ?: return@mapNotNull null
                            buildChange(kind, row["syncId"] as String, row["at"] as Long)
                        }
                }
            }

        var received = 0
        var more = false
        val chunks = changes.chunked(MAX_CHANGES).ifEmpty { listOf(emptyList()) }
        for (chunk in chunks) {
            val cursor = executor.transaction { with(store) { state(CURSOR)?.toLongOrNull() ?: 0L } }
            val response = api.sync(config, SyncRequest(cursor, chunk))
            received += response.documents.size
            more = response.more
            applyResponse(response.documents, response.cursor, maxSeq)
        }
        // Solo cuando todo ha llegado: si algo falla a medias se reenvía, y mezclar dos veces lo
        // mismo no cambia nada.
        val dirtyLeft =
            executor.transaction {
                exec("DELETE FROM SyncDirty WHERE seq <= ?", maxSeq)
                (query("SELECT COUNT(*) AS n FROM SyncDirty").first()["n"] as Long) > 0
            }
        return Round(changes.size, received, more, dirtyLeft)
    }

    /** What changed in a document since the server last confirmed it; null if nothing did. */
    private suspend fun Sql.buildChange(kind: SyncKind, syncId: String, changedAt: Long): SyncChange? =
        with(store) {
            val current = readDocument(kind, syncId)
            val shadow = shadow(kind, syncId)
            // Un cambio nunca puede quedar por detrás del que ya confirmó el servidor para ese campo.
            fun clockFor(field: String) = maxOf(changedAt, (shadow?.get(field)?.clock ?: 0L) + 1)
            val wasDeleted = shadow?.get(DELETED_FIELD)?.value == JsonPrimitive(true)

            if (current == null) {
                // Creado y borrado sin llegar a subir, o ya borrado en el servidor: nada que contar.
                if (shadow == null || wasDeleted) return null
                return SyncChange(
                    kind.wire,
                    syncId,
                    mapOf(DELETED_FIELD to SyncFieldChange(JsonPrimitive(true), clockFor(DELETED_FIELD))),
                )
            }

            val fields =
                current
                    .filter { (name, value) -> !sameJson(shadow?.get(name)?.value, value) }
                    .mapValues { (name, value) -> SyncFieldChange(value, clockFor(name)) }
                    .toMutableMap()
            if (kind == SyncKind.Memory) {
                // Lo que se ha olvidado aquí ya no está en la tabla: se manda como null.
                shadow
                    ?.filter { (name, field) ->
                        name !in current && name != DELETED_FIELD && field.value !is JsonNull
                    }
                    ?.forEach { (name, _) -> fields[name] = SyncFieldChange(JsonNull, clockFor(name)) }
            }
            if (wasDeleted) {
                // Vuelve a existir aquí (deshacer): se dice explícitamente, o seguiría borrado.
                fields[DELETED_FIELD] = SyncFieldChange(JsonPrimitive(false), clockFor(DELETED_FIELD))
            }
            if (fields.isEmpty()) null else SyncChange(kind.wire, syncId, fields)
        }

    private suspend fun applyResponse(documents: List<SyncDocument>, cursor: Long, maxSeq: Long) {
        executor.transaction {
            with(store) {
                setApplying(true)
                try {
                    // Lo que ha cambiado aquí mientras viajaba la petición manda: se envía en la
                    // siguiente vuelta y el servidor devolverá la mezcla.
                    val newerHere =
                        query("SELECT DISTINCT kind, syncId FROM SyncDirty WHERE seq > ?", maxSeq)
                            .map { (it["kind"] as String) to (it["syncId"] as String) }
                            .toSet()
                    documents
                        .sortedBy { SyncKind.of(it.kind)?.ordinal ?: Int.MAX_VALUE }
                        .filter { (it.kind to it.id) !in newerHere }
                        .forEach { adopt(it) }
                    retryPending(newerHere)
                    setState(CURSOR, cursor.toString())
                } finally {
                    setApplying(false)
                }
            }
        }
    }

    /**
     * Applies a document from the server and records it as confirmed. A document that cannot be
     * applied yet (its meal is not here) or that fails is kept in SyncPending for later.
     */
    private suspend fun Sql.adopt(document: SyncDocument) =
        with(store) {
            val kind = SyncKind.of(document.kind) ?: return@with // un tipo de una versión más nueva
            val values = document.fields.mapValues { it.value.value }
            val deleted = document.deleted || values[DELETED_FIELD] == JsonPrimitive(true)
            val applied =
                try {
                    exec("SAVEPOINT sync_document")
                    applyDocument(kind, document.id, values - DELETED_FIELD, deleted).also {
                        exec("RELEASE sync_document")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    exec("ROLLBACK TO sync_document")
                    exec("RELEASE sync_document")
                    logger.w(TAG, e) { "Could not apply ${document.kind} ${document.id}" }
                    false
                }
            if (applied) {
                saveShadow(
                    kind,
                    document.id,
                    document.fields.mapValues { ShadowField(it.value.value, it.value.clock) },
                )
                exec("DELETE FROM SyncPending WHERE kind = ? AND syncId = ?", kind.wire, document.id)
            } else {
                exec(
                    "INSERT OR REPLACE INTO SyncPending (kind, syncId, document) VALUES (?, ?, ?)",
                    kind.wire,
                    document.id,
                    json.encodeToString(SyncDocument.serializer(), document),
                )
            }
        }

    private suspend fun Sql.retryPending(skip: Set<Pair<String, String>>) {
        query("SELECT document FROM SyncPending")
            .map { json.decodeFromString(SyncDocument.serializer(), it["document"] as String) }
            .sortedBy { SyncKind.of(it.kind)?.ordinal ?: Int.MAX_VALUE }
            .filter { (it.kind to it.id) !in skip }
            .forEach { adopt(it) }
    }

    // --- The first sync of a device ----------------------------------------------------------

    /**
     * Merges instead of overwriting. The account wins for every document it already has; local
     * rows it does not have are uploaded; and a local meal with the same name as one of the
     * account's ("Breakfast" on both phones) is joined to it rather than duplicated.
     */
    private suspend fun initialSync(config: SyncConfig): Int {
        val server = LinkedHashMap<Pair<String, String>, SyncDocument>()
        var cursor = 0L
        do {
            val response = api.sync(config, SyncRequest(cursor, emptyList()))
            response.documents.forEach { server[it.kind to it.id] = it }
            cursor = response.cursor
        } while (response.more)

        executor.transaction {
            with(store) {
                setApplying(true)
                try {
                    val adopted = mutableSetOf<Pair<String, String>>()
                    val serverMeals = server.values.filter { it.kind == SyncKind.Meal.wire && !it.deleted }

                    localRows(SyncKind.Meal).forEach { (localId, syncId) ->
                        val same = server[SyncKind.Meal.wire to syncId]
                        val byName =
                            if (same != null) null
                            else {
                                val name = mealName(localId)
                                serverMeals.firstOrNull { meal ->
                                    (meal.kind to meal.id) !in adopted &&
                                        localId(SyncKind.Meal, meal.id) == null &&
                                        meal.nameField()?.trim().equals(name?.trim(), ignoreCase = true)
                                }
                            }
                        val match = same ?: byName
                        if (match != null) {
                            if (byName != null) remap(SyncKind.Meal, localId, byName.id)
                            adopt(match)
                            adopted += match.kind to match.id
                        } else {
                            markDirty(SyncKind.Meal, syncId)
                        }
                    }

                    listOf(SyncKind.FoodEntry, SyncKind.ManualEntry, SyncKind.Recipe).forEach { kind ->
                        localRows(kind).forEach { (_, syncId) ->
                            val doc = server[kind.wire to syncId]
                            if (doc != null) {
                                adopt(doc)
                                adopted += doc.kind to doc.id
                            } else {
                                markDirty(kind, syncId)
                            }
                        }
                    }

                    // Las metas: si la cuenta ya tiene, mandan (como todo lo demás); si no, las de
                    // este móvil pasan a ser las de la cuenta.
                    if (syncsGoals) {
                        val goals = server[SyncKind.Goals.wire to GOALS_DOCUMENT_ID]
                        if (goals != null) {
                            adopt(goals)
                            adopted += goals.kind to goals.id
                        } else {
                            markDirty(SyncKind.Goals, GOALS_DOCUMENT_ID)
                        }
                    }

                    // La memoria se junta: lo de la cuenta llega aquí y lo que solo sabía este
                    // móvil sube en la siguiente vuelta.
                    server[SyncKind.Memory.wire to MEMORY_DOCUMENT_ID]?.let {
                        adopt(it)
                        adopted += it.kind to it.id
                    }
                    markDirty(SyncKind.Memory, MEMORY_DOCUMENT_ID)

                    server.values
                        .sortedBy { SyncKind.of(it.kind)?.ordinal ?: Int.MAX_VALUE }
                        .filter { (it.kind to it.id) !in adopted }
                        .forEach { adopt(it) }

                    setState(CURSOR, cursor.toString())
                } finally {
                    setApplying(false)
                }
            }
        }
        return server.size
    }

    private suspend fun Sql.mealName(localId: Long): String? =
        query("SELECT name FROM Meal WHERE id = ?", localId).firstOrNull()?.get("name") as String?

    private fun SyncDocument.nameField(): String? =
        (fields["name"]?.value as? JsonPrimitive)?.contentOrNull

    private suspend fun Sql.markDirty(kind: SyncKind, syncId: String) =
        exec(
            "INSERT INTO SyncDirty (kind, syncId, changedAt) VALUES (?, ?, ?)",
            kind.wire,
            syncId,
            now(),
        )

    private companion object {
        const val TAG = "SyncEngine"
        const val CURSOR = "cursor"
        const val MAX_CHANGES = 500
        const val MAX_ROUNDS = 20
    }
}
