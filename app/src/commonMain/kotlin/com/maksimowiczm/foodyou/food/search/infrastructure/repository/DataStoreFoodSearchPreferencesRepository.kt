package com.maksimowiczm.foodyou.food.search.infrastructure.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.infrastructure.datastore.AbstractDataStoreUserPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.datastore.set
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences

internal class DataStoreFoodSearchPreferencesRepository(dataStore: DataStore<Preferences>) :
    AbstractDataStoreUserPreferencesRepository<FoodSearchPreferences>(dataStore) {
    override fun Preferences.toUserPreferences(): FoodSearchPreferences =
        FoodSearchPreferences(
            openFoodFacts =
                FoodSearchPreferences.OpenFoodFacts(
                    enabled = this[FoodPreferencesKeys.UseOpenFoodFacts] ?: false
                ),
            usda =
                FoodSearchPreferences.Usda(
                    enabled = this[FoodPreferencesKeys.UseUsda] ?: false,
                    apiKey = this[FoodPreferencesKeys.UsdaApiKey],
                ),
            custom =
                FoodSearchPreferences.Custom(
                    enabled = this[FoodPreferencesKeys.UseCustom] ?: false,
                    baseUrl = this[FoodPreferencesKeys.CustomBaseUrl],
                ),
            primarySource =
                this[FoodPreferencesKeys.PrimarySource]?.let { name ->
                    FoodSource.Type.entries.firstOrNull { it.name == name }
                },
        )

    override fun MutablePreferences.applyUserPreferences(updated: FoodSearchPreferences) {
        this[FoodPreferencesKeys.UseOpenFoodFacts] = updated.openFoodFacts.enabled
        this[FoodPreferencesKeys.UseUsda] = updated.usda.enabled
        this[FoodPreferencesKeys.UsdaApiKey] = updated.usda.apiKey
        this[FoodPreferencesKeys.UseCustom] = updated.custom.enabled
        this[FoodPreferencesKeys.CustomBaseUrl] = updated.custom.baseUrl
        this[FoodPreferencesKeys.PrimarySource] = updated.primarySource?.name
    }
}

private object FoodPreferencesKeys {
    val UseOpenFoodFacts = booleanPreferencesKey("food:use_open_food_facts")
    val UseUsda = booleanPreferencesKey("food:use_usda")
    val UsdaApiKey = stringPreferencesKey("food:usda_api_key")
    val UseCustom = booleanPreferencesKey("food:use_custom")
    val CustomBaseUrl = stringPreferencesKey("food:custom_base_url")
    val PrimarySource = stringPreferencesKey("food:primary_source")
}
