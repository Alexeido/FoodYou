package com.maksimowiczm.foodyou.app.ui.food.diary.add

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.food.search.FoodCategory
import com.maksimowiczm.foodyou.app.ui.food.search.getFoodCategoryFromTags
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import foodyou.app.generated.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AddEntryScreen(
    onBack: () -> Unit,
    onEditFood: (FoodId) -> Unit,
    onEntryAdded: () -> Unit,
    onFoodDeleted: () -> Unit,
    onIngredient: (FoodId, Measurement) -> Unit,
    foodId: FoodId,
    mealId: Long,
    date: LocalDate,
    measurement: Measurement?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val viewModel: AddEntryViewModel = koinViewModel { parametersOf(foodId) }

    LaunchedCollectWithLifecycle(viewModel.uiEvents) {
        when (it) {
            is AddEntryEvent.FoodDeleted -> onFoodDeleted()
            is AddEntryEvent.EntryAdded -> onEntryAdded()
        }
    }

    val food = viewModel.food.collectAsStateWithLifecycle().value
    val events by viewModel.foodHistory.collectAsStateWithLifecycle()
    val suggestions = viewModel.suggestions.collectAsStateWithLifecycle().value
    val possibleTypes = viewModel.possibleMeasurementTypes.collectAsStateWithLifecycle().value
    val measurementSuggestion by viewModel.suggestedMeasurement.collectAsStateWithLifecycle()

    // This is stupid that it is here but it's going to be deleted in 4.0.0
    val selectedMeasurement =
        remember(measurement, measurementSuggestion) {
            val realMeasurement = measurement ?: measurementSuggestion
            if (realMeasurement == null) return@remember null
            if (food == null) return@remember realMeasurement

            try {
                food.weight(realMeasurement)
                realMeasurement
            } catch (_: IllegalStateException) {
                if (food.isLiquid) Measurement.Milliliter(100.0) else Measurement.Gram(100.0)
            }
        }

    if (
        food == null ||
            suggestions == null ||
            possibleTypes == null ||
            selectedMeasurement == null
    ) {
        // TODO loading state
    } else {
        val state =
            rememberFoodMeasurementFormState(
                suggestions = suggestions,
                possibleTypes = possibleTypes,
                selectedMeasurement = selectedMeasurement,
            )

        AddEntryScreen(
            onBack = onBack,
            onAdd = {
                viewModel.addEntry(
                    measurement = state.measurementState.measurement,
                    mealId = mealId,
                    date = date,
                )
            },
            onUnpack = {
                viewModel.unpack(
                    measurement = state.measurementState.measurement,
                    mealId = mealId,
                    date = date,
                )
            },
            onEditFood = onEditFood,
            onDelete = viewModel::deleteFood,
            onIngredient = onIngredient,
            food = food,
            history = events,
            state = state,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = modifier,
        )
    }
}

@Composable
private fun AddEntryScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onUnpack: () -> Unit,
    onEditFood: (FoodId) -> Unit,
    onDelete: () -> Unit,
    onIngredient: (FoodId, Measurement) -> Unit,
    food: FoodModel,
    history: List<FoodHistory>,
    state: FoodMeasurementFormState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val topBar =
        @Composable {
            MediumTopAppBar(
                title = {
                    val (parsedName, parsedBrand) = remember(food.name) { parseNameAndBrand(food.name) }
                    val category = remember(food) {
                        when (food) {
                            is ProductModel -> getFoodCategoryFromTags(food.categories)
                            is RecipeModel -> FoodCategory.UNKNOWN
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
                actions = { Menu(onEdit = { onEditFood(food.foodId) }, onDelete = onDelete) },
                scrollBehavior = scrollBehavior,
            )
        }

    val fab =
        @Composable {
            Column(
                modifier =
                    Modifier.animateFloatingActionButton(
                        visible =
                            !animatedVisibilityScope.transition.isRunning &&
                                state.isValid &&
                                (food !is RecipeModel || food.isValid),
                        alignment = Alignment.BottomEnd,
                    ),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (food.canUnpack) {
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
                            onAdd()
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
                    if (food.canUnpack) {
                        it.add(bottom = 8.dp + 56.dp + 8.dp + 56.dp + 24.dp) // Double FAB
                    } else {
                        it.add(bottom = 56.dp + 24.dp) // FAB
                    }
                },
        ) {
            item { HorizontalDivider(Modifier.padding(horizontal = 8.dp)) }

            item {
                MacroSummaryRow(
                    food = food,
                    measurementState = state.measurementState,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item {
                HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                MeasurementPicker(
                    state = state.measurementState,
                    servingWeight = food.servingWeight,
                    totalWeight = food.totalWeight,
                    isLiquid = food.isLiquid,
                    modifier = Modifier.padding(8.dp),
                )
            }

            if (food is RecipeModel) {
                item {
                    val measurement = state.measurementState.measurement
                    val ingredients = food.unpack(food.weight(measurement))

                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    Ingredients(
                        ingredients = ingredients,
                        onIngredient = onIngredient,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            item {
                HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                NutrientList(
                    food = food,
                    measurement = state.measurementState.measurement,
                    onEditFood = onEditFood,
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

            if (food is ProductModel) {
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.headline_source),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Source(food.source)
                        val _categories = (food as? ProductModel)?.categories
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = _categories?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "N/D",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    FoodHistory(events = history, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MacroSummaryRow(
    food: FoodModel,
    measurementState: MeasurementPickerState,
    modifier: Modifier = Modifier,
) {
    val facts = remember(food, measurementState.measurement) {
        val weight = food.weight(measurementState.measurement)
        food.nutritionFacts * (weight / 100.0)
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

@Composable
private fun Menu(onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteDialog(onDismissRequest = { showDeleteDialog = false }, onDelete = onDelete)
    }

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.action_show_more),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_edit)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_delete)) },
                onClick = {
                    expanded = false
                    showDeleteDialog = true
                },
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

@Composable
private fun DeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete)) }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        icon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
        title = { Text(stringResource(Res.string.headline_delete_food)) },
        text = { Text(stringResource(Res.string.description_delete_food)) },
    )
}
