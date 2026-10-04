package com.maksimowiczm.foodyou.food.search.domain

import androidx.paging.PagingSource

/**
 * Builds a paging source that queries the user's self-hosted server live, keeping the server's own
 * result ordering (the Room-mirror path re-sorts alphabetically and mixes in old cached products).
 */
interface CustomFoodSourceNetworkPagingSourceFactory {
    fun create(query: String): PagingSource<Int, FoodSearch>
}
