package com.maksimowiczm.foodyou.app.ui.home.master

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.maksimowiczm.foodyou.app.ui.home.meals.card.MealsCards
import com.maksimowiczm.foodyou.app.ui.home.poll.PollsCard
import com.maksimowiczm.foodyou.app.ui.home.shared.rememberHomeState
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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

    val listState = rememberLazyListState()

    // Index of GoalsCard in the LazyColumn (0 = PollsCard, 1..N = order items)
    val goalsCardIndex by remember(order) {
        derivedStateOf {
            if (HomeCard.Goals in order) 1 + order.indexOf(HomeCard.Goals) else -1
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
            Column {
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

            items(order, key = { it.name }) {
                when (it) {
                    HomeCard.Calendar ->
                        CalendarCard(
                            homeState = homeState,
                            modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                        )

                    HomeCard.Goals ->
                        GoalsCard(
                            homeState = homeState,
                            onClick = onGoalsCardClick,
                            onLongClick = onGoalsCardLongClick,
                            modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                        )

                    HomeCard.Meals ->
                        MealsCards(
                            homeState = homeState,
                            onAdd = onMealCardAddClick,
                            onQuickAdd = onMealCardQuickAddClick,
                            onEditEntry = onEditDiaryEntryClick,
                            onLongClick = onMealCardLongClick,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                }
            }
        }
    }
}
