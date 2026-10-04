package com.maksimowiczm.foodyou.app.ui.database.externaldatabases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentials
import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentialsRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class UpdateCustomFoodSourceDialogViewModel(
    private val foodSearchPreferencesRepository: UserPreferencesRepository<FoodSearchPreferences>,
    private val credentialsRepository: CustomFoodSourceCredentialsRepository,
) : ViewModel() {

    val initialValues =
        combine(foodSearchPreferencesRepository.observe(), credentialsRepository.observeCredentials()) {
                prefs,
                credentials ->
                CustomFoodSourceFormValues(
                    baseUrl = prefs.custom.baseUrl ?: "",
                    username = credentials?.username ?: "",
                    password = credentials?.password ?: "",
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    fun save(baseUrl: String, username: String, password: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            val wasEnabled = foodSearchPreferencesRepository.observe().first().custom.enabled

            foodSearchPreferencesRepository.update {
                copy(custom = custom.copy(enabled = wasEnabled, baseUrl = baseUrl))
            }
            credentialsRepository.saveCredentials(
                CustomFoodSourceCredentials(username = username, password = password)
            )

            onSaved()
        }
    }
}

internal data class CustomFoodSourceFormValues(
    val baseUrl: String,
    val username: String,
    val password: String,
)
