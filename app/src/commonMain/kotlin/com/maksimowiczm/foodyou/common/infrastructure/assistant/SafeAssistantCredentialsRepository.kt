package com.maksimowiczm.foodyou.common.infrastructure.assistant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.assistant.domain.AssistantCredentials
import com.maksimowiczm.foodyou.assistant.domain.AssistantCredentialsRepository
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import com.maksimowiczm.foodyou.common.log.Logger
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class SafeAssistantCredentialsRepository(
    private val masterCrypto: MasterCrypto,
    private val dataStore: DataStore<Preferences>,
    private val logger: Logger,
) : AssistantCredentialsRepository {

    override suspend fun save(credentials: AssistantCredentials) {
        val encrypted = EncryptedAssistantCredentials(masterCrypto.encrypt(credentials.apiKey.toByteArray()))
        val json = Json.encodeToString(encrypted)

        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply { this[AssistantKeys.apiKey] = json }
        }
    }

    override fun observe(): Flow<AssistantCredentials?> =
        dataStore.data.map { prefs ->
            try {
                val json = prefs[AssistantKeys.apiKey] ?: return@map null
                val encrypted = Json.decodeFromString<EncryptedAssistantCredentials>(json)
                AssistantCredentials(masterCrypto.decrypt(encrypted.apiKey).decodeToString())
            } catch (e: Exception) {
                logger.e("SafeAssistantCredentialsRepository", e) { "Failed to read assistant key" }
                null
            }
        }

    override suspend fun clear() {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply { remove(AssistantKeys.apiKey) }
        }
    }
}

@Serializable private class EncryptedAssistantCredentials(val apiKey: ByteArray)

private object AssistantKeys {
    val apiKey = stringPreferencesKey("assistant:api_key")
}
