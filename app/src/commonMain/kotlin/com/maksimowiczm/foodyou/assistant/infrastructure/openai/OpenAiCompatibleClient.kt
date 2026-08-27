package com.maksimowiczm.foodyou.assistant.infrastructure.openai

import com.maksimowiczm.foodyou.assistant.domain.AssistantCredentialsRepository
import com.maksimowiczm.foodyou.assistant.domain.AssistantPreferences
import com.maksimowiczm.foodyou.common.log.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

    data class Network(val message: String) : AssistantApiError

    data object NotConfigured : AssistantApiError
}

class AssistantApiException(val error: AssistantApiError) :
    Exception(
        when (error) {
            is AssistantApiError.Unauthorized -> "API key rejected"
            is AssistantApiError.RateLimited -> "Rate limited"
            is AssistantApiError.Http -> "HTTP ${error.status}"
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
            } catch (e: Exception) {
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
