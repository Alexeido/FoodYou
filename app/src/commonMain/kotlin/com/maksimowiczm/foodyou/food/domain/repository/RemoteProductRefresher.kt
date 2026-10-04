package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.food.domain.entity.Product

/**
 * Fetches fresh product data from a remote source by its source EAN, for the on-demand
 * "refresh from source" action.
 *
 * Implementations resolve the source's connection details (base URL, credentials) themselves.
 * Only some sources are supported — [fetch] returns `null` for unsupported ones. Currently only the
 * user's Custom self-hosted source is wired; OFF/USDA can be added later.
 */
interface RemoteProductRefresher {
    /**
     * @param source The source to query.
     * @param sourceBarcode The immutable source EAN to look the product up by.
     * @return the fresh product (with a placeholder id), or `null` if the source is unsupported, not
     *   configured, or the product is not found there.
     * @throws com.maksimowiczm.foodyou.food.domain.entity.RemoteFoodException on auth/network errors.
     */
    suspend fun fetch(source: FoodSource.Type, sourceBarcode: String): Product?
}
