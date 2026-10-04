package com.maksimowiczm.foodyou.food.search.domain

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.RemoteMediator
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.event.EventBus
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.search.SearchQuery
import com.maksimowiczm.foodyou.common.domain.search.searchQuery
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.runBlocking

class FoodSearchUseCase(
    private val foodSearchRepository: FoodSearchRepository,
    private val foodSearchPreferencesRepository: UserPreferencesRepository<FoodSearchPreferences>,
    private val foodRemoteMediatorFactoryAggregate: FoodRemoteMediatorFactoryAggregate,
    private val openFoodFactsNetworkPagingSourceFactory: OpenFoodFactsNetworkPagingSourceFactory,
    private val customFoodSourceNetworkPagingSourceFactory: CustomFoodSourceNetworkPagingSourceFactory,
    private val eventBus: EventBus,
    private val dateProvider: DateProvider,
) {
    fun search(
        query: String?,
        source: FoodSource.Type,
        excludedRecipeId: FoodId.Recipe?,
        useAlternativeDb: Boolean = false,
    ): Flow<PagingData<FoodSearch>> {
        val query = searchQuery(query)

        if (query is SearchQuery.Text) {
            eventBus.publish(FoodSearchEvent(query, dateProvider.nowInstant()))
        }

        return foodSearchPreferencesRepository.observe().flatMapLatest { prefs ->
            // For OOF text queries: bypass Room round-trip with direct network PagingSource (V1).
            // Barcode queries use local-first (RemoteMediator) so cached products show instantly.
            if (source == FoodSource.Type.OpenFoodFacts && prefs.openFoodFacts.enabled) {
                when (query) {
                    is SearchQuery.Text ->
                        Pager(
                            config = PagingConfig(pageSize = PAGE_SIZE),
                            pagingSourceFactory = {
                                openFoodFactsNetworkPagingSourceFactory.create(query.query, useAlternativeDb)
                            },
                        ).flow
                    else ->
                        foodSearchRepository.search(
                            query = query,
                            source = source,
                            config = PagingConfig(pageSize = PAGE_SIZE),
                            remoteMediatorFactory = prefs.remoteMediatorFactory(source)?.wrap(query),
                            excludedRecipeId = excludedRecipeId,
                        )
                }
            } else if (
                source == FoodSource.Type.Custom &&
                    prefs.custom.enabled &&
                    query is SearchQuery.Text
            ) {
                // Text search on the user's own server streams live, in the server's own order.
                // Barcode lookups stay on the local-first path so cached products show instantly.
                Pager(
                        // initialLoadSize defaults to pageSize * 3, which would ask the server for
                        // 72 results before showing anything. The server pays a full upstream round
                        // trip proportional to that size (~0.6 s for 24, ~1 s for 72), so the first
                        // page asks only for what is displayed and the rest is prefetched remotely.
                        config =
                            PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE),
                        pagingSourceFactory = {
                            customFoodSourceNetworkPagingSourceFactory.create(query.query)
                        },
                    )
                    .flow
            } else {
                foodSearchRepository.search(
                    query = query,
                    source = source,
                    config = PagingConfig(pageSize = PAGE_SIZE),
                    remoteMediatorFactory = prefs.remoteMediatorFactory(source)?.wrap(query),
                    excludedRecipeId = excludedRecipeId,
                )
            }
        }
    }

    fun searchFavorites(
        query: String?,
        source: FoodSource.Type?,
        excludedRecipeId: FoodId.Recipe?,
    ): Flow<PagingData<FoodSearch>> {
        val query = searchQuery(query)

        if (query is SearchQuery.Text) {
            eventBus.publish(FoodSearchEvent(query, dateProvider.nowInstant()))
        }

        // Favorites are local-only; no remote mediator. `source == null` means all sources.
        return foodSearchRepository.favorites(
            query = query,
            source = source,
            config = PagingConfig(pageSize = PAGE_SIZE),
            excludedRecipeId = excludedRecipeId,
        )
    }

    fun searchRecent(
        query: String?,
        excludedRecipeId: FoodId.Recipe?,
    ): Flow<PagingData<FoodSearch>> {
        val query = searchQuery(query)

        if (query is SearchQuery.Text) {
            eventBus.publish(FoodSearchEvent(query, dateProvider.nowInstant()))
        }

        return foodSearchRepository.searchRecent(
            query = query,
            config = PagingConfig(pageSize = PAGE_SIZE),
            now = dateProvider.now(),
            excludedRecipeId = excludedRecipeId,
        )
    }

    private fun FoodSearchPreferences.remoteMediatorFactory(
        source: FoodSource.Type
    ): ProductRemoteMediatorFactory? =
        when (source) {
            FoodSource.Type.OpenFoodFacts if this.openFoodFacts.enabled ->
                foodRemoteMediatorFactoryAggregate.openFoodFactsRemoteMediatorFactory

            FoodSource.Type.USDA if this.usda.enabled ->
                foodRemoteMediatorFactoryAggregate.usdaRemoteMediatorFactory

            FoodSource.Type.Custom if this.custom.enabled ->
                foodRemoteMediatorFactoryAggregate.customRemoteMediatorFactory
            else -> null
        }

    @OptIn(ExperimentalPagingApi::class)
    private fun ProductRemoteMediatorFactory.wrap(query: SearchQuery): RemoteMediatorFactory =
        object : RemoteMediatorFactory {
            override fun <K : Any, T : Any> create(): RemoteMediator<K, T>? = runBlocking {
                this@wrap.create(query, PAGE_SIZE)
            }
        }

    private companion object {
        const val PAGE_SIZE = 24
    }
}
