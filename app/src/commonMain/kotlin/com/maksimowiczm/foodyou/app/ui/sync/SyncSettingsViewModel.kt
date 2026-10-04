package com.maksimowiczm.foodyou.app.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.sync.domain.SyncApi
import com.maksimowiczm.foodyou.sync.domain.SyncConfig
import com.maksimowiczm.foodyou.sync.domain.SyncConfigRepository
import com.maksimowiczm.foodyou.sync.domain.SyncHttpException
import com.maksimowiczm.foodyou.sync.infrastructure.SyncEngine
import com.maksimowiczm.foodyou.sync.infrastructure.SyncFailure
import com.maksimowiczm.foodyou.sync.infrastructure.SyncScheduler
import com.maksimowiczm.foodyou.sync.infrastructure.SyncStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface ConnectionTest {
    data object Idle : ConnectionTest

    data object Testing : ConnectionTest

    data class Ok(val account: String, val documents: Long) : ConnectionTest

    data class Failed(val reason: SyncFailure) : ConnectionTest
}

internal sealed interface PairingState {
    data object Hidden : PairingState

    data object Loading : PairingState

    data class Code(val code: String, val minutes: Int) : PairingState

    data class Failed(val reason: SyncFailure) : PairingState
}

internal class SyncSettingsViewModel(
    private val configRepository: SyncConfigRepository,
    private val engine: SyncEngine,
    private val scheduler: SyncScheduler,
    private val api: SyncApi,
) : ViewModel() {

    /** Null while loading; a config with blank fields when nothing is set up yet. */
    val config: StateFlow<SyncConfig?> =
        configRepository
            .observe()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    val status: StateFlow<SyncStatus> = engine.status

    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _test = MutableStateFlow<ConnectionTest>(ConnectionTest.Idle)
    val test: StateFlow<ConnectionTest> = _test.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Hidden)
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    /** Asks the server for a 6-digit code the watch can use to join this account. */
    fun pairWatch() {
        val config = config.value ?: return
        _pairing.value = PairingState.Loading
        viewModelScope.launch {
            _pairing.value =
                try {
                    val code = api.pairingCode(config)
                    PairingState.Code(code.code, (code.expiresInSeconds / 60).coerceAtLeast(1))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SyncHttpException) {
                    PairingState.Failed(
                        if (e.status == 401) SyncFailure.Unauthorized else SyncFailure.Server
                    )
                } catch (e: Exception) {
                    PairingState.Failed(SyncFailure.Network)
                }
        }
    }

    fun closePairing() {
        _pairing.value = PairingState.Hidden
    }

    init {
        viewModelScope.launch { engine.status.collect { refreshPending() } }
    }

    private suspend fun refreshPending() {
        _pending.value = runCatching { engine.pendingChanges() }.getOrDefault(0)
    }

    fun testConnection(url: String, username: String, password: String) {
        _test.value = ConnectionTest.Testing
        viewModelScope.launch {
            _test.value =
                try {
                    val status = api.status(SyncConfig(url, username, password, enabled = false))
                    ConnectionTest.Ok(status.account, status.documents)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SyncHttpException) {
                    ConnectionTest.Failed(
                        when (e.status) {
                            401 -> SyncFailure.Unauthorized
                            403 -> SyncFailure.Forbidden
                            else -> SyncFailure.Server
                        }
                    )
                } catch (e: Exception) {
                    ConnectionTest.Failed(SyncFailure.Network)
                }
        }
    }

    /** Saves the account and turns sync on; the scheduler starts the first sync. */
    fun enable(url: String, username: String, password: String) {
        viewModelScope.launch {
            configRepository.save(SyncConfig(url.trim(), username.trim(), password, enabled = true))
        }
    }

    /** Saves edited server details without changing whether sync is on. */
    fun save(url: String, username: String, password: String) {
        viewModelScope.launch {
            val enabled = configRepository.observe().first()?.enabled ?: false
            configRepository.save(SyncConfig(url.trim(), username.trim(), password, enabled))
            if (enabled) scheduler.syncNow()
        }
    }

    fun disable() {
        viewModelScope.launch {
            engine.disable()
            configRepository.observe().first()?.let { configRepository.save(it.copy(enabled = false)) }
            refreshPending()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            scheduler.syncNow()
            refreshPending()
        }
    }

    fun forget() {
        viewModelScope.launch {
            engine.disable()
            configRepository.clear()
            _test.value = ConnectionTest.Idle
            refreshPending()
        }
    }
}
