package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalNutrientsOrder
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.common.compose.extension.toDp
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.valentinilk.shimmer.Shimmer
import com.valentinilk.shimmer.shimmer
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun GoalsCard(
    homeState: HomeState,
    onClick: (epochDay: Long) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = koinViewModel(),
) {
    LaunchedEffect(homeState.selectedDate) { viewModel.setDate(homeState.selectedDate) }

    val model = viewModel.model.collectAsStateWithLifecycle().value
    val expand by viewModel.expandGoalsCard.collectAsStateWithLifecycle()

    if (model == null) {
        GoalsCardSkeleton(
            shimmer = homeState.shimmer,
            expand = expand,
            onClick = { onClick(homeState.selectedDate.toEpochDays()) },
            onLongClick = onLongClick,
            modifier = modifier,
        )
    } else {
        GoalsCard(
            expand = expand,
            energy = model.energy,
            energyGoal = model.energyGoal,
            proteins = model.proteins,
            proteinsGoal = model.proteinsGoal,
            carbohydrates = model.carbohydrates,
            carbohydratesGoal = model.carbohydratesGoal,
            fats = model.fats,
            fatsGoal = model.fatsGoal,
            onClick = { onClick(homeState.selectedDate.toEpochDays()) },
            onLongClick = onLongClick,
            modifier = modifier,
        )
    }
}

