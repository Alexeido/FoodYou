package com.maksimowiczm.foodyou.food.search.infrastructure.customsource

import androidx.paging.PagingSource
import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentialsRepository
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.customsource.CustomFoodSourceProductMapper
import com.maksimowiczm.foodyou.food.infrastructure.customsource.CustomFoodSourceRemoteDataSource
import com.maksimowiczm.foodyou.food.infrastructure.network.RemoteProductMapper
import com.maksimowiczm.foodyou.food.search.domain.CustomFoodSourceNetworkPagingSourceFactory
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences

internal class CustomFoodSourceNetworkPagingSourceFactoryImpl(
    private val preferencesRepository: UserPreferencesRepository<FoodSearchPreferences>,
    private val credentialsRepository: CustomFoodSourceCredentialsRepository,
    private val remoteDataSource: CustomFoodSourceRemoteDataSource,
    private val productRepository: ProductRepository,
    private val foodHistoryRepository: FoodHistoryRepository,
    private val transactionProvider: TransactionProvider,
    private val productMapper: CustomFoodSourceProductMapper,
    private val remoteMapper: RemoteProductMapper,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) : CustomFoodSourceNetworkPagingSourceFactory {
    override fun create(query: String): PagingSource<Int, FoodSearch> =
        CustomFoodSourceNetworkPagingSource(
            query = query,
            preferencesRepository = preferencesRepository,
            credentialsRepository = credentialsRepository,
            remoteDataSource = remoteDataSource,
            productRepository = productRepository,
            foodHistoryRepository = foodHistoryRepository,
            transactionProvider = transactionProvider,
            productMapper = productMapper,
            remoteMapper = remoteMapper,
            dateProvider = dateProvider,
            logger = logger,
        )
}
