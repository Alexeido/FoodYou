package com.maksimowiczm.foodyou.app.ui.food.diary.update

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.food.search.FoodCategory
import com.maksimowiczm.foodyou.app.ui.food.search.getFoodCategoryFromTags
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPicker
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerState
import com.maksimowiczm.foodyou.app.ui.food.diary.component.FoodMeasurementFormState
import com.maksimowiczm.foodyou.app.ui.food.diary.component.Source
import com.maksimowiczm.foodyou.app.ui.food.diary.component.rememberFoodMeasurementFormState
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun UpdateEntryScreen(
    entryId: Long,
    onBack: () -> Unit,
    onSave: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val viewModel: UpdateFoodDiaryEntryViewModel = koinViewModel {
        parametersOf(FoodDiaryEntryId(entryId))
    }

    LaunchedCollectWithLifecycle(viewModel.uiEvents) {
        when (it) {
            is UpdateEntryEvent.Saved -> onSave()
        }
    }

    val entry = viewModel.entry.collectAsStateWithLifecycle().value
    val possibleTypes = viewModel.possibleMeasurementTypes.collectAsStateWithLifecycle().value
    val suggestions = viewModel.suggestions.collectAsStateWithLifecycle().value

    if (entry == null || suggestions == null || possibleTypes == null) {
        // TODO loading state
    } else {
        val state =
            rememberFoodMeasurementFormState(
                suggestions = suggestions,
                possibleTypes = possibleTypes,
                selectedMeasurement = entry.measurement,
            )

        UpdateEntryScreen(
            onBack = onBack,
            onUnpack = {
                viewModel.unpack(
                    measurement = state.measurementState.measurement,
                    mealId = entry.mealId,
                    date = entry.date,
                )
            },
            onSave = {
                viewModel.save(
                    measurement = state.measurementState.measurement,
                    mealId = entry.mealId,
                    date = entry.date,
                )
            },
            state = state,
            entry = entry,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = modifier,
        )
    }
}

