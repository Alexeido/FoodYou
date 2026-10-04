package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.itemKey
import com.maksimowiczm.foodyou.app.ui.common.component.FoodListItemSkeleton
import com.maksimowiczm.foodyou.app.ui.common.component.FullScreenCameraBarcodeScanner
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.extension.error
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.RemoteFoodException
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import foodyou.app.generated.resources.*
import kotlinx.coroutines.flow.collect
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FoodSearchApp(
    onFoodClick: (FoodSearch, Measurement) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    modifier: Modifier = Modifier,
    excludedRecipe: FoodId.Recipe? = null,
    targetMealId: Long? = null,
    targetDate: LocalDate? = null,
) {
    val viewModel: FoodSearchViewModel = koinViewModel { parametersOf(excludedRecipe) }

    FoodSearchApp(
        uiState = viewModel.uiState.collectAsStateWithLifecycle().value,
        onSearch = viewModel::search,
        onSourceChange = viewModel::changeSource,
        onFavoritesChange = viewModel::setFavoritesOnly,
        onToggleFavorite = viewModel::toggleFavorite,
        onFoodClick = onFoodClick,
        onUpdateUsdaApiKey = onUpdateUsdaApiKey,
        onAlternativeDb = viewModel::searchOnAlternativeDb,
        modifier = modifier,
        targetMealId = targetMealId,
        targetDate = targetDate,
    )
}

