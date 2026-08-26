package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.RemoteFoodException
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RemoteProductRefresher
import kotlinx.coroutines.flow.first

sealed interface RefreshProductResult {
    /** Macros and metadata were refreshed; the user's edited barcode was preserved. */
    data object Updated : RefreshProductResult

    /** The product id does not exist locally. */
    data object ProductNotFound : RefreshProductResult

    /** The product has no source EAN to look up (e.g. a user-created product). */
    data object NoSource : RefreshProductResult

    /** The source no longer has a product for this EAN. */
    data object NotFoundOnSource : RefreshProductResult

    /** Credentials were rejected by the source. */
    data object Unauthorized : RefreshProductResult

    /** Any other network/parse failure. */
    data object Error : RefreshProductResult
}

/**
 * Refreshes a single product's data from its source, on explicit user action.
 *
 * Looks the product up by its immutable [sourceBarcode][com.maksimowiczm.foodyou.food.domain.entity.Product.sourceBarcode]
 * (never by the user's possibly-overridden visible barcode), overwrites name/brand/macros/serving/
 * categories, and **preserves** the user's `barcode`, `note`, `isFavorite` and `isEdited`. Never
 * touches diary history (that's an immutable snapshot).
 */
class RefreshProductUseCase(
    private val productRepository: ProductRepository,
    private val refresher: RemoteProductRefresher,
    private val logger: Logger,
) {
    suspend fun refresh(id: FoodId.Product): RefreshProductResult {
        val existing =
            productRepository.observeProduct(id).first() ?: return RefreshProductResult.ProductNotFound

        val anchor = existing.sourceBarcode ?: existing.barcode ?: return RefreshProductResult.NoSource

        val fresh =
            try {
                refresher.fetch(existing.source.type, anchor)
            } catch (e: RemoteFoodException.Custom.Unauthorized) {
                logger.w(TAG) { "Refresh unauthorized for product ${id.id}" }
                return RefreshProductResult.Unauthorized
            } catch (e: RemoteFoodException) {
                logger.e(TAG, e) { "Refresh failed for product ${id.id}" }
                return RefreshProductResult.Error
            } ?: return RefreshProductResult.NotFoundOnSource

        val updated =
            existing.copy(
                name = fresh.name,
                brand = fresh.brand,
                // Keep the user's visible barcode; re-anchor identity to the source's current EAN.
                sourceBarcode = fresh.barcode ?: existing.sourceBarcode,
                nutritionFacts = fresh.nutritionFacts,
                packageWeight = fresh.packageWeight,
                servingWeight = fresh.servingWeight,
                isLiquid = fresh.isLiquid,
                categories = fresh.categories,
                source = existing.source.copy(url = fresh.source.url ?: existing.source.url),
            )

        productRepository.updateProduct(updated)
        return RefreshProductResult.Updated
    }

    private companion object {
        const val TAG = "RefreshProductUseCase"
    }
}