@Composable
internal fun GoalsCard(
    expand: Boolean,
    energy: Int,
    energyGoal: Int,
    proteins: Int,
    proteinsGoal: Int,
    carbohydrates: Int,
    carbohydratesGoal: Int,
    fats: Int,
    fatsGoal: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val proteinsPercentage =
        animateFloatAsState(
                targetValue = proteins.toFloat() / proteinsGoal,
                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
            )
            .value

    val carbsPercentage =
        animateFloatAsState(
                targetValue = carbohydrates.toFloat() / carbohydratesGoal,
                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
            )
            .value

    val fatsPercentage =
        animateFloatAsState(
                targetValue = fats.toFloat() / fatsGoal,
                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
            )
            .value

    FoodYouHomeCard(modifier = modifier, onClick = onClick, onLongClick = onLongClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            GoalsCardContent(
                energy = energy,
                energyGoal = energyGoal,
                proteinsPercentage = proteinsPercentage,
                proteinsGrams = proteins,
                carbsPercentage = carbsPercentage,
                carbohydratesGrams = carbohydrates,
                fatsPercentage = fatsPercentage,
                fatsGrams = fats,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(
                visible = expand,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))

                    ExpandedCardContent(
                        proteinsGrams = proteins,
                        proteinsGoalGrams = proteinsGoal,
                        carbohydratesGrams = carbohydrates,
                        carbohydratesGoalGrams = carbohydratesGoal,
                        fatsGrams = fats,
                        fatsGoalGrams = fatsGoal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalsCardContent(
    energy: Int,
    energyGoal: Int,
    proteinsPercentage: Float,
    proteinsGrams: Int,
    carbsPercentage: Float,
    carbohydratesGrams: Int,
    fatsPercentage: Float,
    fatsGrams: Int,
    modifier: Modifier = Modifier,
) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val nutrientsOrder = LocalNutrientsOrder.current
    val energyFormatter = LocalEnergyFormatter.current

    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme
    val outlineColor = MaterialTheme.colorScheme.outline

    val caloriesString = buildAnnotatedString {
        withStyle(
            typography.headlineLargeEmphasized
                .merge(
                    color =
                        when {
                            energy < energyGoal -> colorScheme.onSurface
                            energy == energyGoal -> colorScheme.onSurface
                            else -> colorScheme.error
                        }
                )
                .toSpanStyle()
        ) {
            append(energyFormatter.formatEnergy(energy, withSuffix = false))
            append(" ")
        }
        withStyle(typography.bodyMedium.merge(outlineColor).toSpanStyle()) {
            val energyGoal = energyFormatter.formatEnergy(energyGoal)
            append("/ $energyGoal")
        }
    }

    val left = remember(energy, energyGoal) { energyGoal - energy }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = caloriesString, style = typography.headlineLargeEmphasized)

            when {
                left > 0 ->
                    Text(
                        text = energyFormatter.energyLeft(left),
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )

                left == 0 ->
                    Text(
                        text = stringResource(Res.string.positive_goal_reached),
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )

                else ->
                    Text(
                        text = energyFormatter.energyExceeded(-left),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nutrientsOrder.forEach { field ->
                when (field) {
                    NutrientsOrder.Proteins ->
                        MacroBarWithLabel(
                            shortLabel = stringResource(Res.string.nutriment_proteins_short),
                            grams = proteinsGrams,
                            progress = proteinsPercentage,
                            containerColor =
                                nutrientsPalette.proteinsOnSurfaceContainer.copy(alpha = .25f),
                            barColor = nutrientsPalette.proteinsOnSurfaceContainer,
                        )

                    NutrientsOrder.Fats ->
                        MacroBarWithLabel(
                            shortLabel = stringResource(Res.string.nutriment_fats_short),
                            grams = fatsGrams,
                            progress = fatsPercentage,
                            containerColor =
                                nutrientsPalette.fatsOnSurfaceContainer.copy(alpha = .25f),
                            barColor = nutrientsPalette.fatsOnSurfaceContainer,
                        )

                    NutrientsOrder.Carbohydrates ->
                        MacroBarWithLabel(
                            shortLabel = stringResource(Res.string.nutriment_carbohydrates_short),
                            grams = carbohydratesGrams,
                            progress = carbsPercentage,
                            containerColor =
                                nutrientsPalette.carbohydratesOnSurfaceContainer.copy(alpha = .25f),
                            barColor = nutrientsPalette.carbohydratesOnSurfaceContainer,
                        )

                    NutrientsOrder.Other,
                    NutrientsOrder.Vitamins,
                    NutrientsOrder.Minerals -> Unit
                }
            }
        }
    }
}

@Composable
private fun MacroBar(
    progress: Float,
    containerColor: Color,
    barColor: Color,
    modifier: Modifier = Modifier,
    overflowColor: Color = MaterialTheme.colorScheme.error,
) {
    val containerFraction = (1 - progress).coerceIn(0f, 1f)
    val overflowFraction = (progress - 1).coerceIn(0f, 1f)

    Canvas(
        modifier =
            modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 8.dp,
                        topEnd = 8.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 4.dp,
                    )
                )
                .fillMaxHeight()
                .width(24.dp)
    ) {
        if (overflowFraction > 0f) {
            val barHeight = 1 - overflowFraction

            drawRect(
                color = barColor,
                size = Size(width = size.width, height = size.height * barHeight - 1.dp.toPx()),
            )
            drawRect(
                color = overflowColor,
                topLeft = Offset(x = 0f, y = size.height * barHeight + 1.dp.toPx()),
                size =
                    Size(width = size.width, height = size.height * overflowFraction - 1.dp.toPx()),
            )
        } else {
            drawRect(
                color = containerColor,
                size =
                    Size(width = size.width, height = size.height * containerFraction - 1.dp.toPx()),
            )
            drawRect(
                color = barColor,
                topLeft = Offset(x = 0f, y = size.height * containerFraction + 1.dp.toPx()),
                size = Size(width = size.width, height = size.height * progress - 1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun MacroBarWithLabel(
    shortLabel: String,
    grams: Int,
    progress: Float,
    containerColor: Color,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier.height(64.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            MacroBar(
                progress = progress,
                containerColor = containerColor,
                barColor = barColor,
            )
            Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Text(
            text = "$grams",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ExpandedCardContent(
    proteinsGrams: Int,
    proteinsGoalGrams: Int,
    carbohydratesGrams: Int,
    carbohydratesGoalGrams: Int,
    fatsGrams: Int,
    fatsGoalGrams: Int,
    modifier: Modifier = Modifier,
) {
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme
    val nutrientsPalette = LocalNutrientsPalette.current
    val nutrientsOrder = LocalNutrientsOrder.current
    val gramShort = stringResource(Res.string.unit_gram_short)

    val proteinsString = buildAnnotatedString {
        val color =
            if (proteinsGrams > proteinsGoalGrams) {
                colorScheme.error
            } else {
                nutrientsPalette.proteinsOnSurfaceContainer
            }

        withStyle(typography.headlineSmall.merge(color).toSpanStyle()) {
            append(" $proteinsGrams ")
        }
        withStyle(typography.bodyMedium.merge(colorScheme.outline).toSpanStyle()) {
            append("/ $proteinsGoalGrams $gramShort")
        }
    }

    val carbohydratesString = buildAnnotatedString {
        val color =
            if (carbohydratesGrams > carbohydratesGoalGrams) {
                colorScheme.error
            } else {
                nutrientsPalette.carbohydratesOnSurfaceContainer
            }

        withStyle(typography.headlineSmall.merge(color).toSpanStyle()) {
            append(" $carbohydratesGrams ")
        }
        withStyle(typography.bodyMedium.merge(colorScheme.outline).toSpanStyle()) {
            append("/ $carbohydratesGoalGrams $gramShort")
        }
    }

    val fatsString = buildAnnotatedString {
        val color =
            if (fatsGrams > fatsGoalGrams) {
                colorScheme.error
            } else {
                nutrientsPalette.fatsOnSurfaceContainer
            }

        withStyle(typography.headlineSmall.merge(color).toSpanStyle()) { append(" $fatsGrams ") }
        withStyle(typography.bodyMedium.merge(colorScheme.outline).toSpanStyle()) {
            append("/ $fatsGoalGrams $gramShort")
        }
    }

    Column(modifier = modifier) {
        nutrientsOrder.forEach {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (it) {
                    NutrientsOrder.Proteins -> {
                        RoundedSquare(LocalNutrientsPalette.current.proteinsOnSurfaceContainer)

                        Text(
                            text = stringResource(Res.string.nutriment_proteins),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        Text(text = proteinsString, style = MaterialTheme.typography.headlineSmall)
                    }

                    NutrientsOrder.Carbohydrates -> {
                        RoundedSquare(LocalNutrientsPalette.current.carbohydratesOnSurfaceContainer)

                        Text(
                            text = stringResource(Res.string.nutriment_carbohydrates),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        Text(
                            text = carbohydratesString,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }

                    NutrientsOrder.Fats -> {
                        RoundedSquare(LocalNutrientsPalette.current.fatsOnSurfaceContainer)

                        Text(
                            text = stringResource(Res.string.nutriment_fats),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        Text(text = fatsString, style = MaterialTheme.typography.headlineSmall)
                    }

                    NutrientsOrder.Other,
                    NutrientsOrder.Vitamins,
                    NutrientsOrder.Minerals -> Unit
                }
            }
        }
    }
}

@Composable
private fun RoundedSquare(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp).clip(MaterialTheme.shapes.extraSmall)) {
        drawRect(color = color, size = size)
    }
}

@Composable
internal fun GoalsMiniBar(
    energy: Int,
    energyGoal: Int,
    proteins: Int,
    proteinsGoal: Int,
    carbohydrates: Int,
    carbohydratesGoal: Int,
    fats: Int,
    fatsGoal: Int,
    modifier: Modifier = Modifier,
) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val nutrientsOrder = LocalNutrientsOrder.current
    val energyFormatter = LocalEnergyFormatter.current
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniMacroColumn(
                label = energyFormatter.suffix(),
                value = energyFormatter.formatEnergy(energy, withSuffix = false),
                goal = energyFormatter.formatEnergy(energyGoal, withSuffix = false),
                progress = if (energyGoal > 0) (energy.toFloat() / energyGoal).coerceIn(0f, 1f) else 0f,
                barColor = colorScheme.primary,
                modifier = Modifier.weight(1f),
            )

            nutrientsOrder.forEach { field ->
                when (field) {
                    NutrientsOrder.Proteins ->
                        MiniMacroColumn(
                            label = stringResource(Res.string.nutriment_proteins),
                            value = "$proteins",
                            goal = "$proteinsGoal",
                            progress = if (proteinsGoal > 0) (proteins.toFloat() / proteinsGoal).coerceIn(0f, 1f) else 0f,
                            barColor = nutrientsPalette.proteinsOnSurfaceContainer,
                            suffix = stringResource(Res.string.unit_gram_short),
                            modifier = Modifier.weight(1f),
                        )

                    NutrientsOrder.Carbohydrates ->
                        MiniMacroColumn(
                            label = stringResource(Res.string.nutriment_carbohydrates),
                            value = "$carbohydrates",
                            goal = "$carbohydratesGoal",
                            progress = if (carbohydratesGoal > 0) (carbohydrates.toFloat() / carbohydratesGoal).coerceIn(0f, 1f) else 0f,
                            barColor = nutrientsPalette.carbohydratesOnSurfaceContainer,
                            suffix = stringResource(Res.string.unit_gram_short),
                            modifier = Modifier.weight(1f),
                        )

                    NutrientsOrder.Fats ->
                        MiniMacroColumn(
                            label = stringResource(Res.string.nutriment_fats),
                            value = "$fats",
                            goal = "$fatsGoal",
                            progress = if (fatsGoal > 0) (fats.toFloat() / fatsGoal).coerceIn(0f, 1f) else 0f,
                            barColor = nutrientsPalette.fatsOnSurfaceContainer,
                            suffix = stringResource(Res.string.unit_gram_short),
                            modifier = Modifier.weight(1f),
                        )

                    NutrientsOrder.Other,
                    NutrientsOrder.Vitamins,
                    NutrientsOrder.Minerals -> Unit
                }
            }
        }
    }
}

@Composable
private fun MiniMacroColumn(
    label: String,
    value: String,
    goal: String,
    progress: Float,
    barColor: Color,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
    )
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = typography.labelSmall,
            color = colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildAnnotatedString {
                withStyle(
                    typography.labelMedium
                        .copy(color = barColor)
                        .toSpanStyle(),
                ) {
                    append(value)
                }
                withStyle(
                    typography.labelSmall
                        .copy(color = colorScheme.outline)
                        .toSpanStyle(),
                ) {
                    append(" /$goal$suffix")
                }
            },
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 2.dp,
                        topEnd = 2.dp,
                        bottomStart = 2.dp,
                        bottomEnd = 2.dp,
                    )
                ),
        ) {
            drawRect(color = barColor.copy(alpha = 0.25f), size = size)
            drawRect(color = barColor, size = Size(size.width * animatedProgress, size.height))
        }
    }
}

