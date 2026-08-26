package com.maksimowiczm.foodyou.food.infrastructure.customsource

import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentialsRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences
import kotlinx.coroutines.flow.first

/**
 * PLACEHOLDER: assumes paste-URL import links look like `{baseUrl}/product/{barcode}`. The real
 * URL convention will be defined alongside the server's API contract in the companion repo — only
 * [extractBarcode]'s regex should need to change.
 *
 * Unlike [com.maksimowiczm.foodyou.food.infrastructure.openfoodfacts.OpenFoodFactsFacade] or
 * `USDAFacade`, [matches] here is `suspend` because it depends on the user's configured base URL
 * rather than a fixed, compiled-in domain.
 */
internal class CustomFoodSourceFacade(
    private val dataSource: CustomFoodSourceRemoteDataSource,
    private val mapper: CustomFoodSourceProductMapper,
    private val preferencesRepository: UserPreferencesRepository<FoodSearchPreferences>,
    private val credentialsRepository: CustomFoodSourceCredentialsRepository,
    private val logger: Logger,
) {
    /** Extracts the barcode from a given custom food source product URL. */
    fun extractBarcode(url: String): String? =
        try {
            productUrlRegex.find(url)?.groups?.get(1)?.value
        } catch (e: Exception) {
            logger.w(TAG) { "Failed to extract barcode from URL: $url" }
            null
        }

    /** Creates a request to fetch product details from the custom food source. */
    fun createRequest(barcode: String) =
        CustomFoodSourceProductRequest(
            dataSource = dataSource,
            barcode = barcode,
            mapper = mapper,
            preferencesRepository = preferencesRepository,
            credentialsRepository = credentialsRepository,
        )

    /** Checks if the given URL points at the user's configured custom food source. */
    suspend fun matches(url: String): Boolean {
        val baseUrl = preferencesRepository.observe().first().custom.baseUrl
        return !baseUrl.isNullOrBlank() && url.startsWith(baseUrl)
    }

    private companion object {
        private const val TAG = "CustomFoodSourceFacade"
    }
}

private val productUrlRegex by lazy { Regex("/product/(\\w+)/*$") }
