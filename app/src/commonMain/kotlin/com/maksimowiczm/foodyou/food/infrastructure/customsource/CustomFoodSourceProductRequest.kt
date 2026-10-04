package com.maksimowiczm.foodyou.food.infrastructure.customsource

import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentialsRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.RemoteFoodException
import com.maksimowiczm.foodyou.food.domain.entity.RemoteProduct
import com.maksimowiczm.foodyou.food.domain.entity.RemoteProductRequest
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences
import kotlinx.coroutines.flow.first

internal class CustomFoodSourceProductRequest(
    private val dataSource: CustomFoodSourceRemoteDataSource,
    private val barcode: String,
    private val mapper: CustomFoodSourceProductMapper,
    private val preferencesRepository: UserPreferencesRepository<FoodSearchPreferences>,
    private val credentialsRepository: CustomFoodSourceCredentialsRepository,
) : RemoteProductRequest {
    override suspend fun execute(): Result<RemoteProduct, RemoteFoodException> {
        val baseUrl = preferencesRepository.observe().first().custom.baseUrl
        val credentials = credentialsRepository.observeCredentials().first()

        if (baseUrl.isNullOrBlank() || credentials == null) {
            return Err(RemoteFoodException.Custom.NotConfigured())
        }

        return dataSource
            .getProduct(
                barcode = barcode,
                baseUrl = baseUrl,
                username = credentials.username,
                password = credentials.password,
            )
            .map(mapper::toRemoteProduct)
            .fold(onSuccess = ::Ok, onFailure = { Err(RemoteFoodException.fromThrowable(it)) })
    }
}