@Composable
private fun UpdateEntryScreen(
    onBack: () -> Unit,
    onUnpack: () -> Unit,
    onSave: () -> Unit,
    state: FoodMeasurementFormState,
    entry: FoodDiaryEntry,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val topBar =
        @Composable {
            MediumTopAppBar(
                title = {
                    val (parsedName, parsedBrand) = remember(entry.food.name) { parseNameAndBrand(entry.food.name) }
                    val category = remember(entry.food) {
                        when (val f = entry.food) {
                            is DiaryFoodProduct -> getFoodCategoryFromTags(f.categories)
                            is DiaryFoodRecipe -> FoodCategory.UNKNOWN
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = category.emoji,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Column {
                            Text(parsedName)
                            if (parsedBrand != null) {
                                Text(
                                    text = parsedBrand,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = FontStyle.Italic,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        }
    val fab =
        @Composable {
            Column(
                modifier =
                    Modifier.animateFloatingActionButton(
                        visible = !animatedVisibilityScope.transition.isRunning && state.isValid,
                        alignment = Alignment.BottomEnd,
                    ),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (entry.food is DiaryFoodRecipe) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (state.isValid) {
                                onUnpack()
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.CallSplit,
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(Res.string.action_unpack)) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                FloatingActionButton(
                    onClick = {
                        if (state.isValid) {
                            onSave()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(Res.string.action_save),
                    )
                }
            }
        }

    Scaffold(modifier = modifier, topBar = topBar, floatingActionButton = fab) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .imePadding()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                paddingValues.add(vertical = 8.dp).let {
                    if (entry.food is DiaryFoodRecipe) {
                        it.add(bottom = 8.dp + 56.dp + 8.dp + 56.dp + 24.dp) // Double FAB
                    } else {
                        it.add(bottom = 56.dp + 24.dp) // Single FAB
                    }
                },
        ) {
            item { HorizontalDivider(Modifier.padding(horizontal = 8.dp)) }

            item {
                MacroSummaryRow(
                    food = entry.food,
                    measurementState = state.measurementState,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item {
                HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                MeasurementPicker(
                    state = state.measurementState,
                    servingWeight = entry.food.servingWeight,
                    totalWeight = entry.food.totalWeight,
                    isLiquid = entry.food.isLiquid,
                    modifier = Modifier.padding(8.dp),
                )
            }

            val food = entry.food
            if (food is DiaryFoodRecipe) {
                item {
                    val measurement = state.measurementState.measurement
                    val ingredients = food.unpack(measurement)

                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    Ingredients(
                        ingredients = ingredients,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            item {
                HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                NutrientList(
                    food = food,
                    measurement = state.measurementState.measurement,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            val note = food.note
            if (note != null) {
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.headline_note),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(text = note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (food is DiaryFoodProduct) {
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            text = stringResource(Res.string.headline_source),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Source(food.source)
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroSummaryRow(
    food: DiaryFood,
    measurementState: MeasurementPickerState,
    modifier: Modifier = Modifier,
) {
    val facts = remember(food, measurementState.measurement) {
        food.nutritionFacts * (food.weight(measurementState.measurement) / 100.0)
    }
    val palette = LocalNutrientsPalette.current

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MacroCell(
            label = "kcal",
            unit = "",
            baseValue = food.nutritionFacts.energy.value,
            currentValue = facts.energy.value,
            onSetWeight = measurementState::setWeightGrams,
            borderColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        MacroCell(
            label = "Prot.",
            unit = "g",
            baseValue = food.nutritionFacts.proteins.value,
            currentValue = facts.proteins.value,
            onSetWeight = measurementState::setWeightGrams,
            borderColor = palette.proteinsOnSurfaceContainer,
            modifier = Modifier.weight(1f),
        )
        MacroCell(
            label = "Carbs",
            unit = "g",
            baseValue = food.nutritionFacts.carbohydrates.value,
            currentValue = facts.carbohydrates.value,
            onSetWeight = measurementState::setWeightGrams,
            borderColor = palette.carbohydratesOnSurfaceContainer,
            modifier = Modifier.weight(1f),
        )
        MacroCell(
            label = "Grasa",
            unit = "g",
            baseValue = food.nutritionFacts.fats.value,
            currentValue = facts.fats.value,
            onSetWeight = measurementState::setWeightGrams,
            borderColor = palette.fatsOnSurfaceContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MacroCell(
    label: String,
    unit: String,
    baseValue: Double?,
    currentValue: Double?,
    onSetWeight: (Float) -> Unit,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    val textFieldState = rememberTextFieldState(currentValue?.formatClipZeros() ?: "")
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(currentValue) {
        if (!isFocused) {
            textFieldState.setTextAndPlaceCursorAtEnd(currentValue?.formatClipZeros() ?: "")
        }
    }

    fun applyValue() {
        val typed = textFieldState.text.toString().toDoubleOrNull()
        if (typed != null && baseValue != null && baseValue > 0) {
            onSetWeight((typed * 100.0 / baseValue).toFloat())
        }
    }

    Surface(
        modifier = modifier.border(1.dp, borderColor, MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (baseValue != null) {
                BasicTextField(
                    state = textFieldState,
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        isFocused = it.isFocused
                        if (!it.isFocused) applyValue()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { applyValue() },
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        textAlign = TextAlign.Center,
                        color = LocalContentColor.current,
                    ),
                    cursorBrush = SolidColor(LocalContentColor.current),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    decorator = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) { innerTextField() }
                    },
                )
            } else {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = if (unit.isNotEmpty()) "$label ($unit)" else label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun parseNameAndBrand(combined: String): Pair<String, String?> {
    val match = Regex("""\s*\((.+)\)$""").find(combined) ?: return Pair(combined, null)
    val rawName = combined.substring(0, match.range.first)
    val brand = match.groupValues[1].split(",").first().trim()
    return Pair(rawName, brand.ifEmpty { null })
}
