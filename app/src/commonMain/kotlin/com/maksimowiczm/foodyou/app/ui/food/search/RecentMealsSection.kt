package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.meal.MealSectionIcon
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.fooddiary.domain.entity.RecentMeal
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.AddRecentMealUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveRecentMealsUseCase
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * "Recent meals" — re-add a whole past meal at once. Same-block meals come first (see
 * [ObserveRecentMealsUseCase]). Only shown when a target meal + date are available (i.e. searching
 * to add to the diary).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentMealsSection(
    targetMealId: Long,
    targetDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    val observeRecentMeals: ObserveRecentMealsUseCase = koinInject()
    val addRecentMeal: AddRecentMealUseCase = koinInject()
    val scope = rememberCoroutineScope()

    val meals =
        remember(targetMealId, targetDate) {
                observeRecentMeals.observe(currentMealId = targetMealId, currentDate = targetDate)
            }
            .collectAsStateWithLifecycle(emptyList())
            .value

    if (meals.isEmpty()) return

    // Keys of meals the user just added, to flip the button to "Added".
    val added = remember { mutableStateListOf<String>() }
    var showAll by remember { mutableStateOf(false) }

    val onAdd: (RecentMeal) -> Unit = { meal ->
        val key = "${meal.mealId}:${meal.date}"
        if (key !in added) {
            added.add(key)
            scope.launch {
                addRecentMeal.add(
                    sourceMealId = meal.mealId,
                    sourceDate = meal.date,
                    targetMealId = targetMealId,
                    targetDate = targetDate,
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SearchSectionHeader(
            text = stringResource(Res.string.headline_recent_meals),
            trailing = {
                if (meals.size > 2) {
                    TextButton(onClick = { showAll = true }) {
                        Text(stringResource(Res.string.action_see_all))
                    }
                }
            },
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(meals, key = { "${it.mealId}:${it.date}" }) { meal ->
                RecentMealCard(
                    meal = meal,
                    isAdded = "${meal.mealId}:${meal.date}" in added,
                    onAdd = { onAdd(meal) },
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }

    if (showAll) {
        ModalBottomSheet(onDismissRequest = { showAll = false }) {
            Text(
                text = stringResource(Res.string.headline_recent_meals),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(meals, key = { "all-${it.mealId}:${it.date}" }) { meal ->
                    RecentMealCard(
                        meal = meal,
                        isAdded = "${meal.mealId}:${meal.date}" in added,
                        onAdd = { onAdd(meal) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentMealCard(
    meal: RecentMeal,
    isAdded: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = LocalDateFormatter.current
    val energyFormatter = LocalEnergyFormatter.current
    val palette = LocalNutrientsPalette.current

    val proteins = meal.nutritionFacts.proteins.value?.roundToInt() ?: 0
    val carbs = meal.nutritionFacts.carbohydrates.value?.roundToInt() ?: 0
    val fats = meal.nutritionFacts.fats.value?.roundToInt() ?: 0
    val energy = meal.nutritionFacts.energy.value?.roundToInt() ?: 0
    val g = stringResource(Res.string.unit_gram_short)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MealSectionIcon(icon = meal.icon, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = meal.mealName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = dateFormatter.formatDate(meal.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = meal.itemNames.take(3).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(34.dp),
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = energyFormatter.formatEnergy(energy),
                style = MaterialTheme.typography.labelMedium,
            )
            Row {
                Text("$proteins $g", color = palette.proteinsOnSurfaceContainer, style = MaterialTheme.typography.labelSmall)
                Text("  ")
                Text("$carbs $g", color = palette.carbohydratesOnSurfaceContainer, style = MaterialTheme.typography.labelSmall)
                Text("  ")
                Text("$fats $g", color = palette.fatsOnSurfaceContainer, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = onAdd,
                shape = CircleShape,
                color =
                    if (isAdded) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.primaryContainer,
                contentColor =
                    if (isAdded) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth().height(36.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isAdded) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text =
                            if (isAdded) stringResource(Res.string.neutral_added)
                            else stringResource(Res.string.action_add_meal_block),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
