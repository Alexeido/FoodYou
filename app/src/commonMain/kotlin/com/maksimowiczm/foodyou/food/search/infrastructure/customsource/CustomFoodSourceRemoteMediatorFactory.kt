package com.maksimowiczm.foodyou.food.search.infrastructure.customsource

import androidx.paging.ExperimentalPagingApi
import androidx.paging.RemoteMediator
import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentialsRepository
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.search.SearchQuery
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.customsource.CustomFoodSourceProductMapper
import com.maksimowiczm.foodyou.food.infrastructure.customsource.CustomFoodSourceRemoteDataSource
import com.maksimowiczm.foodyou.food.infrastructure.network.RemoteProductMapper
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences
import com.maksimowiczm.foodyou.food.search.domain.ProductRemoteMediatorFactory
import com.maksimowiczm.foodyou.food.search.infrastructure.room.CustomFoodSourcePagingKeyDao
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalPagingApi::class)
internal class CustomFoodSourceRemoteMediatorFactory(
    private val foodSearchPreferencesRepository: UserPreferencesRepository<FoodSearchPreferences>,
    private val credentialsRepository: CustomFoodSourceCredentialsRepository,
    private val transactionProvider: TransactionProvider,
    private val productRepository: ProductRepository,
    private val foodHistoryRepository: FoodHistoryRepository,
    private val remoteDataSource: CustomFoodSourceRemoteDataSource,
    private val pagingKeyDao: CustomFoodSourcePagingKeyDao,
    private val productMapper: CustomFoodSourceProductMapper,
    private val remoteMapper: RemoteProductMapper,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) : ProductRemoteMediatorFactory {
    override suspend fun <K : Any, T : Any> create(
        query: SearchQuery,
        pageSize: Int,
    ): RemoteMediator<K, T>? {
        if (query !is SearchQuery.NotBlank) {
            return null
        }

        val baseUrl = foodSearchPreferencesRepository.observe().first().custom.baseUrl
        if (baseUrl.isNullOrBlank()) {
            return null
        }

        val credentials = credentialsRepository.observeCredentials().first() ?: return null

        return CustomFoodSourceRemoteMediator(
            query = query.query,
            isBarcode = query is SearchQuery.Barcode,
            baseUrl = baseUrl,
            username = credentials.username,
            password = credentials.password,
            transactionProvider = transactionProvider,
            productRepository = productRepository,
            foodHistoryRepository = foodHistoryRepository,
            remoteDataSource = remoteDataSource,
            pagingKeyDao = pagingKeyDao,
            productMapper = productMapper,
            remoteMapper = remoteMapper,
            dateProvider = dateProvider,
            logger = logger,
        )
    }
}
