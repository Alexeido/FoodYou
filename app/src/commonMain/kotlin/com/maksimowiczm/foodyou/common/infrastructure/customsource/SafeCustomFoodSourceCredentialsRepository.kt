package com.maksimowiczm.foodyou.common.infrastructure.customsource

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentials
import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentialsRepository
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import com.maksimowiczm.foodyou.common.log.Logger
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class SafeCustomFoodSourceCredentialsRepository(
    private val masterCrypto: MasterCrypto,
    private val dataStore: DataStore<Preferences>,
    private val logger: Logger,
) : CustomFoodSourceCredentialsRepository {
    override suspend fun saveCredentials(credentials: CustomFoodSourceCredentials) {
        val encrypted = masterCrypto.encryptCredentials(credentials)
        val json = Json.encodeToString(encrypted)

        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply { this[CredentialsKeys.credentialsKey] = json }
        }
    }

    override fun observeCredentials(): Flow<CustomFoodSourceCredentials?> =
        dataStore.data.map { prefs ->
            try {
                val json = prefs[CredentialsKeys.credentialsKey] ?: return@map null
                val encrypted = Json.decodeFromString<EncryptedCustomFoodSourceCredentials>(json)

                CustomFoodSourceCredentials(
                    username = masterCrypto.decrypt(encrypted.username).decodeToString(),
                    password = masterCrypto.decrypt(encrypted.password).decodeToString(),
                )
            } catch (e: Exception) {
                logger.e("SafeCustomFoodSourceCredentialsRepository", e) {
                    "Failed to get custom food source credentials"
                }
                null
            }
        }

    override suspend fun clearCredentials() {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply { remove(CredentialsKeys.credentialsKey) }
        }
    }
}

@Serializable
private class EncryptedCustomFoodSourceCredentials(val username: ByteArray, val password: ByteArray)

private suspend fun MasterCrypto.encryptCredentials(
    credentials: CustomFoodSourceCredentials
): EncryptedCustomFoodSourceCredentials =
    EncryptedCustomFoodSourceCredentials(
        username = encrypt(credentials.username.toByteArray()),
        password = encrypt(credentials.password.toByteArray()),
    )

private object CredentialsKeys {
    val credentialsKey = stringPreferencesKey("custom_food_source:credentials")
}
