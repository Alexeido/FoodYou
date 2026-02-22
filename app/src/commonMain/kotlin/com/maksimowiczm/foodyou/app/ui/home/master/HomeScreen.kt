package com.maksimowiczm.foodyou.app.ui.home.master

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.home.calendar.CalendarCard
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsCard
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsMiniBar
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsViewModel
import com.maksimowiczm.foodyou.app.ui.home.meals.card.FoodMealEntryModel
import com.maksimowiczm.foodyou.app.ui.home.meals.card.HorizontalMealsCards
import com.maksimowiczm.foodyou.app.ui.home.meals.card.ManualMealEntryModel
import com.maksimowiczm.foodyou.app.ui.home.meals.card.MealListItem
import com.maksimowiczm.foodyou.app.ui.home.meals.card.MealsCardsViewModel
import com.maksimowiczm.foodyou.app.ui.home.meals.card.toFlatItems
import com.maksimowiczm.foodyou.app.ui.home.meals.card.toReorderedAssignments
import com.maksimowiczm.foodyou.app.ui.home.meals.card.verticalMealsCardsItems
import com.maksimowiczm.foodyou.app.ui.home.meals.card.withSyncedGhosts
import com.maksimowiczm.foodyou.app.ui.home.poll.PollsCard
import com.maksimowiczm.foodyou.app.ui.home.shared.rememberHomeState
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsCardsLayout
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    onTitle: () -> Unit,
    onMealCardLongClick: (mealId: Long) -> Unit,
    onMealCardAddClick: (epochDay: Long, mealId: Long) -> Unit,
    onMealCardQuickAddClick: (epochDay: Long, mealId: Long) -> Unit,
    onGoalsCardLongClick: () -> Unit,
    onGoalsCardClick: (epochDay: Long) -> Unit,
    onEditDiaryEntryClick: (foodEntryId: Long?, manualEntryId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = koinViewModel()
    val order by viewModel.homeOrder.collectAsStateWithLifecycle()
    val homeState = rememberHomeState()

    val goalsViewModel: GoalsViewModel = koinViewModel()
    val goalsModel by goalsViewModel.model.collectAsStateWithLifecycle()
    LaunchedEffect(homeState.selectedDate) { goalsViewModel.setDate(homeState.selectedDate) }

    val mealsViewModel: MealsCardsViewModel = koinViewModel()
    val diaryMeals by mealsViewModel.diaryMeals.collectAsStateWithLifecycle()
    val layout by mealsViewModel.layout.collectAsStateWithLifecycle()
    LaunchedEffect(homeState.selectedDate, mealsViewModel) {
        mealsViewModel.setDate(homeState.selectedDate)
    }

    val sourceFlatItems = remember(diaryMeals) { diaryMeals?.toFlatItems() ?: emptyList() }
    // flatItemsHolder is a plain Kotlin object — NOT a Compose snapshot participant.
    // Writes inside the reorderState onMove callback are therefore immediately visible
    // when onDragStopped reads it, even if onMove runs inside Snapshot.withMutableSnapshot.
    val flatItemsHolder = remember { object { var items: List<MealListItem> = emptyList() } }
    var localFlatItems by remember(sourceFlatItems) {
        flatItemsHolder.items = sourceFlatItems
        mutableStateOf(sourceFlatItems)
    }

    val listState = rememberLazyListState()

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val fromItem = localFlatItems.find { it.key == fromKey }
        if (fromItem is MealListItem.Entry) {
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            val fromIndex = localFlatItems.indexOf(fromItem)
            val toIndex = localFlatItems.indexOfFirst { it.key == toKey }
            if (toIndex >= 0 && fromIndex != toIndex) {
                val newList = localFlatItems.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }.withSyncedGhosts()
                flatItemsHolder.items = newList
                localFlatItems = newList
            }
        }
    }

    val onMealDragStopped = remember {
        { mealsViewModel.onEntriesReordered(flatItemsHolder.items.toReorderedAssignments()) }
    }

    // Count how many LazyColumn items appear before the GoalsCard to determine if it's scrolled past
    val goalsCardIndex by remember(order) {
        derivedStateOf {
            if (HomeCard.Goals !in order) return@derivedStateOf -1
            var count = 1 // PollsCard is at index 0; cards start at index 1
            for (card in order) {
                if (card == HomeCard.Goals) return@derivedStateOf count
                count += when {
                    card == HomeCard.Meals && layout == MealsCardsLayout.Vertical ->
                        if (diaryMeals == null) 4 else localFlatItems.size
                    else -> 1
                }
            }
            -1
        }
    }

    val isGoalsScrolledPast by remember {
        derivedStateOf {
            goalsCardIndex >= 0 && listState.firstVisibleItemIndex > goalsCardIndex
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.foundation.layout.Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.app_name),
                            modifier =
                                Modifier.clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClick = onTitle,
                                ),
                        )
                    },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(Res.string.action_go_to_settings),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                AnimatedVisibility(
                    visible = isGoalsScrolledPast && goalsModel != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    goalsModel?.let { model ->
                        GoalsMiniBar(
                            energy = model.energy,
                            energyGoal = model.energyGoal,
                            proteins = model.proteins,
                            proteinsGoal = model.proteinsGoal,
                            carbohydrates = model.carbohydrates,
                            carbohydratesGoal = model.carbohydratesGoal,
                            fats = model.fats,
                            fatsGoal = model.fatsGoal,
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues,
        ) {
            item(key = "polls") {
                PollsCard(modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp))
            }

            order.forEach { card ->
                when (card) {
                    HomeCard.Calendar ->
                        item(key = card.name) {
                            CalendarCard(
                                homeState = homeState,
                                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                            )
                        }

                    HomeCard.Goals ->
                        item(key = card.name) {
                            GoalsCard(
                                homeState = homeState,
                                onClick = onGoalsCardClick,
                                onLongClick = onGoalsCardLongClick,
                                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                            )
                        }

                    HomeCard.Meals ->
                        when (layout) {
                            MealsCardsLayout.Horizontal ->
                                item(key = card.name) {
                                    HorizontalMealsCards(
                                        meals = diaryMeals,
                                        onAdd = { mealId ->
                                            onMealCardAddClick(
                                                homeState.selectedDate.toEpochDays(),
                                                mealId,
                                            )
                                        },
                                        onQuickAdd = { mealId ->
                                            onMealCardQuickAddClick(
                                                homeState.selectedDate.toEpochDays(),
                                                mealId,
                                            )
                                        },
                                        onEditEntry = { model ->
                                            onEditDiaryEntryClick(
                                                (model as? FoodMealEntryModel)?.id?.value,
                                                (model as? ManualMealEntryModel)?.id?.value,
                                            )
                                        },
                                        onDeleteEntry = mealsViewModel::onDeleteEntry,
                                        onToggleEaten = mealsViewModel::onToggleEaten,
                                        onLongClick = onMealCardLongClick,
                                        shimmer = homeState.shimmer,
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }

                            MealsCardsLayout.Vertical -> {
                                verticalMealsCardsItems(
                                    meals = diaryMeals,
                                    localFlatItems = localFlatItems,
                                    reorderState = reorderState,
                                    onAdd = { mealId ->
                                        onMealCardAddClick(
                                            homeState.selectedDate.toEpochDays(),
                                            mealId,
                                        )
                                    },
                                    onQuickAdd = { mealId ->
                                        onMealCardQuickAddClick(
                                            homeState.selectedDate.toEpochDays(),
                                            mealId,
                                        )
                                    },
                                    onEditEntry = { model ->
                                        onEditDiaryEntryClick(
                                            (model as? FoodMealEntryModel)?.id?.value,
                                            (model as? ManualMealEntryModel)?.id?.value,
                                        )
                                    },
                                    onDeleteEntry = mealsViewModel::onDeleteEntry,
                                    onToggleEaten = mealsViewModel::onToggleEaten,
                                    onDragStopped = onMealDragStopped,
                                    onLongClick = onMealCardLongClick,
                                    shimmer = homeState.shimmer,
                                    horizontalPadding = 8.dp,
                                )
                                item(key = "meals_bottom_spacer") {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                }
            }
        }
    }
}
