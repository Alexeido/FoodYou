package com.maksimowiczm.foodyou.assistant.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.assistant.domain.AssistantPreferences
import com.maksimowiczm.foodyou.assistant.domain.AssistantSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DataStoreAssistantPreferences(private val dataStore: DataStore<Preferences>) :
    AssistantPreferences {

    override fun observe(): Flow<AssistantSettings> =
        dataStore.data.map { prefs ->
            AssistantSettings(
                baseUrl = prefs[Keys.baseUrl] ?: AssistantSettings.DEFAULT_BASE_URL,
                model = prefs[Keys.model].orEmpty(),
                // Compatibilidad con quien ya tenia un modelo de vision configurado antes de que
                // esto pasara a ser un simple si/no: si esa clave vieja tenia algo, se lee como
                // "si" una vez, sin migracion real - la clave vieja ya no se vuelve a escribir.
                supportsVision =
                    prefs[Keys.supportsVision] ?: !prefs[Keys.legacyVisionModel].isNullOrBlank(),
                maxIterations = prefs[Keys.maxIterations] ?: 8,
            )
        }

    override suspend fun setBaseUrl(baseUrl: String) = edit { it[Keys.baseUrl] = baseUrl }

    override suspend fun setModel(model: String) = edit { it[Keys.model] = model }

    override suspend fun setSupportsVision(value: Boolean) = edit {
        it[Keys.supportsVision] = value
    }

    override suspend fun setMaxIterations(value: Int) = edit {
        // 0 o menos es "sin limite", una eleccion deliberada desde Ajustes - no se recorta a un
        // minimo de 1 como antes. El techo de 128 evita un valor absurdo escrito a mano.
        it[Keys.maxIterations] = value.coerceAtMost(128)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.updateData { prefs -> prefs.toMutablePreferences().apply(block) }
    }

    private object Keys {
        val baseUrl = stringPreferencesKey("assistant:base_url")
        val model = stringPreferencesKey("assistant:model")
        val supportsVision = booleanPreferencesKey("assistant:supports_vision")
        /** Read-only leftover from when there were two models. Never written to anymore. */
        val legacyVisionModel = stringPreferencesKey("assistant:vision_model")
        val maxIterations = intPreferencesKey("assistant:max_iterations")
    }
}
