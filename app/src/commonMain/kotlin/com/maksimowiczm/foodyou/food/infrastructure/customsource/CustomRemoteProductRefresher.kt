package com.maksimowiczm.foodyou.food.infrastructure.customsource

import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentialsRepository
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.RemoteFoodException
import com.maksimowiczm.foodyou.food.domain.repository.RemoteProductRefresher
import com.maksimowiczm.foodyou.food.infrastructure.network.RemoteProductMapper
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences
import kotlinx.coroutines.flow.first

/**
 * [RemoteProductRefresher] for the user's Custom self-hosted source. Resolves base URL +
 * credentials the same way [com.maksimowiczm.foodyou.food.search.infrastructure.customsource.CustomFoodSourceRemoteMediatorFactory]
 * does, looks the product up by exact EAN, and maps the response to a domain [Product].
 */
internal class CustomRemoteProductRefresher(
    private val preferencesRepository: UserPreferencesRepository<FoodSearchPreferences>,
    private val credentialsRepository: CustomFoodSourceCredentialsRepository,
    private val remoteDataSource: CustomFoodSourceRemoteDataSource,
    private val productMapper: CustomFoodSourceProductMapper,
    private val remoteMapper: RemoteProductMapper,
) : RemoteProductRefresher {
    override suspend fun fetch(source: FoodSource.Type, sourceBarcode: String): Product? {
        if (source != FoodSource.Type.Custom) return null

        val baseUrl =
            preferencesRepository.observe().first().custom.baseUrl?.takeIf { it.isNotBlank() }
                ?: return null
        val credentials = credentialsRepository.observeCredentials().first() ?: return null

        val custom =
            remoteDataSource
                .getProduct(
                    barcode = sourceBarcode,
                    baseUrl = baseUrl,
                    username = credentials.username,
                    password = credentials.password,
                )
                .getOrElse {
                    if (it is RemoteFoodException.ProductNotFoundException) return null else throw it
                }

        return runCatching { custom.let(productMapper::toRemoteProduct).let(remoteMapper::toModel) }
            .getOrNull()
    }
}
