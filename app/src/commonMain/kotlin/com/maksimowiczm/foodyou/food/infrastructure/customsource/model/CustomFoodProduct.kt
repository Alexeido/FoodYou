package com.maksimowiczm.foodyou.food.infrastructure.customsource.model

import com.maksimowiczm.foodyou.food.domain.entity.RemoteNutritionFacts
import kotlinx.serialization.Serializable

/**
 * PLACEHOLDER: the custom food source's real API contract has not been designed yet (it will be
 * defined in a separate, companion repository for the self-hosted server). This shape assumes the
 * server returns [RemoteNutritionFacts]-compatible JSON directly. Only this file and
 * [CustomFoodPageResponse] should need to change once the real contract exists — everything
 * downstream (mapper, mediator, DI, UI) is written against domain types and shouldn't need to.
 */
@Serializable
data class CustomFoodProduct(
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val packageWeight: Double? = null,
    val servingWeight: Double? = null,
    val isLiquid: Boolean = false,
    val categories: List<String>? = null,
    val url: String? = null,
    val nutritionFacts: RemoteNutritionFacts? = null,
)
