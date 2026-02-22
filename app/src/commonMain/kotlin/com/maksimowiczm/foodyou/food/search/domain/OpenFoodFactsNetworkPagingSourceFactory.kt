package com.maksimowiczm.foodyou.food.search.domain

import androidx.paging.PagingSource

interface OpenFoodFactsNetworkPagingSourceFactory {
    fun create(query: String, useAlternativeDb: Boolean = false): PagingSource<Int, FoodSearch>
    fun createForBarcode(barcode: String, useAlternativeDb: Boolean = false): PagingSource<Int, FoodSearch>
}
