package com.maksimowiczm.foodyou.food.search.infrastructure.openfoodfacts

import androidx.paging.PagingSource
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.network.RemoteProductMapper
import com.maksimowiczm.foodyou.food.infrastructure.openfoodfacts.OpenFoodFactsProductMapper
import com.maksimowiczm.foodyou.food.infrastructure.openfoodfacts.OpenFoodFactsRemoteDataSource
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.food.search.domain.OpenFoodFactsNetworkPagingSourceFactory

internal class OpenFoodFactsNetworkPagingSourceFactoryImpl(
    private val remoteDataSource: OpenFoodFactsRemoteDataSource,
    private val productRepository: ProductRepository,
    private val foodHistoryRepository: FoodHistoryRepository,
    private val offMapper: OpenFoodFactsProductMapper,
    private val remoteMapper: RemoteProductMapper,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) : OpenFoodFactsNetworkPagingSourceFactory {
    override fun create(query: String, useAlternativeDb: Boolean): PagingSource<Int, FoodSearch> =
        OpenFoodFactsNetworkPagingSource(
            query = query,
            country = null,
            remoteDataSource = remoteDataSource,
            productRepository = productRepository,
            foodHistoryRepository = foodHistoryRepository,
            offMapper = offMapper,
            remoteMapper = remoteMapper,
            dateProvider = dateProvider,
            logger = logger,
            baseUrl = if (useAlternativeDb) OpenFoodFactsRemoteDataSource.API_URL_ALT
                      else OpenFoodFactsRemoteDataSource.API_URL,
        )

    override fun createForBarcode(barcode: String, useAlternativeDb: Boolean): PagingSource<Int, FoodSearch> =
        OpenFoodFactsBarcodePagingSource(
            barcode = barcode,
            country = null,
            remoteDataSource = remoteDataSource,
            productRepository = productRepository,
            foodHistoryRepository = foodHistoryRepository,
            offMapper = offMapper,
            remoteMapper = remoteMapper,
            dateProvider = dateProvider,
            logger = logger,
            baseUrl = if (useAlternativeDb) OpenFoodFactsRemoteDataSource.API_URL_ALT
                      else OpenFoodFactsRemoteDataSource.API_URL,
        )
}
