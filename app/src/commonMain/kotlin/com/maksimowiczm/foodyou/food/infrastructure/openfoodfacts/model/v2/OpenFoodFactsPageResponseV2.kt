package com.maksimowiczm.foodyou.food.infrastructure.openfoodfacts.model.v2

import com.maksimowiczm.foodyou.food.infrastructure.openfoodfacts.model.OpenFoodPageResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenFoodFactsPageResponseV2(
    @SerialName("count") override val count: Int,
    @SerialName("page") override val page: Int,
    @SerialName("page_size") override val pageSize: Int,
    @SerialName("products") override val products: List<OpenFoodFactsProductV2>,
) : OpenFoodPageResponse
