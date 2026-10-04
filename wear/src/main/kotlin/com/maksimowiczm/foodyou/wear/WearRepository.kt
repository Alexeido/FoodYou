package com.maksimowiczm.foodyou.wear

import android.content.Context
import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Por qué no se ha podido emparejar o sincronizar. */
enum class WearProblem {
    WrongCode,
    NoConnection,
    /** El token ya no vale: se retiró desde el panel o se desactivó la cuenta. */
    Unpaired,
}

/**
 * El reloj es un dispositivo más de la cuenta, con su propio token (sin contraseña: se empareja
 * con el código de 6 cifras que da la app del móvil). Habla directamente con el servidor, así
 * que funciona lejos del móvil si el reloj tiene wifi o datos.
 */
class WearRepository(context: Context) {
    private val file = File(context.filesDir, "wear-state.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val client =
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }
            install(ContentNegotiation) { json(json) }
        }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<WearState> = _state.asStateFlow()

    private val _problem = MutableStateFlow<WearProblem?>(null)
    val problem: StateFlow<WearProblem?> = _problem.asStateFlow()

    private fun load(): WearState {
        val loaded =
            runCatching { json.decodeFromString(WearState.serializer(), file.readText()) }
                .getOrDefault(WearState())
        return if (loaded.deviceId.isBlank()) {
            loaded.copy(deviceId = "watch-" + UUID.randomUUID().toString().take(12))
        } else {
            loaded
        }
    }

    private suspend fun save(state: WearState) {
        _state.value = state
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(WearState.serializer(), state))
            tmp.renameTo(file)
        }
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    suspend fun pair(code: String): Boolean =
        mutex.withLock {
            val current = _state.value
            try {
                val response =
                    client.post("${current.serverUrl}/v1/pair") {
                        contentType(ContentType.Application.Json)
                        setBody(PairRequest(code, "Reloj ${Build.MODEL}"))
                    }
                if (!response.status.isSuccess()) {
                    _problem.value = WearProblem.WrongCode
                    return false
                }
                val paired = response.body<PairResponse>()
                save(WearState(current.serverUrl, paired.account, paired.token, current.deviceId))
                _problem.value = null
                true
            } catch (e: Exception) {
                _problem.value = WearProblem.NoConnection
                false
            }
        }.also { if (it) sync() }

    suspend fun unpair() = mutex.withLock { save(WearState(deviceId = _state.value.deviceId)) }

    fun toggle(row: EntryRow) {
        _state.value = _state.value.toggleEaten(row.kind, row.id, System.currentTimeMillis())
    }

    /** Manda lo pendiente y trae lo nuevo. Sin red no pasa nada: lo pendiente espera. */
    suspend fun sync() =
        mutex.withLock {
            var state = _state.value
            val token = state.token ?: return@withLock
            try {
                var sending = state.pending
                while (true) {
                    val response =
                        client.post("${state.serverUrl}/v1/sync") {
                            bearerAuth(token)
                            header("X-Device-Id", state.deviceId)
                            contentType(ContentType.Application.Json)
                            setBody(SyncRequest(state.cursor, sending))
                        }
                    if (response.status.value == 401 || response.status.value == 403) {
                        save(WearState(deviceId = state.deviceId))
                        _problem.value = WearProblem.Unpaired
                        return@withLock
                    }
                    if (!response.status.isSuccess()) throw IllegalStateException("${response.status}")
                    val body = response.body<SyncResponse>()
                    // Lo que se marcó mientras viajaba la petición sigue pendiente y manda.
                    val latest = _state.value
                    val stillPending = latest.pending.filterNot { it in sending }
                    state =
                        latest
                            .copy(pending = stillPending)
                            .merge(
                                body.documents,
                                body.cursor,
                                today(),
                                stillPending.map { WearState.key(it.kind, it.id) }.toSet(),
                            )
                    save(state)
                    sending = emptyList()
                    if (!body.more) break
                }
                _problem.value = null
            } catch (e: Exception) {
                _problem.value = WearProblem.NoConnection
                save(_state.value) // al menos que lo marcado no se pierda si se cierra la app
            }
        }

    /**
     * Escucha los avisos en vivo del servidor mientras la app está a la vista: si se marca algo
     * en el móvil, el reloj lo ve al momento. Devuelve cuando se corta la conexión.
     */
    suspend fun listen() {
        val state = _state.value
        val token = state.token ?: return
        client
            .prepareGet("${state.serverUrl}/v1/events") {
                bearerAuth(token)
                header("X-Device-Id", state.deviceId)
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = 90_000
                }
            }
            .execute { response ->
                if (!response.status.isSuccess()) return@execute
                val channel = response.bodyAsChannel()
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.trim() == "event: changed") sync()
                }
            }
    }
}
