package com.maksimowiczm.foodyou.food.search.domain

import androidx.paging.PagingSource

interface OpenFoodFactsNetworkPagingSourceFactory {
    fun create(query: String): PagingSource<Int, FoodSearch>
}
