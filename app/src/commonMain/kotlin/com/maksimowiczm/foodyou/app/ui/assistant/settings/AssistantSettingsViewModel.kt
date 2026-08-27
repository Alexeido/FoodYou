package com.maksimowiczm.foodyou.app.ui.assistant.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.assistant.domain.AssistantCredentials
import com.maksimowiczm.foodyou.assistant.domain.AssistantCredentialsRepository
import com.maksimowiczm.foodyou.assistant.domain.AssistantPreferences
import com.maksimowiczm.foodyou.assistant.domain.AssistantSettings
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.AssistantApiError
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.AssistantApiException
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.OpenAiCompatibleClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the "test connection" button has found out so far. */
sealed interface ConnectionState {
    data object Idle : ConnectionState

    data object Testing : ConnectionState

    data class Connected(val models: List<String>) : ConnectionState

    /** Deliberately specific: "the key was rejected" is not the same as "it did not work". */
    data class Failed(val reason: FailureReason, val detail: String?) : ConnectionState
}

enum class FailureReason {
    Unauthorized,
    RateLimited,
    Network,
    Server,
    NotConfigured,
}

internal class AssistantSettingsViewModel(
    private val preferences: AssistantPreferences,
    private val credentials: AssistantCredentialsRepository,
    private val client: OpenAiCompatibleClient,
) : ViewModel() {

    val settings: StateFlow<AssistantSettings> =
        preferences
            .observe()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = AssistantSettings(),
            )

    /** Only whether a key exists, never the key itself: it must not travel back to the UI. */
    val hasApiKey: StateFlow<Boolean> =
        credentials
            .observe()
            .map { it != null }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = false,
            )

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    fun setBaseUrl(value: String) {
        viewModelScope.launch {
            preferences.setBaseUrl(value.trim())
            _connection.value = ConnectionState.Idle
        }
    }

    fun setApiKey(value: String) {
        viewModelScope.launch {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) credentials.clear()
            else credentials.save(AssistantCredentials(trimmed))
            _connection.value = ConnectionState.Idle
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            credentials.clear()
            _connection.value = ConnectionState.Idle
        }
    }

    fun setModel(value: String) {
        viewModelScope.launch { preferences.setModel(value) }
    }

    fun setVisionModel(value: String) {
        viewModelScope.launch { preferences.setVisionModel(value) }
    }

    fun setMaxIterations(value: Int) {
        viewModelScope.launch { preferences.setMaxIterations(value) }
    }

    /**
     * Asks the server which models it serves.
     *
     * Doubles as the connection test: a list coming back proves the URL is right, the key is valid
     * and the network is up, and it fills the picker with what this provider really offers instead
     * of a list baked into the app.
     */
    fun testConnection() {
        viewModelScope.launch {
            _connection.value = ConnectionState.Testing
            _connection.value =
                try {
                    ConnectionState.Connected(client.listModels())
                } catch (e: AssistantApiException) {
                    when (val error = e.error) {
                        is AssistantApiError.Unauthorized ->
                            ConnectionState.Failed(FailureReason.Unauthorized, null)
                        is AssistantApiError.RateLimited ->
                            ConnectionState.Failed(FailureReason.RateLimited, null)
                        is AssistantApiError.Network ->
                            ConnectionState.Failed(FailureReason.Network, error.message)
                        is AssistantApiError.Http ->
                            ConnectionState.Failed(FailureReason.Server, "HTTP ${error.status}")
                        is AssistantApiError.NotConfigured ->
                            ConnectionState.Failed(FailureReason.NotConfigured, null)
                    }
                }
        }
    }
}
