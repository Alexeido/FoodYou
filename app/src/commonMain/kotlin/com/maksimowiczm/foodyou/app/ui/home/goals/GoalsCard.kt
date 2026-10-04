package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import com.maksimowiczm.foodyou.app.ui.goals.master.stringResource
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.common.compose.extension.toDp
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.food.NutrientUnit
import com.maksimowiczm.foodyou.common.domain.food.displayUnit
import com.maksimowiczm.foodyou.common.domain.food.isLimit
import com.maksimowiczm.foodyou.settings.domain.entity.GoalsCardStyle
import com.maksimowiczm.foodyou.settings.domain.entity.GoalsFigureValue
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import kotlin.math.roundToInt
import kotlin.math.sign
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
    val style by viewModel.goalsCardStyle.collectAsStateWithLifecycle()
    val figureValue by viewModel.goalsFigureValue.collectAsStateWithLifecycle()

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
            style = style,
            figureValue = figureValue,
            energy = model.energy,
            energyGoal = model.energyGoal,
            proteins = model.proteins,
            proteinsGoal = model.proteinsGoal,
            carbohydrates = model.carbohydrates,
            carbohydratesGoal = model.carbohydratesGoal,
            fats = model.fats,
            fatsGoal = model.fatsGoal,
            tracked = model.tracked,
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
    style: GoalsCardStyle = GoalsCardStyle.Bars,
    figureValue: GoalsFigureValue = GoalsFigureValue.Percentage,
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
    tracked: List<TrackedNutrientModel> = emptyList(),
) {
    // Marcar o desmarcar algo como comido cambia el día de golpe; las cifras cuentan hasta el
    // nuevo valor en vez de saltar, y todo lo que se dibuja con ellas las sigue.
    val energyShown by animateIntAsState(energy, MaterialTheme.motionScheme.slowEffectsSpec())
    val proteinsShown by animateIntAsState(proteins, MaterialTheme.motionScheme.slowEffectsSpec())
    val carbohydratesShown by
        animateIntAsState(carbohydrates, MaterialTheme.motionScheme.slowEffectsSpec())
    val fatsShown by animateIntAsState(fats, MaterialTheme.motionScheme.slowEffectsSpec())

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
                energy = energyShown,
                energyGoal = energyGoal,
                style = style,
                figureValue = figureValue,
                showMacroValues = !expand,
                proteinsGoal = proteinsGoal,
                carbohydratesGoal = carbohydratesGoal,
                fatsGoal = fatsGoal,
                proteinsPercentage = proteinsPercentage,
                proteinsGrams = proteinsShown,
                carbsPercentage = carbsPercentage,
                carbohydratesGrams = carbohydratesShown,
                fatsPercentage = fatsPercentage,
                fatsGrams = fatsShown,
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
                        proteinsGrams = proteinsShown,
                        proteinsGoalGrams = proteinsGoal,
                        carbohydratesGrams = carbohydratesShown,
                        carbohydratesGoalGrams = carbohydratesGoal,
                        fatsGrams = fatsShown,
                        fatsGoalGrams = fatsGoal,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    tracked.forEach { nutrient ->
                        TrackedNutrientRow(nutrient, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsCardContent(
    energy: Int,
    energyGoal: Int,
    style: GoalsCardStyle,
    figureValue: GoalsFigureValue,
    showMacroValues: Boolean,
    proteinsGoal: Int,
    carbohydratesGoal: Int,
    fatsGoal: Int,
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

    val caloriesColor by
        animateColorAsState(
            if (energy > energyGoal) colorScheme.error else colorScheme.onSurface,
            MaterialTheme.motionScheme.slowEffectsSpec(),
        )

    val caloriesString = buildAnnotatedString {
        withStyle(typography.headlineLargeEmphasized.merge(color = caloriesColor).toSpanStyle()) {
            append(energyFormatter.formatEnergy(energy, withSuffix = false))
            append(" ")
        }
        withStyle(typography.bodyMedium.merge(outlineColor).toSpanStyle()) {
            val energyGoal = energyFormatter.formatEnergy(energyGoal)
            append("/ $energyGoal")
        }
    }

    val left = remember(energy, energyGoal) { energyGoal - energy }

    val figureText =
        when (figureValue) {
            GoalsFigureValue.Percentage -> {
                val ratio = energy.toFloat() / energyGoal.coerceAtLeast(1)
                "${(ratio.coerceIn(0f, 1f) * 100).roundToInt()}%"
            }
            GoalsFigureValue.Energy -> energyFormatter.formatEnergy(energy, withSuffix = false)
        }

    val macros =
        nutrientsOrder.mapNotNull { field ->
            when (field) {
                NutrientsOrder.Proteins ->
                    MacroSlice(
                        label = stringResource(Res.string.nutriment_proteins_short),
                        grams = proteinsGrams,
                        goalGrams = proteinsGoal,
                        progress = proteinsPercentage,
                        color = nutrientsPalette.proteinsOnSurfaceContainer,
                    )

                NutrientsOrder.Fats ->
                    MacroSlice(
                        label = stringResource(Res.string.nutriment_fats_short),
                        grams = fatsGrams,
                        goalGrams = fatsGoal,
                        progress = fatsPercentage,
                        color = nutrientsPalette.fatsOnSurfaceContainer,
                    )

                NutrientsOrder.Carbohydrates ->
                    MacroSlice(
                        label = stringResource(Res.string.nutriment_carbohydrates_short),
                        grams = carbohydratesGrams,
                        goalGrams = carbohydratesGoal,
                        progress = carbsPercentage,
                        color = nutrientsPalette.carbohydratesOnSurfaceContainer,
                    )

                NutrientsOrder.Other,
                NutrientsOrder.Vitamins,
                NutrientsOrder.Minerals -> null
            }
        }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The calorie block yields width so a wide macro figure can never push itself off the
            // card — that was the carbohydrates column running past the edge.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = caloriesString, style = typography.headlineLargeEmphasized)

                if (style.showsEnergyLeft) {
                    val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = left.sign,
                        transitionSpec = {
                            (fadeIn(fadeInSpec) + slideInVertically { it / 3 })
                                .togetherWith(fadeOut(fadeOutSpec))
                        },
                        label = "energyLeft",
                    ) { state ->
                    when {
                        state > 0 ->
                            Text(
                                text = energyFormatter.energyLeft(left),
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                            )

                        state == 0 ->
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
                }

                // With the detail section open those grams are printed just below, so the compact
                // figure drops its numbers rather than stating them twice.
                if (style.showsInlineMacros && showMacroValues) {
                    InlineMacros(macros)
                }
            }

            Spacer(Modifier.width(12.dp))

            when (style) {
                GoalsCardStyle.Bars ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        macros.forEach { macro ->
                            MacroBarWithLabel(
                                shortLabel = macro.label,
                                grams = macro.grams,
                                showValue = showMacroValues,
                                progress = macro.progress,
                                containerColor = macro.color.copy(alpha = .3f),
                                barColor = macro.color,
                            )
                        }
                    }

                GoalsCardStyle.Columns -> MacroColumns(macros, showValues = showMacroValues)

                GoalsCardStyle.Ring ->
                    EnergyRing(
                        progress = energy.toFloat() / energyGoal.coerceAtLeast(1),
                        centerText = figureText,
                    )

                GoalsCardStyle.Arc ->
                    EnergyArc(
                        progress = energy.toFloat() / energyGoal.coerceAtLeast(1),
                        centerText = figureText,
                    )

                GoalsCardStyle.Stacked -> MacroLegend(macros, showValues = showMacroValues)

                // Figures-only has nothing left to draw once the numbers move to the detail
                // section, so it steps aside instead of leaving an empty gap.
                GoalsCardStyle.Numbers -> if (showMacroValues) MacroNumbers(macros)

                GoalsCardStyle.HorizontalBars ->
                    MacroHorizontalBars(macros, showValues = showMacroValues)
            }
        }

        if (style == GoalsCardStyle.Stacked) {
            Spacer(Modifier.height(12.dp))
            StackedMacroBar(
                proteins = proteinsGrams,
                carbohydrates = carbohydratesGrams,
                fats = fatsGrams,
                proteinsColor = nutrientsPalette.proteinsOnSurfaceContainer,
                carbohydratesColor = nutrientsPalette.carbohydratesOnSurfaceContainer,
                fatsColor = nutrientsPalette.fatsOnSurfaceContainer,
            )
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
                .clip(RoundedCornerShape(3.dp))
                .fillMaxHeight()
                .width(6.dp)
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
    showValue: Boolean = true,
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
            modifier = Modifier.height(48.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            MacroBar(
                progress = progress,
                containerColor = containerColor,
                barColor = barColor,
            )
        }
        // Label sits under the track, not inside it — a 6dp bar has no room for text.
        Text(
            text = shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        if (showValue) {
            Text(
                text = "$grams",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/**
 * A nutrient the person wants to reach (calcium...), like the macro rows above. Going over is not
 * a problem for most - reaching the goal turns it the primary colour - but it is for a limit
 * (sugar, salt...), which turns red like a macro. When
 * some food doesn't say how much it has, the figure is a minimum and says so with "≥".
 */
@Composable
private fun TrackedNutrientRow(nutrient: TrackedNutrientModel, modifier: Modifier = Modifier) {
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme
    val unit = nutrient.field.displayUnit
    val target = nutrient.grams * unit.perGram
    val animated by
        animateFloatAsState(target.toFloat(), MaterialTheme.motionScheme.slowEffectsSpec())
    val value = animated.toDouble()
    val goal = nutrient.goalGrams * unit.perGram
    val reached = goal > 0 && target >= goal
    val color by
        animateColorAsState(
            when {
                nutrient.field.isLimit && target > goal -> colorScheme.error
                nutrient.field.isLimit -> colorScheme.tertiary
                reached -> colorScheme.primary
                else -> colorScheme.tertiary
            },
            MaterialTheme.motionScheme.slowEffectsSpec(),
        )
    val unitLabel =
        when (unit) {
            NutrientUnit.Gram -> stringResource(Res.string.unit_gram_short)
            NutrientUnit.Milligram -> stringResource(Res.string.unit_milligram_short)
            NutrientUnit.Microgram -> stringResource(Res.string.unit_microgram_short)
        }

    val text = buildAnnotatedString {
        withStyle(typography.headlineSmall.merge(color).toSpanStyle()) {
            append(if (nutrient.complete) " " else " ≥")
            append(value.forCard())
            append(" ")
        }
        withStyle(typography.bodyMedium.merge(colorScheme.outline).toSpanStyle()) {
            append("/ ${goal.forCard()} $unitLabel")
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoundedSquare(color)
        Text(
            text = nutrient.field.stringResource(),
            modifier = Modifier.weight(1f),
            style = typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = text, style = typography.headlineSmall)
    }
}

/** Whole numbers from 10 up; one decimal below, where it still says something (1.4 g). */
private fun Double.forCard(): String =
    if (this >= 10 || this == 0.0) roundToInt().toString() else formatClipZeros("%.1f")

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
    val energyShown by animateIntAsState(energy, MaterialTheme.motionScheme.slowEffectsSpec())
    val proteinsShown by animateIntAsState(proteins, MaterialTheme.motionScheme.slowEffectsSpec())
    val carbohydratesShown by
        animateIntAsState(carbohydrates, MaterialTheme.motionScheme.slowEffectsSpec())
    val fatsShown by animateIntAsState(fats, MaterialTheme.motionScheme.slowEffectsSpec())

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
                value = energyFormatter.formatEnergy(energyShown, withSuffix = false),
                goal = energyFormatter.formatEnergy(energyGoal, withSuffix = false),
                progress = if (energyGoal > 0) (energyShown.toFloat() / energyGoal).coerceIn(0f, 1f) else 0f,
                barColor = colorScheme.primary,
                modifier = Modifier.weight(1f),
            )

            nutrientsOrder.forEach { field ->
                when (field) {
                    NutrientsOrder.Proteins ->
                        MiniMacroColumn(
                            label = stringResource(Res.string.nutriment_proteins),
                            value = "$proteinsShown",
                            goal = "$proteinsGoal",
                            progress = if (proteinsGoal > 0) (proteinsShown.toFloat() / proteinsGoal).coerceIn(0f, 1f) else 0f,
                            barColor = nutrientsPalette.proteinsOnSurfaceContainer,
                            suffix = stringResource(Res.string.unit_gram_short),
                            modifier = Modifier.weight(1f),
                        )

                    NutrientsOrder.Carbohydrates ->
                        MiniMacroColumn(
                            label = stringResource(Res.string.nutriment_carbohydrates),
                            value = "$carbohydratesShown",
                            goal = "$carbohydratesGoal",
                            progress = if (carbohydratesGoal > 0) (carbohydratesShown.toFloat() / carbohydratesGoal).coerceIn(0f, 1f) else 0f,
                            barColor = nutrientsPalette.carbohydratesOnSurfaceContainer,
                            suffix = stringResource(Res.string.unit_gram_short),
                            modifier = Modifier.weight(1f),
                        )

                    NutrientsOrder.Fats ->
                        MiniMacroColumn(
                            label = stringResource(Res.string.nutriment_fats),
                            value = "$fatsShown",
                            goal = "$fatsGoal",
                            progress = if (fatsGoal > 0) (fatsShown.toFloat() / fatsGoal).coerceIn(0f, 1f) else 0f,
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
