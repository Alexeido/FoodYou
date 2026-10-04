package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.goals.master.stringResource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField
import com.maksimowiczm.foodyou.goals.domain.entity.DailyGoal
import com.maksimowiczm.foodyou.settings.domain.entity.GoalsCardStyle
import com.maksimowiczm.foodyou.settings.domain.entity.GoalsFigureValue
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GoalsCardSettings(
    onBack: () -> Unit,
    onGoalsSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GoalsViewModel = koinViewModel()
    val expand by viewModel.expandGoalsCard.collectAsStateWithLifecycle()
    val style by viewModel.goalsCardStyle.collectAsStateWithLifecycle()
    val figureValue by viewModel.goalsFigureValue.collectAsStateWithLifecycle()
    val tracked by viewModel.trackedNutrients.collectAsStateWithLifecycle()

    GoalsCardSettings(
        onBack = onBack,
        expand = expand,
        style = style,
        figureValue = figureValue,
        onFigureValueChange = viewModel::setGoalsFigureValue,
        onShowDetailsChange = viewModel::setExpandGoalsCard,
        onStyleChange = viewModel::setGoalsCardStyle,
        onGoalsSettings = onGoalsSettings,
        tracked = tracked,
        onToggleTracked = viewModel::toggleTrackedNutrient,
        modifier = modifier,
    )
}

@Composable
private fun GoalsCardSettings(
    onBack: () -> Unit,
    onGoalsSettings: () -> Unit,
    onShowDetailsChange: (Boolean) -> Unit,
    onStyleChange: (GoalsCardStyle) -> Unit,
    onFigureValueChange: (GoalsFigureValue) -> Unit,
    expand: Boolean,
    style: GoalsCardStyle,
    figureValue: GoalsFigureValue,
    tracked: List<NutritionFactsField>,
    onToggleTracked: (NutritionFactsField) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_daily_goals)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues,
        ) {
            stickyHeader {
                GoalsCard(
                    expand = expand,
                    style = style,
                    figureValue = figureValue,
                    energy = 1600,
                    energyGoal = 2000,
                    proteins = 50,
                    proteinsGoal = 75,
                    carbohydrates = 200,
                    carbohydratesGoal = 300,
                    fats = 70,
                    fatsGoal = 90,
                    // Ejemplo: dos tercios de su objetivo por defecto, para ver cómo queda.
                    tracked =
                        tracked.mapNotNull { field ->
                            val goal = DailyGoal.defaultGoals.map[field] ?: return@mapNotNull null
                            TrackedNutrientModel(field, goal * 2 / 3, goal, complete = true)
                        },
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.padding(16.dp),
                )
            }

            item { HorizontalDivider() }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(Res.string.headline_goals_card_style),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.description_goals_card_style),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoalsCardStyle.entries.forEach { entry ->
                            FilterChip(
                                selected = entry == style,
                                onClick = { onStyleChange(entry) },
                                label = { Text(entry.label()) },
                            )
                        }
                    }
                }
            }

            if (style == GoalsCardStyle.Ring || style == GoalsCardStyle.Arc) {
                item { HorizontalDivider() }

                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = stringResource(Res.string.headline_figure_value),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(Res.string.description_figure_value),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GoalsFigureValue.entries.forEach { entry ->
                                FilterChip(
                                    selected = entry == figureValue,
                                    onClick = { onFigureValueChange(entry) },
                                    label = { Text(entry.label()) },
                                )
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.action_show_details)) },
                    modifier = Modifier.clickable { onShowDetailsChange(!expand) },
                    supportingContent = {
                        Text(stringResource(Res.string.description_show_macronutrients_goals))
                    },
                    trailingContent = {
                        Switch(checked = expand, onCheckedChange = onShowDetailsChange)
                    },
                )
            }

            item { HorizontalDivider() }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(Res.string.headline_tracked_nutrients),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.description_tracked_nutrients),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TRACKABLE_GROUPS.forEach { (title, fields) ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fields.forEach { field ->
                                FilterChip(
                                    selected = field in tracked,
                                    onClick = { onToggleTracked(field) },
                                    label = { Text(field.stringResource()) },
                                )
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.headline_daily_goals_settings))
                    },
                    modifier = Modifier.clickable { onGoalsSettings() },
                )
            }
        }
    }
}

/** What can be tracked, grouped as in the goals screen: minerals first, the usual ask. */
private val TRACKABLE_GROUPS =
    listOf(
        Res.string.headline_minerals to
            listOf(
                NutritionFactsField.Calcium,
                NutritionFactsField.Iron,
                NutritionFactsField.Magnesium,
                NutritionFactsField.Potassium,
                NutritionFactsField.Sodium,
                NutritionFactsField.Zinc,
                NutritionFactsField.Phosphorus,
                NutritionFactsField.Iodine,
                NutritionFactsField.Selenium,
                NutritionFactsField.Copper,
                NutritionFactsField.Manganese,
                NutritionFactsField.Chromium,
            ),
        Res.string.headline_vitamins to
            listOf(
                NutritionFactsField.VitaminD,
                NutritionFactsField.VitaminB12,
                NutritionFactsField.VitaminC,
                NutritionFactsField.VitaminB9,
                NutritionFactsField.VitaminA,
                NutritionFactsField.VitaminE,
                NutritionFactsField.VitaminK,
                NutritionFactsField.VitaminB1,
                NutritionFactsField.VitaminB2,
                NutritionFactsField.VitaminB3,
                NutritionFactsField.VitaminB5,
                NutritionFactsField.VitaminB6,
                NutritionFactsField.VitaminB7,
            ),
        Res.string.headline_other to
            listOf(
                NutritionFactsField.DietaryFiber,
                NutritionFactsField.Sugars,
                NutritionFactsField.AddedSugars,
                NutritionFactsField.SaturatedFats,
                NutritionFactsField.Salt,
                NutritionFactsField.Cholesterol,
                NutritionFactsField.Caffeine,
                NutritionFactsField.Omega3,
            ),
    )

@Composable
private fun GoalsCardStyle.label(): String =
    when (this) {
        GoalsCardStyle.Bars -> stringResource(Res.string.goals_style_bars)
        GoalsCardStyle.Columns -> stringResource(Res.string.goals_style_columns)
        GoalsCardStyle.Ring -> stringResource(Res.string.goals_style_ring)
        GoalsCardStyle.Arc -> stringResource(Res.string.goals_style_arc)
        GoalsCardStyle.Stacked -> stringResource(Res.string.goals_style_stacked)
        GoalsCardStyle.Numbers -> stringResource(Res.string.goals_style_numbers)
        GoalsCardStyle.HorizontalBars -> stringResource(Res.string.goals_style_horizontal_bars)
    }

@Composable
private fun GoalsFigureValue.label(): String =
    when (this) {
        GoalsFigureValue.Percentage -> stringResource(Res.string.figure_value_percentage)
        GoalsFigureValue.Energy -> stringResource(Res.string.figure_value_energy)
    }