@Composable
private fun FoodSearchApp(
    uiState: FoodSearchUiState,
    onSearch: (String?) -> Unit,
    onSourceChange: (FoodFilter.Source) -> Unit,
    onFavoritesChange: (Boolean) -> Unit,
    onToggleFavorite: (FoodId, Boolean) -> Unit,
    onFoodClick: (FoodSearch, Measurement) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onAlternativeDb: () -> Unit,
    modifier: Modifier = Modifier,
    targetMealId: Long? = null,
    targetDate: LocalDate? = null,
    appState: FoodSearchAppState = rememberFoodSearchAppState(),
) {
    val remoteFilterSources =
        remember(uiState.enabledRemoteSources) {
            uiState.enabledRemoteSources.map { it.toFilterSource() }
        }
    var pickedDbSource by remember { mutableStateOf<FoodFilter.Source?>(null) }
    val activeDbSource: FoodFilter.Source? =
        when {
            !uiState.filter.favorites && uiState.filter.source in remoteFilterSources ->
                uiState.filter.source
            pickedDbSource in remoteFilterSources -> pickedDbSource
            else ->
                uiState.primarySource?.toFilterSource()?.takeIf { it in remoteFilterSources }
                    ?: remoteFilterSources.firstOrNull()
        }

    val activeTab =
        when {
            uiState.filter.favorites -> SearchTab.Favorites
            uiState.filter.source == FoodFilter.Source.Recent -> SearchTab.Recent
            uiState.filter.source == FoodFilter.Source.YourFood -> SearchTab.YourFood
            else -> SearchTab.Database
        }

    val hasText = appState.searchTextFieldState.text.isNotBlank()

    // Live search as the user types: local tabs (Recent/Yours/Favorites) filter instantly, network
    // sources debounce inside the view model. It stays on the current tab until the user submits.
    LaunchedEffect(Unit) {
        snapshotFlow { appState.searchTextFieldState.text.toString() }
            .collect { text -> onSearch(text.ifBlank { null }) }
    }

    // Pressing search / scanning a barcode jumps to the primary database tab.
    val onSubmit: (String?) -> Unit = { query ->
        appState.focused = false
        if (!query.isNullOrBlank()) {
            onFavoritesChange(false)
            activeDbSource?.let { onSourceChange(it) }
        }
    }

    FullScreenCameraBarcodeScanner(
        visible = appState.showBarcodeScanner,
        onBarcodeScan = { code ->
            appState.showBarcodeScanner = false
            appState.searchTextFieldState.setTextAndPlaceCursorAtEnd(code)
            onSubmit(code)
        },
        onClose = { appState.showBarcodeScanner = false },
    )

    val pages = uiState.currentSourceState?.collectAsLazyPagingItems()
    val shimmer = rememberShimmer(ShimmerBounds.View)

    val showSuggestions = appState.focused && !hasText
    val showRecentMeals =
        activeTab == SearchTab.Recent && !hasText && targetMealId != null && targetDate != null

    Scaffold(modifier) { paddingValues ->
        // Fix for searchbar issues on Android SDK 27 and below
        Box(Modifier.focusable().size(1.dp))

        var topContentHeight by remember { mutableIntStateOf(0) }

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .zIndex(10f)
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(top = paddingValues.calculateTopPadding())
                    .onSizeChanged { topContentHeight = it.height }
                    .padding(top = 8.dp),
        ) {
            FoodSearchBar(
                textFieldState = appState.searchTextFieldState,
                focused = appState.focused,
                onFocusChange = { appState.focused = it },
                onSearch = onSubmit,
                onBarcodeScanner = { appState.showBarcodeScanner = true },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )

            if (showSuggestions) {
                Spacer(Modifier.height(6.dp))
                RecentSearchSuggestions(
                    searches = uiState.recentSearches,
                    onFill = { appState.searchTextFieldState.setTextAndPlaceCursorAtEnd(it) },
                    onSearch = { s ->
                        appState.searchTextFieldState.setTextAndPlaceCursorAtEnd(s)
                        onSubmit(s)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            SearchTabs(
                activeTab = activeTab,
                activeDbSource = activeDbSource,
                enabledRemoteSources = uiState.enabledRemoteSources,
                onRecent = {
                    onFavoritesChange(false)
                    onSourceChange(FoodFilter.Source.Recent)
                },
                onYourFood = {
                    onFavoritesChange(false)
                    onSourceChange(FoodFilter.Source.YourFood)
                },
                onFavorites = { onFavoritesChange(true) },
                onDatabase = { source ->
                    pickedDbSource = source
                    onFavoritesChange(false)
                    onSourceChange(source)
                },
            )

            val error = pages?.loadState?.error as? RemoteFoodException
            if (error != null) {
                FoodSearchErrorCard(
                    error = error,
                    onRetry = pages::retry,
                    onAlternativeDb = onAlternativeDb,
                    onUsdaApiKey = onUpdateUsdaApiKey,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).padding(horizontal = 16.dp),
                )
            }
        }

        val contentPadding =
            paddingValues.add(
                top = LocalDensity.current.run { topContentHeight.toDp() },
                bottom = 56.dp + 32.dp,
            )

        Box(Modifier.fillMaxSize()) {
            if (pages?.delayedLoadingState() == true) {
                ContainedLoadingIndicator(
                    modifier =
                        Modifier.align(Alignment.TopCenter)
                            .zIndex(20f)
                            .padding(top = contentPadding.calculateTopPadding())
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                state = appState.listStates.state(uiState.filter.source),
            ) {
                if (showRecentMeals) {
                    item(key = "recent-meals") {
                        RecentMealsSection(targetMealId = targetMealId!!, targetDate = targetDate!!)
                    }
                }

                if (activeTab == SearchTab.Recent && !hasText && (pages?.itemCount ?: 0) > 0) {
                    item(key = "recent-foods-header") {
                        SearchSectionHeader(stringResource(Res.string.headline_recent_foods))
                    }
                }

                if (pages != null) {
                    items(
                        count = pages.itemCount,
                        // Index is part of the key on purpose: distinct remote results can map onto
                        // the same cached product id, and a repeated key crashes LazyColumn.
                        key = { index ->
                            "${uiState.filter.source}:$index:${pages.peek(index)?.id}"
                        },
                    ) { i ->
                        when (val food = pages[i]) {
                            null -> FoodListItemSkeleton(shimmer)
                            is FoodSearch.Product -> {
                                val measurement = food.suggestedMeasurement
                                FoodSearchListItem(
                                    food = food,
                                    measurement = measurement,
                                    onClick = { onFoodClick(food, measurement) },
                                    onToggleFavorite = { id, newState ->
                                        onToggleFavorite(id, newState)
                                    },
                                )
                            }

                            is FoodSearch.Recipe -> {
                                val measurement = food.suggestedMeasurement
                                FoodSearchListItem(
                                    food = food,
                                    measurement = measurement,
                                    onClick = { onFoodClick(food, measurement) },
                                    shimmer = shimmer,
                                    onToggleFavorite = { id, newState ->
                                        onToggleFavorite(id, newState)
                                    },
                                )
                            }
                        }
                    }

                    if (pages.loadState.append is LoadState.Loading) {
                        items(10) { FoodListItemSkeleton(shimmer) }
                    }
                }

                if (pages == null) {
                    items(10) { FoodListItemSkeleton(shimmer) }
                }
            }

            val isEmpty =
                pages != null &&
                    pages.itemCount == 0 &&
                    pages.loadState.append !is LoadState.Loading &&
                    pages.loadState.refresh !is LoadState.Loading
            if (isEmpty && !showRecentMeals) {
                val message =
                    when {
                        hasText -> stringResource(Res.string.neutral_no_food_found)
                        activeTab == SearchTab.YourFood ->
                            stringResource(Res.string.neutral_no_created_foods)
                        activeTab == SearchTab.Favorites ->
                            stringResource(Res.string.neutral_no_favorites)
                        activeTab == SearchTab.Database ->
                            stringResource(Res.string.neutral_nothing_added_here)
                        else -> stringResource(Res.string.neutral_no_food_found)
                    }
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.align(Alignment.Center)
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 48.dp),
                )
            }
        }
    }
}

private fun ListStates.state(source: FoodFilter.Source) =
    when (source) {
        FoodFilter.Source.Recent -> recent
        FoodFilter.Source.YourFood -> yourFood
        FoodFilter.Source.OpenFoodFacts -> openFoodFacts
        FoodFilter.Source.USDA -> usda
        FoodFilter.Source.SwissFoodCompositionDatabase -> swiss
        FoodFilter.Source.Custom -> custom
    }
