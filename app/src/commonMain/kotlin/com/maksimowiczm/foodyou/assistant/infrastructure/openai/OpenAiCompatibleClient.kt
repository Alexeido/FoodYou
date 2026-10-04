package com.maksimowiczm.foodyou.assistant.infrastructure.openai

import com.maksimowiczm.foodyou.assistant.domain.AssistantCredentialsRepository
import com.maksimowiczm.foodyou.assistant.domain.AssistantPreferences
import com.maksimowiczm.foodyou.common.log.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first

/** What went wrong, in terms the settings screen can explain and the user can act on. */
sealed interface AssistantApiError {
    /** The key was rejected. Distinct from "the model did not know" on purpose. */
    data object Unauthorized : AssistantApiError

    data object RateLimited : AssistantApiError

    data class Http(val status: Int, val body: String) : AssistantApiError

    /**
     * The request just didn't get an answer in time - a reasoning model mid tool-call chain can
     * easily outrun 90 s. Kept apart from [Network] because the useful response is different: a
     * dropped connection or a wrong URL need fixing, but a timeout usually just needs the same
     * turn nudged forward, which is why the chat offers a "Continue" button only for this one.
     */
    data object Timeout : AssistantApiError

    data class Network(val message: String) : AssistantApiError

    data object NotConfigured : AssistantApiError
}

class AssistantApiException(val error: AssistantApiError) :
    Exception(
        when (error) {
            is AssistantApiError.Unauthorized -> "API key rejected"
            is AssistantApiError.RateLimited -> "Rate limited"
            is AssistantApiError.Http -> "HTTP ${error.status}. ${error.body}"
            is AssistantApiError.Timeout -> "Request timed out"
            is AssistantApiError.Network -> error.message
            is AssistantApiError.NotConfigured -> "Assistant not configured"
        }
    )

/**
 * A thin client for any endpoint speaking the OpenAI chat-completions dialect.
 *
 * Lives in commonMain so iOS inherits it the day that target is switched on, and takes the base URL
 * from preferences so DeepSeek, OpenRouter, Groq or a local llama.cpp all work without a code
 * change.
 */
class OpenAiCompatibleClient(
    private val httpClient: HttpClient,
    private val credentials: AssistantCredentialsRepository,
    private val preferences: AssistantPreferences,
    private val logger: Logger,
) {

    suspend fun chat(request: ChatRequest): ChatResponse {
        val key = credentials.observe().first()?.apiKey ?: throw AssistantApiException(AssistantApiError.NotConfigured)
        val baseUrl = preferences.observe().first().baseUrl

        val response =
            try {
                httpClient.post("${baseUrl.trimEnd('/')}/chat/completions") {
                    header("Authorization", "Bearer $key")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            } catch (e: HttpRequestTimeoutException) {
                throw AssistantApiException(AssistantApiError.Timeout)
            } catch (e: Exception) {
                // HttpRequestTimeoutException cubre el timeout de la propia peticion (Ktor), pero
                // el de conexion o el de socket los lanza el motor (OkHttp en Android) con su
                // propia excepcion, que no es parte de la API comun de Ktor. Se detecta por el
                // nombre en vez de por tipo para no acoplarse a una clase de un motor concreto.
                if (e::class.simpleName?.contains("Timeout", ignoreCase = true) == true) {
                    throw AssistantApiException(AssistantApiError.Timeout)
                }
                logger.e(TAG, e) { "Chat request failed" }
                throw AssistantApiException(AssistantApiError.Network(e.message ?: "sin conexion"))
            }

        when (response.status) {
            HttpStatusCode.OK -> Unit
            HttpStatusCode.Unauthorized,
            HttpStatusCode.PaymentRequired ->
                throw AssistantApiException(AssistantApiError.Unauthorized)
            HttpStatusCode.TooManyRequests ->
                throw AssistantApiException(AssistantApiError.RateLimited)
            else ->
                throw AssistantApiException(
                    AssistantApiError.Http(response.status.value, response.bodyAsText().take(400))
                )
        }

        return response.body()
    }

    /**
     * Asks the server which models it serves.
     *
     * The settings screen fills its picker from this rather than from a hard-coded list, so a new
     * model on the provider's side shows up without an app update - and so "test connection"
     * actually proves the key works.
     */
    suspend fun listModels(): List<String> {
        val key = credentials.observe().first()?.apiKey ?: throw AssistantApiException(AssistantApiError.NotConfigured)
        val baseUrl = preferences.observe().first().baseUrl

        val response =
            try {
                httpClient.get("${baseUrl.trimEnd('/')}/models") {
                    header("Authorization", "Bearer $key")
                }
            } catch (e: Exception) {
                throw AssistantApiException(AssistantApiError.Network(e.message ?: "sin conexion"))
            }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw AssistantApiException(AssistantApiError.Unauthorized)
        }
        if (response.status != HttpStatusCode.OK) {
            throw AssistantApiException(
                AssistantApiError.Http(response.status.value, response.bodyAsText().take(400))
            )
        }

        return response.body<ModelList>().data.map { it.id }.sorted()
    }

    private companion object {
        const val TAG = "OpenAiCompatibleClient"
    }
}
