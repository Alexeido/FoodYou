package com.maksimowiczm.foodyou.food.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts

/**
 * Represents a product in the food domain.
 *
 * @param id Unique identifier for the product.
 * @param name Name of the product.
 * @param brand Brand of the product, if available.
 * @param barcode Barcode of the product, if available.
 * @param note Additional note about the product, if available.
 * @param isLiquid Indicates whether the product is liquid (e.g., juice, milk).
 * @param packageWeight Weight of the product package, if available.
 * @param servingWeight Weight of a single serving of the product, if available.
 * @param source Source of the product.
 * @param nutritionFacts Nutrition facts of the product per 100g or 100ml, depending on whether the
 *   product is solid or liquid.
 */
data class Product(
    override val id: FoodId.Product,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val note: String?,
    override val isLiquid: Boolean,
    val packageWeight: Double?,
    override val servingWeight: Double?,
    val source: FoodSource,
    override val nutritionFacts: NutritionFacts,
    val categories: List<String>? = null,
    val isFavorite: Boolean = false,
    /**
     * Immutable EAN as it comes from the source; identity anchor for dedup and refresh. `null` for
     * user-created products. May differ from [barcode] once the user overrides the visible code.
     */
    val sourceBarcode: String? = null,
    /** True when the user hand-edited this product (durable: protected from the mirror purge). */
    val isEdited: Boolean = false,
) : Food {
    override val totalWeight: Double? = packageWeight

    override val headline = run {
        val brandSuffix =
            if (!brand.isNullOrEmpty()) {
                " ($brand)"
            } else {
                ""
            }

        "${name}$brandSuffix"
    }
}
