package com.maksimowiczm.foodyou.food.infrastructure.customsource.model

import kotlinx.serialization.Serializable

/** PLACEHOLDER: see [CustomFoodProduct] for context on why this shape is provisional. */
@Serializable
data class CustomFoodPageResponse(
    val count: Int,
    val page: Int,
    val pageSize: Int,
    val products: List<CustomFoodProduct>,
)