@Composable
private fun GoalsCardSkeleton(
    shimmer: Shimmer,
    expand: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoodYouHomeCard(modifier = modifier, onClick = onClick, onLongClick = onLongClick) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(
                        Modifier.shimmer(shimmer)
                            .width(60.dp)
                            .height(MaterialTheme.typography.headlineLargeEmphasized.toDp())
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )

                    Spacer(
                        Modifier.shimmer(shimmer)
                            .size(120.dp, MaterialTheme.typography.bodyMediumEmphasized.toDp())
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                }

                Row(
                    modifier = Modifier.height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MacroBar(
                        progress = 1f,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        barColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.shimmer(shimmer),
                    )

                    MacroBar(
                        progress = 1f,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        barColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.shimmer(shimmer),
                    )

                    MacroBar(
                        progress = 1f,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        barColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.shimmer(shimmer),
                    )
                }
            }

            AnimatedVisibility(
                visible = expand,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoundedSquare(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.shimmer(shimmer),
                        )

                        Spacer(
                            Modifier.shimmer(shimmer)
                                .width(100.dp)
                                .height(MaterialTheme.typography.labelLarge.toDp())
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )

                        Spacer(Modifier.weight(1f))

                        Spacer(
                            Modifier.shimmer(shimmer)
                                .size(80.dp, MaterialTheme.typography.headlineSmall.toDp() - 4.dp)
                                .padding(vertical = 2.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoundedSquare(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.shimmer(shimmer),
                        )

                        Spacer(
                            Modifier.shimmer(shimmer)
                                .width(100.dp)
                                .height(MaterialTheme.typography.labelLarge.toDp())
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )

                        Spacer(Modifier.weight(1f))

                        Spacer(
                            Modifier.shimmer(shimmer)
                                .size(80.dp, MaterialTheme.typography.headlineSmall.toDp() - 4.dp)
                                .padding(vertical = 2.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoundedSquare(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.shimmer(shimmer),
                        )

                        Spacer(
                            Modifier.shimmer(shimmer)
                                .width(100.dp)
                                .height(MaterialTheme.typography.labelLarge.toDp())
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )

                        Spacer(Modifier.weight(1f))

                        Spacer(
                            Modifier.shimmer(shimmer)
                                .size(80.dp, MaterialTheme.typography.headlineSmall.toDp() - 4.dp)
                                .padding(vertical = 2.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                }
            }
        }
    }
}
