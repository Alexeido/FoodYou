package com.maksimowiczm.foodyou.sync.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.sync.domain.SyncConfig
import com.maksimowiczm.foodyou.sync.domain.SyncConfigRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The sync settings, encrypted like the custom food database's credentials, under a key of
 * their own: the two are configured separately and never share an account.
 */
internal class DataStoreSyncConfigRepository(
    private val masterCrypto: MasterCrypto,
    private val dataStore: DataStore<Preferences>,
    private val logger: Logger,
) : SyncConfigRepository {

    override fun observe(): Flow<SyncConfig?> =
        dataStore.data.map { prefs ->
            val stored = prefs[KEY] ?: return@map null
            try {
                val encrypted = Json.decodeFromString<EncryptedSyncConfig>(stored)
                SyncConfig(
                    serverUrl = encrypted.serverUrl,
                    username = masterCrypto.decrypt(encrypted.username).decodeToString(),
                    password = masterCrypto.decrypt(encrypted.password).decodeToString(),
                    enabled = encrypted.enabled,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, e) { "Could not read the sync settings" }
                null
            }
        }

    override suspend fun save(config: SyncConfig) {
        val encrypted =
            EncryptedSyncConfig(
                serverUrl = config.serverUrl.trim(),
                username = masterCrypto.encrypt(config.username.encodeToByteArray()),
                password = masterCrypto.encrypt(config.password.encodeToByteArray()),
                enabled = config.enabled,
            )
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply { this[KEY] = Json.encodeToString(encrypted) }
        }
    }

    override suspend fun clear() {
        dataStore.updateData { prefs -> prefs.toMutablePreferences().apply { remove(KEY) } }
    }

    private companion object {
        const val TAG = "DataStoreSyncConfigRepository"
        val KEY = stringPreferencesKey("sync:config")
    }
}

@Serializable
private class EncryptedSyncConfig(
    val serverUrl: String,
    val username: ByteArray,
    val password: ByteArray,
    val enabled: Boolean,
)
