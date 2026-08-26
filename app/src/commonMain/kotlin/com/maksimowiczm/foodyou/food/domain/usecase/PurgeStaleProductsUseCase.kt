package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository

/**
 * Removes transient search-mirror products the user never kept, so browsing a database section
 * doesn't fill with thousands of one-off search results. Intended to run once at app startup.
 *
 * Safe: the diary keeps embedded snapshots, favorites/edited/logged/recipe-referenced products are
 * protected, and only re-fetchable remote sources are eligible (see
 * [ProductRepository.purgeStaleProducts]).
 */
class PurgeStaleProductsUseCase(
    private val productRepository: ProductRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke() {
        val deleted = productRepository.purgeStaleProducts()
        logger.d(TAG) { "Purged $deleted stale mirror products" }
    }

    private companion object {
        const val TAG = "PurgeStaleProductsUseCase"
    }
}
