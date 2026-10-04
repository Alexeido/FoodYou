package com.maksimowiczm.foodyou.common.domain.customsource

import kotlinx.coroutines.flow.Flow

interface CustomFoodSourceCredentialsRepository {
    suspend fun saveCredentials(credentials: CustomFoodSourceCredentials)

    fun observeCredentials(): Flow<CustomFoodSourceCredentials?>

    suspend fun clearCredentials()
}
