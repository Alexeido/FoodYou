package com.maksimowiczm.foodyou.assistant.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
                visionModel = prefs[Keys.visionModel].orEmpty(),
                maxIterations = prefs[Keys.maxIterations] ?: 8,
            )
        }

    override suspend fun setBaseUrl(baseUrl: String) = edit { it[Keys.baseUrl] = baseUrl }

    override suspend fun setModel(model: String) = edit { it[Keys.model] = model }

    override suspend fun setVisionModel(model: String) = edit { it[Keys.visionModel] = model }

    override suspend fun setMaxIterations(value: Int) = edit {
        it[Keys.maxIterations] = value.coerceIn(1, 20)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.updateData { prefs -> prefs.toMutablePreferences().apply(block) }
    }

    private object Keys {
        val baseUrl = stringPreferencesKey("assistant:base_url")
        val model = stringPreferencesKey("assistant:model")
        val visionModel = stringPreferencesKey("assistant:vision_model")
        val maxIterations = intPreferencesKey("assistant:max_iterations")
    }
}
