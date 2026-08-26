package com.maksimowiczm.foodyou.food.search.infrastructure

import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepositoryOf
import com.maksimowiczm.foodyou.food.domain.repository.FoodSearchHistoryRepository
import com.maksimowiczm.foodyou.food.search.domain.FoodRemoteMediatorFactoryAggregate
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchRepository
import com.maksimowiczm.foodyou.food.search.domain.ProductRemoteMediatorFactory
import com.maksimowiczm.foodyou.food.search.domain.CustomFoodSourceNetworkPagingSourceFactory
import com.maksimowiczm.foodyou.food.search.domain.OpenFoodFactsNetworkPagingSourceFactory
import com.maksimowiczm.foodyou.food.search.infrastructure.customsource.CustomFoodSourceNetworkPagingSourceFactoryImpl
import com.maksimowiczm.foodyou.food.search.infrastructure.customsource.CustomFoodSourceRemoteMediatorFactory
import com.maksimowiczm.foodyou.food.search.infrastructure.openfoodfacts.OpenFoodFactsNetworkPagingSourceFactoryImpl
import com.maksimowiczm.foodyou.food.search.infrastructure.openfoodfacts.OpenFoodFactsRemoteMediatorFactory
import com.maksimowiczm.foodyou.food.search.infrastructure.repository.DataStoreFoodSearchPreferencesRepository
import com.maksimowiczm.foodyou.food.search.infrastructure.repository.RoomFoodSearchHistoryRepository
import com.maksimowiczm.foodyou.food.search.infrastructure.repository.RoomFoodSearchRepository
import com.maksimowiczm.foodyou.food.search.infrastructure.room.FoodSearchDatabase
import com.maksimowiczm.foodyou.food.search.infrastructure.usda.USDARemoteMediatorFactory
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.scope.Scope
import org.koin.dsl.bind

fun Module.foodSearchInfrastructureModule() {
    factoryOf(::FoodRemoteMediatorFactoryAggregateImpl).bind<FoodRemoteMediatorFactoryAggregate>()

    factoryOf(::RoomFoodSearchHistoryRepository).bind<FoodSearchHistoryRepository>()
    factoryOf(::RoomFoodSearchRepository).bind<FoodSearchRepository>()

    userPreferencesRepositoryOf(::DataStoreFoodSearchPreferencesRepository)

    factory { database.foodSearchDao }
    factory { database.usdaPagingKeyDao }
    factory { database.openFoodFactsPagingKeyDao }
    factory { database.customFoodSourcePagingKeyDao }

    factoryOf(::OpenFoodFactsNetworkPagingSourceFactoryImpl).bind<OpenFoodFactsNetworkPagingSourceFactory>()
    factory {
            CustomFoodSourceNetworkPagingSourceFactoryImpl(
                preferencesRepository = userPreferencesRepository(),
                credentialsRepository = get(),
                remoteDataSource = get(),
                productRepository = get(),
                foodHistoryRepository = get(),
                transactionProvider = get(),
                productMapper = get(),
                remoteMapper = get(),
                dateProvider = get(),
                logger = get(),
            )
        }
        .bind<CustomFoodSourceNetworkPagingSourceFactory>()
    factoryOf(::OpenFoodFactsRemoteMediatorFactory).bind<ProductRemoteMediatorFactory>()
    factory {
            USDARemoteMediatorFactory(
                foodSearchPreferencesRepository = userPreferencesRepository(),
                transactionProvider = get(),
                productRepository = get(),
                historyRepository = get(),
                remoteDataSource = get(),
                pagingKeyDao = get(),
                usdaMapper = get(),
                remoteMapper = get(),
                dateProvider = get(),
                logger = get(),
            )
        }
        .bind<ProductRemoteMediatorFactory>()
    factory {
            CustomFoodSourceRemoteMediatorFactory(
                foodSearchPreferencesRepository = userPreferencesRepository(),
                credentialsRepository = get(),
                transactionProvider = get(),
                productRepository = get(),
                foodHistoryRepository = get(),
                remoteDataSource = get(),
                pagingKeyDao = get(),
                productMapper = get(),
                remoteMapper = get(),
                dateProvider = get(),
                logger = get(),
            )
        }
        .bind<ProductRemoteMediatorFactory>()
}

private val Scope.database: FoodSearchDatabase
    get() = get()
