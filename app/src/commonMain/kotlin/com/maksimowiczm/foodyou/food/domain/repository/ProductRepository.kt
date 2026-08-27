package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun observeProduct(id: FoodId.Product): Flow<Product?>

    fun observeProducts(limit: Int, offset: Int): Flow<List<Product>>

    /** Full-text search returning a plain list. For callers that cannot page, like the assistant. */
    suspend fun searchProducts(query: String, limit: Int): List<Product>

    /**
     * @param name Name of the product.
     * @param brand Brand of the product, if available.
     * @param barcode Barcode of the product, if available.
     * @param note Additional note about the product, if available.
     * @param isLiquid Indicates whether the product is liquid (e.g., juice, milk).
     * @param packageWeight Weight of the product package, if available.
     * @param source Source of the product.
     * @param servingWeight Weight of a single serving of the product, if available.
     * @param nutritionFacts Nutrition facts of the product per 100g or 100ml, depending on whether
     *   the product is solid or liquid.
     */
    suspend fun insertProduct(
        name: String,
        brand: String?,
        barcode: String?,
        note: String?,
        isLiquid: Boolean,
        packageWeight: Double?,
        servingWeight: Double?,
        source: FoodSource,
        nutritionFacts: NutritionFacts,
        categories: List<String>? = null,
    ): FoodId.Product

    /**
     * Creates a new product only if a product with the same name, brand, and barcode does not
     * already exist.
     *
     * @return The ID of the newly created product, or null if a duplicate product exists.
     */
    suspend fun insertUniqueProduct(
        name: String,
        brand: String?,
        barcode: String?,
        note: String?,
        isLiquid: Boolean,
        packageWeight: Double?,
        servingWeight: Double?,
        source: FoodSource,
        nutritionFacts: NutritionFacts,
        categories: List<String>? = null,
    ): FoodId.Product?

    /**
     * Inserts the product, or refreshes the existing cached copy. Unlike [insertUniqueProduct] this
     * never returns null for a duplicate — live network searches need an id for every result they
     * show. [ProductUpsertResult.created] tells callers whether the row is new, so per-result
     * bookkeeping (history events) is not repeated on every search.
     */
    suspend fun insertOrRefreshProduct(
        name: String,
        brand: String?,
        barcode: String?,
        note: String?,
        isLiquid: Boolean,
        packageWeight: Double?,
        servingWeight: Double?,
        source: FoodSource,
        nutritionFacts: NutritionFacts,
        categories: List<String>? = null,
    ): ProductUpsertResult

    suspend fun updateProduct(product: Product)

    suspend fun deleteProduct(product: Product)

    suspend fun updateFavorite(productId: FoodId.Product, isFavorite: Boolean)

    /**
     * Deletes transient search-mirror products: remote-sourced rows the user never kept (not
     * favorite, not edited, never logged, not used in a recipe). Safe — the diary keeps its own
     * snapshots.
     *
     * @return the number of rows deleted.
     */
    suspend fun purgeStaleProducts(): Int
}

/** Result of [ProductRepository.insertOrRefreshProduct]. */
data class ProductUpsertResult(val id: FoodId.Product, val created: Boolean)
