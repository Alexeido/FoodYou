package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.maksimowiczm.foodyou.app.ui.food.search.RemoteStatus.Companion.toRemoteStatus
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.search.searchQuery
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.extension.combine
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FoodSearchHistoryRepository
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchRepository
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchUseCase
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class FoodSearchViewModel(
    private val excludedRecipeId: FoodId.Recipe?,
    private val foodSearchPreferencesRepository: UserPreferencesRepository<FoodSearchPreferences>,
    searchHistoryRepository: FoodSearchHistoryRepository,
    private val foodSearchRepository: FoodSearchRepository,
    private val productRepository: com.maksimowiczm.foodyou.food.domain.repository.ProductRepository,
    private val recipeRepository: com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository,
    private val foodSearchUseCase: FoodSearchUseCase,
    private val dateProvider: DateProvider,
) : ViewModel() {

    // Use shared flow to allow emitting same value multiple times
    private val searchQuery =
        MutableSharedFlow<String?>(replay = 1).apply { runBlocking { emit(null) } }

    // Debounced query for network-backed sources (OOF, USDA) to avoid wasting the 10 req/min limit
    private val debouncedSearchQuery = searchQuery.debounce(300L)

    // For OOF specifically: barcodes (all-digit queries) skip debounce — they arrive complete from scanner
    private val openFoodFactsSearchQuery = searchQuery.debounce { query ->
        if (query != null && query.all(Char::isDigit)) 0L else 300L
    }

    private val filter = MutableStateFlow(FoodFilter())

    private val _useAlternativeDb = MutableStateFlow(false)

    fun searchOnAlternativeDb() {
        _useAlternativeDb.value = true
    }

    fun search(query: String?) {
        _useAlternativeDb.value = false
        viewModelScope.launch { searchQuery.emit(query) }
    }

    fun changeSource(source: FoodFilter.Source) {
        filter.update { it.copy(source = source) }
    }

    fun setFavoritesOnly(enabled: Boolean) {
        filter.update { it.copy(favorites = enabled) }
    }

    private val foodPreferences =
        foodSearchPreferencesRepository
            .observe()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = runBlocking { foodSearchPreferencesRepository.observe().first() },
            )

    private val recentFoodPages =
        combine(searchQuery, filter) { q, f -> Pair(q, f) }
            .flatMapLatest { (query, currentFilter) ->
                if (currentFilter.favorites) {
                    foodSearchUseCase.searchFavorites(query, null, excludedRecipeId)
                } else {
                    foodSearchUseCase.searchRecent(query, excludedRecipeId)
                }
            }.cachedIn(viewModelScope)
    private val recentFoodState =
        combine(searchQuery, filter) { q, f -> Pair(q, f) }
            .flatMapLatest { (query, currentFilter) ->
                val q2 = searchQuery(query)
                if (currentFilter.favorites) {
                    foodSearchRepository.favoritesCount(
                        query = q2,
                        source = null,
                        excludedRecipeId = excludedRecipeId,
                    )
                } else {
                    foodSearchRepository.searchRecentFoodCount(
                        query = q2,
                        now = dateProvider.now(),
                        excludedRecipeId = excludedRecipeId,
                    )
                }
            }
            .map { count ->
                FoodSourceUiState(
                    remoteEnabled = RemoteStatus.LocalOnly,
                    pages = recentFoodPages,
                    count = count,
                    alwaysShowFilter = true,
                )
            }

    private val yourFoodPages = observeFoodPages(FoodSource.Type.User).cachedIn(viewModelScope)
    private val yourFoodState =
        observeFoodCount(FoodSource.Type.User).map { count ->
            FoodSourceUiState(
                remoteEnabled = RemoteStatus.LocalOnly,
                pages = yourFoodPages,
                count = count,
                alwaysShowFilter = true,
            )
        }

    private val openFoodFactsPages =
        combine(
            openFoodFactsSearchQuery,
            filter,
            _useAlternativeDb,
        ) { query, currentFilter, useAlt -> Triple(query, currentFilter, useAlt) }
            .flatMapLatest { (query, currentFilter, useAlt) ->
                if (currentFilter.favorites) {
                    foodSearchUseCase.searchFavorites(query, null, excludedRecipeId)
                } else {
                    foodSearchUseCase.search(
                        query,
                        FoodSource.Type.OpenFoodFacts,
                        excludedRecipeId,
                        useAlt,
                    )
                }
            }.cachedIn(viewModelScope)
    private val openFoodFactsState =
        combine(observeFoodCount(FoodSource.Type.OpenFoodFacts), foodPreferences) { count, prefs ->
            FoodSourceUiState(
                remoteEnabled = prefs.isOpenFoodFactsEnabled.toRemoteStatus(),
                pages = openFoodFactsPages,
                count = count,
            )
        }

    private val usdaPages = observeFoodPages(FoodSource.Type.USDA, debounced = true).cachedIn(viewModelScope)
    private val usdaState =
        combine(observeFoodCount(FoodSource.Type.USDA), foodPreferences) { count, prefs ->
            FoodSourceUiState(
                remoteEnabled = prefs.isUsdaEnabled.toRemoteStatus(),
                pages = usdaPages,
                count = count,
            )
        }

    private val swissPages =
        observeFoodPages(FoodSource.Type.SwissFoodCompositionDatabase).cachedIn(viewModelScope)
    private val swissState =
        observeFoodCount(FoodSource.Type.SwissFoodCompositionDatabase).map { count ->
            FoodSourceUiState(
                remoteEnabled = RemoteStatus.LocalOnly,
                pages = swissPages,
                count = count,
            )
        }

    private val customPages =
        observeFoodPages(FoodSource.Type.Custom, debounced = true).cachedIn(viewModelScope)
    private val customState =
        combine(observeFoodCount(FoodSource.Type.Custom), foodPreferences) { count, prefs ->
            FoodSourceUiState(
                remoteEnabled = prefs.isCustomEnabled.toRemoteStatus(),
                pages = customPages,
                count = count,
            )
        }

    private fun observeFoodCount(source: FoodSource.Type) =
        combine(searchQuery, filter) { query, currentFilter -> Pair(query, currentFilter) }
            .flatMapLatest { (query, currentFilter) ->
                val q = searchQuery(query)

                if (currentFilter.favorites) {
                    // Use source-specific count so disabled sources (USDA, Swiss) stay hidden
                    foodSearchRepository.favoritesCount(
                        query = q,
                        source = source,
                        excludedRecipeId = excludedRecipeId,
                    )
                } else {
                    foodSearchRepository.searchFoodCount(
                        query = q,
                        source = source,
                        excludedRecipeId = excludedRecipeId,
                    )
                }
            }

    private fun observeFoodPages(source: FoodSource.Type, debounced: Boolean = false) =
        combine(
            if (debounced) debouncedSearchQuery else searchQuery,
            filter,
        ) { query, currentFilter -> Pair(query, currentFilter) }
            .flatMapLatest { (query, currentFilter) ->
                if (currentFilter.favorites) {
                    // Show favorites across all sources when favorites mode is enabled
                    foodSearchUseCase.searchFavorites(query, null, excludedRecipeId)
                } else {
                    foodSearchUseCase.search(query, source, excludedRecipeId)
                }
            }

    private val searchHistory =
        searchHistoryRepository
            .observeHistory(limit = 10)
            .map { list -> list.map { it.query } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )

    // Build base ui state (without favoritesCount) then combine with favorites count flow
    private val baseUiState =
        combine(
                recentFoodState,
                yourFoodState,
                openFoodFactsState,
                usdaState,
                swissState,
                customState,
                filter,
                searchHistory,
            ) {
                recentFoodState,
                yourFoodState,
                openFoodFactsState,
                usdaState,
                swissState,
                customState,
                filter,
                searchHistory ->
                FoodSearchUiState(
                    sources =
                        mapOf(
                            FoodFilter.Source.Recent to recentFoodState,
                            FoodFilter.Source.YourFood to yourFoodState,
                            FoodFilter.Source.OpenFoodFacts to openFoodFactsState,
                            FoodFilter.Source.USDA to usdaState,
                            FoodFilter.Source.SwissFoodCompositionDatabase to swissState,
                            FoodFilter.Source.Custom to customState,
                        ),
                    filter = filter,
                    recentSearches = searchHistory.map { it.query },
                    favoritesCount = 0,
                )
            }

    private val favoritesCountFlow =
        searchQuery.flatMapLatest { q ->
            val q2 = searchQuery(q)
            // Aggregate favorites across all sources
            foodSearchRepository.favoritesCount(q2, source = null, excludedRecipeId = excludedRecipeId)
        }

    val uiState =
        combine(baseUiState, favoritesCountFlow, foodPreferences) { base, favCount, prefs ->
                base.copy(
                    favoritesCount = favCount,
                    enabledRemoteSources = prefs.enabledRemoteSources,
                    primarySource = prefs.effectivePrimarySource,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = FoodSearchUiState(
                    sources = emptyMap(),
                    filter = FoodFilter(),
                    recentSearches = emptyList(),
                    favoritesCount = 0,
                ),
            )
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue =
                    FoodSearchUiState(
                        sources = emptyMap(),
                        filter = FoodFilter(),
                        recentSearches = emptyList(),
                    ),
            )

    // Note: automatic source-switching on search was removed intentionally. With the tabbed UI the
    // user explicitly controls the active tab; typing filters the current tab and pressing search
    // jumps to the database tab (handled in the UI).

    fun toggleFavorite(foodId: FoodId, newState: Boolean) {
        viewModelScope.launch {
            when (foodId) {
                is FoodId.Product -> productRepository.updateFavorite(foodId, newState)
                is FoodId.Recipe -> recipeRepository.updateFavorite(foodId, newState)
            }
        }
    }
}

@OptIn(ExperimentalContracts::class)
private fun Int?.positive(): Boolean {
    contract { returns(true) implies (this@positive != null) }

    return this != null && this > 0
}
