package com.maksimowiczm.foodyou.sync.infrastructure

import com.maksimowiczm.foodyou.common.config.NetworkConfig
import com.maksimowiczm.foodyou.common.system.InstallationId
import com.maksimowiczm.foodyou.sync.domain.PairingCode
import com.maksimowiczm.foodyou.sync.domain.SyncApi
import com.maksimowiczm.foodyou.sync.domain.SyncEvent
import com.maksimowiczm.foodyou.sync.domain.SyncConfig
import com.maksimowiczm.foodyou.sync.domain.SyncHttpException
import com.maksimowiczm.foodyou.sync.domain.SyncRequest
import com.maksimowiczm.foodyou.sync.domain.SyncResponse
import com.maksimowiczm.foodyou.sync.domain.SyncServerStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.basicAuth
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.userAgent

/** docs/sync/protocol.md over HTTP, with Basic Auth and the installation id as device. */
internal class KtorSyncApi(
    private val client: HttpClient,
    private val networkConfig: NetworkConfig,
    private val installationId: InstallationId,
) : SyncApi {

    override suspend fun sync(config: SyncConfig, request: SyncRequest): SyncResponse {
        val response =
            client.post("${config.baseUrl}/v1/sync") {
                common(config)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        return response.checked().body()
    }

    override suspend fun status(config: SyncConfig): SyncServerStatus =
        client.get("${config.baseUrl}/v1/status") { common(config) }.checked().body()

    override suspend fun pairingCode(config: SyncConfig): PairingCode =
        client.post("${config.baseUrl}/v1/pairing-codes") { common(config) }.checked().body()

    override fun events(config: SyncConfig): Flow<SyncEvent> = flow {
        client
            .prepareGet("${config.baseUrl}/v1/events") {
                common(config)
                // Es una conexión que se queda abierta: sin límite total, pero si en 90 s no
                // llega ni el latido (cada 25 s), está muerta.
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = 90_000
                }
            }
            .execute { response ->
                response.checked()
                val channel = response.bodyAsChannel()
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    when (line.trim()) {
                        "event: ready" -> emit(SyncEvent.Ready)
                        "event: changed" -> emit(SyncEvent.Changed)
                    }
                }
            }
    }

    private fun HttpRequestBuilder.common(config: SyncConfig) {
        userAgent(networkConfig.userAgent)
        basicAuth(config.username, config.password)
        installationId.get()?.let { header("X-Device-Id", it) }
    }

    private suspend fun HttpResponse.checked(): HttpResponse {
        if (!status.isSuccess()) {
            throw SyncHttpException(status.value, bodyAsText().take(300))
        }
        return this
    }
}
