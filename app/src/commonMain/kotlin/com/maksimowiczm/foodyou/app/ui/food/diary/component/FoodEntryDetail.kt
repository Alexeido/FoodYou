package com.maksimowiczm.foodyou.app.ui.food.diary.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import foodyou.app.generated.resources.*
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResource
import com.maksimowiczm.foodyou.app.ui.food.component.EnergyProgressIndicator
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPicker
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerState
import com.maksimowiczm.foodyou.app.ui.food.shared.component.NutrientList
import com.maksimowiczm.foodyou.app.ui.food.component.splitFoodName
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import org.jetbrains.compose.resources.stringResource

/**
 * Everything the detail screen needs to draw a food, flattened out of the model it came from — the
 * "add" flow and the "edit an entry already in the diary" flow carry different types but present
 * exactly the same screen.
 */
@Immutable
internal data class FoodDetailUi(
    val name: String,
    val emoji: String,
    val nutritionFacts: NutritionFacts,
    val isLiquid: Boolean,
    val note: String?,
    val totalWeight: Double?,
    val servingWeight: Double?,
    val source: FoodSource?,
    val weightOf: (Measurement) -> Double,
)

/**
 * The single food-detail screen. Both diary flows go through here so they can never drift apart
 * again.
 */
@Composable
internal fun FoodEntryDetailScaffold(
    ui: FoodDetailUi,
    measurementState: MeasurementPickerState,
    isValid: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    confirmIcon: ImageVector,
    confirmDescription: String,
    fabVisible: Boolean,
    canUnpack: Boolean,
    onUnpack: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    ingredients: (@Composable (Measurement) -> Unit)? = null,
    extraNutrientContent: (@Composable () -> Unit)? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    // The name lives in the content; the bar picks it up only once that header scrolls away, so you
    // never lose track of which food you are looking at.
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val displayName = remember(ui.name) { splitFoodName(ui.name) }

    val topBar =
        @Composable {
            TopAppBar(
                title = {
                    AnimatedVisibility(visible = showTitle, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            text = displayName.first,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        }

    val fab =
        @Composable {
            Column(
                modifier =
                    Modifier.animateFloatingActionButton(
                        visible =
                            animatedVisibilityScope?.transition?.isRunning != true &&
                                isValid &&
                                fabVisible,
                        alignment = Alignment.BottomEnd,
                    ),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canUnpack) {
                    ExtendedFloatingActionButton(
                        onClick = { if (isValid) onUnpack() },
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
                    onClick = { if (isValid) onConfirm() },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(imageVector = confirmIcon, contentDescription = confirmDescription)
                }
            }
        }

    Scaffold(modifier = modifier, topBar = topBar, floatingActionButton = fab) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier.fillMaxSize()
                    .imePadding()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                paddingValues.add(vertical = 8.dp).let {
                    if (canUnpack) {
                        it.add(bottom = 8.dp + 56.dp + 8.dp + 56.dp + 24.dp) // Double FAB
                    } else {
                        it.add(bottom = 56.dp + 24.dp) // FAB
                    }
                },
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = ui.emoji, fontSize = 23.sp)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName.first,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        displayName.second?.let { brand ->
                            Text(
                                text = brand,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 13.sp,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // States which amount every number below refers to.
            item {
                val measurement = measurementState.measurement
                val weight = ui.weightOf(measurement)
                val unit =
                    if (ui.isLiquid) stringResource(Res.string.unit_milliliter_short)
                    else stringResource(Res.string.unit_gram_short)
                val amount = "${weight.formatClipZeros("%.1f")} $unit"
                // The measurement label is only added when it says something the weight doesn't.
                val qualifier =
                    when (measurement) {
                        is Measurement.Serving,
                        is Measurement.Package -> measurement.stringResource()
                        else -> null
                    }

                Text(
                    text =
                        buildAnnotatedString {
                            append(stringResource(Res.string.description_values_for, ""))
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            ) {
                                append(amount)
                            }
                            if (qualifier != null) append(" ($qualifier)")
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            item {
                MacroSummaryRow(
                    ui = ui,
                    measurementState = measurementState,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item {
                MeasurementPicker(
                    state = measurementState,
                    servingWeight = ui.servingWeight,
                    totalWeight = ui.totalWeight,
                    isLiquid = ui.isLiquid,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            if (ingredients != null) {
                item {
                    Surface(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        ingredients(measurementState.measurement)
                    }
                }
            }

            item {
                val measurement = measurementState.measurement
                val facts =
                    remember(ui, measurement) {
                        ui.nutritionFacts * (ui.weightOf(measurement) / 100)
                    }

                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val proteins = facts.proteins.value
                    val carbohydrates = facts.carbohydrates.value
                    val fats = facts.fats.value

                    if (proteins != null && carbohydrates != null && fats != null) {
                        EnergyProgressIndicator(
                            proteins = proteins.toFloat(),
                            carbohydrates = carbohydrates.toFloat(),
                            fats = fats.toFloat(),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        NutrientList(facts, flatten = true, modifier = Modifier.padding(4.dp))
                    }

                    extraNutrientContent?.invoke()
                }
            }

            val note = ui.note
            if (note != null) {
                item {
                    SectionCard(
                        title = stringResource(Res.string.headline_note),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(text = note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val source = ui.source
            if (source != null) {
                item {
                    // One line: the label on the left, the source on the right.
                    Surface(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(Res.string.headline_source),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Source(source)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

/** Titled card wrapper, so each block on the detail screen is enclosed rather than rule-separated. */
@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MacroSummaryRow(
    ui: FoodDetailUi,
    measurementState: MeasurementPickerState,
    modifier: Modifier = Modifier,
) {
    val facts =
        remember(ui, measurementState.measurement) {
            ui.nutritionFacts * (ui.weightOf(measurementState.measurement) / 100.0)
        }
    val palette = LocalNutrientsPalette.current

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MacroCell(
            label = "kcal",
            unit = "",
            baseValue = ui.nutritionFacts.energy.value,
            currentValue = facts.energy.value,
            onSetWeight = measurementState::setWeightGrams,
            valueColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        MacroCell(
            label = "Prot.",
            unit = "g",
            baseValue = ui.nutritionFacts.proteins.value,
            currentValue = facts.proteins.value,
            onSetWeight = measurementState::setWeightGrams,
            valueColor = palette.proteinsOnSurfaceContainer,
            modifier = Modifier.weight(1f),
        )
        MacroCell(
            label = "Carbs",
            unit = "g",
            baseValue = ui.nutritionFacts.carbohydrates.value,
            currentValue = facts.carbohydrates.value,
            onSetWeight = measurementState::setWeightGrams,
            valueColor = palette.carbohydratesOnSurfaceContainer,
            modifier = Modifier.weight(1f),
        )
        MacroCell(
            label = "Grasa",
            unit = "g",
            baseValue = ui.nutritionFacts.fats.value,
            currentValue = facts.fats.value,
            onSetWeight = measurementState::setWeightGrams,
            valueColor = palette.fatsOnSurfaceContainer,
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
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val textFieldState = rememberTextFieldState(currentValue?.formatClipZeros("%.1f") ?: "")
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(currentValue) {
        if (!isFocused) {
            textFieldState.setTextAndPlaceCursorAtEnd(currentValue?.formatClipZeros("%.1f") ?: "")
        }
    }

    fun applyValue() {
        val typed = textFieldState.text.toString().toDoubleOrNull()
        if (typed != null && baseValue != null && baseValue > 0) {
            onSetWeight((typed * 100.0 / baseValue).toFloat())
        }
    }

    // Only the number carries the macro colour; four coloured outlines side by side read as noise.
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (baseValue != null) {
                // The unit sits outside the field so the field itself stays purely numeric.
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicTextField(
                        state = textFieldState,
                        modifier =
                            Modifier.width(IntrinsicSize.Min).onFocusChanged {
                                isFocused = it.isFocused
                                if (!it.isFocused) applyValue()
                            },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                        onKeyboardAction = { applyValue() },
                        textStyle =
                            MaterialTheme.typography.titleMedium.copy(
                                textAlign = TextAlign.Center,
                                color = valueColor,
                            ),
                        cursorBrush = SolidColor(valueColor),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        decorator = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) { innerTextField() }
                        },
                    )
                    if (unit.isNotEmpty()) {
                        Text(
                            text = " $unit",
                            style = MaterialTheme.typography.labelMedium,
                            color = valueColor,
                        )
                    }
                }
            } else {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = valueColor,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
