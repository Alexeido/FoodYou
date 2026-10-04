package com.maksimowiczm.foodyou.food.infrastructure.customsource

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.food.domain.entity.RemoteProduct
import com.maksimowiczm.foodyou.food.infrastructure.customsource.model.CustomFoodProduct

internal class CustomFoodSourceProductMapper {
    fun toRemoteProduct(product: CustomFoodProduct): RemoteProduct =
        RemoteProduct(
            name = product.name.trim(),
            brand = product.brand?.trim(),
            barcode = product.barcode?.trim(),
            nutritionFacts = product.nutritionFacts,
            packageWeight = product.packageWeight,
            servingWeight = product.servingWeight,
            source = FoodSource(type = FoodSource.Type.Custom, url = product.url),
            isLiquid = product.isLiquid,
            categories = product.categories,
        )
}
