package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalNutrientsOrder
import androidx.compose.material3.Surface
import com.maksimowiczm.foodyou.app.ui.common.extension.hapticDraggableHandle
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCardDefaults
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import foodyou.app.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope

@Composable
internal fun MealCard(
    meal: MealModel,
    onAddFood: () -> Unit,
    onQuickAdd: () -> Unit,
    onEditEntry: (MealEntryModel) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    onToggleEaten: (MealEntryModel) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val nutrientsOrder = LocalNutrientsOrder.current
    val dateFormatter = LocalDateFormatter.current
    val energyFormatter = LocalEnergyFormatter.current
    val enDash = stringResource(Res.string.en_dash)
    val allDayString = stringResource(Res.string.headline_all_day)

    val timeString =
        remember(dateFormatter, meal, enDash, allDayString) {
            if (meal.isAllDay) {
                allDayString
            } else {
                buildString {
                    append(dateFormatter.formatTime(meal.from))
                    append(" $enDash ")
                    append(dateFormatter.formatTime(meal.to))
                }
            }
        }

    FoodYouHomeCard(modifier = modifier, onClick = onAddFood, onLongClick = onLongClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meal.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MealTotals(meal)
            }

            Spacer(Modifier.height(16.dp))

            FoodContainer(
                foods = meal.foods,
                onEditEntry = onEditEntry,
                onDeleteEntry = onDeleteEntry,
                onToggleEaten = onToggleEaten,
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            )

            AnimatedVisibility(
                visible = meal.foods.isNotEmpty(),
                enter =
                    expandVertically(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ),
                exit =
                    shrinkVertically(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ),
            ) {
                Spacer(Modifier.height(16.dp))
            }

            MealActions(onAddFood = onAddFood, onQuickAdd = onQuickAdd)
        }
    }
}

@Composable
private fun FoodContainer(
    foods: List<MealEntryModel>,
    onEditEntry: (MealEntryModel) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    onToggleEaten: (MealEntryModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        foods.forEachIndexed { i, entry ->
            val key =
                remember(entry) {
                    when (entry) {
                        is FoodMealEntryModel -> entry.id.toString()
                        is ManualMealEntryModel -> entry.id.toString()
                    }
                }

            key(key) {
                val shape = RoundedCornerShape(16.dp)

                FoodContainerItem(
                    entry = entry,
                    onEditEntry = onEditEntry,
                    onDeleteEntry = onDeleteEntry,
                    onToggleEaten = onToggleEaten,
                    shape = shape,
                )
            }
        }
    }
}

@Composable
private fun animateTopCornerRadius(index: Int, defaultRadius: Dp = 12.dp): Dp =
    animateDpAsState(
            targetValue =
                when (index) {
                    0 -> defaultRadius
                    else -> 0.dp
                },
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        )
        .value
        .coerceAtLeast(0.dp)

@Composable
private fun <T> List<T>.animateBottomCornerRadius(index: Int, defaultRadius: Dp = 12.dp): Dp =
    animateDpAsState(
            targetValue =
                when (index) {
                    lastIndex -> defaultRadius
                    else -> 0.dp
                },
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        )
        .value
        .coerceAtLeast(0.dp)

@Composable
private fun FoodContainerItem(
    entry: MealEntryModel,
    onEditEntry: (MealEntryModel) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    onToggleEaten: (MealEntryModel) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState) {
            BottomSheetContent(
                entry = entry,
                onEdit = {
                    coroutineScope.launch {
                        onEditEntry(entry)
                        sheetState.hide()
                        showBottomSheet = false
                    }
                },
                onDelete = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onDeleteEntry(entry)
                        showBottomSheet = false
                    }
                },
            )
        }
    }

    MealFoodListItem(
        entry = entry,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier.clickable { showBottomSheet = true },
        onToggleEaten = onToggleEaten,
    )
}

/**
 * The meal's totals, rendered compactly for the card header: energy on top, macros underneath.
 *
 * Lives in the header on purpose — when a meal holds a single food, a separate totals row at the
 * bottom repeats that food's numbers verbatim. Renders nothing when nothing is ticked as eaten,
 * which is also what the old footer showed as a row of dashes.
 */
@Composable
private fun MealTotals(meal: MealModel, modifier: Modifier = Modifier) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val nutrientsOrder = LocalNutrientsOrder.current
    val energyFormatter = LocalEnergyFormatter.current

    // Values are animated rather than swapped: moving a food between meals should read as the
    // totals moving, not as two numbers blinking.
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val energy by animateIntAsState(meal.energy, animationSpec = tween(400))
    val proteins by animateFloatAsState(meal.proteins.toFloat(), animationSpec = spec)
    val fats by animateFloatAsState(meal.fats.toFloat(), animationSpec = spec)
    val carbohydrates by animateFloatAsState(meal.carbohydrates.toFloat(), animationSpec = spec)

    AnimatedVisibility(
        visible = meal.energy > 0,
        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = energyFormatter.formatEnergy(energy),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CompositionLocalProvider(
                    LocalTextStyle provides
                        MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp)
                ) {
                    val fields = nutrientsOrder.filter { it in MACRO_FIELDS }
                    fields.forEachIndexed { index, field ->
                        val (value, color) =
                            when (field) {
                                NutrientsOrder.Proteins ->
                                    proteins to nutrientsPalette.proteinsOnSurfaceContainer
                                NutrientsOrder.Fats ->
                                    fats to nutrientsPalette.fatsOnSurfaceContainer
                                else ->
                                    carbohydrates to
                                        nutrientsPalette.carbohydratesOnSurfaceContainer
                            }

                        Text(
                            text = value.toDouble().formatClipZeros("%.1f") + field.shortLetter(),
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (index != fields.lastIndex) {
                            Text(text = "·", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

private val MACRO_FIELDS =
    listOf(NutrientsOrder.Proteins, NutrientsOrder.Fats, NutrientsOrder.Carbohydrates)

/** P / G / C — the compact macro letters used across the diary and search rows. */
internal fun NutrientsOrder.shortLetter(): String =
    when (this) {
        NutrientsOrder.Proteins -> "P"
        NutrientsOrder.Fats -> "G"
        NutrientsOrder.Carbohydrates -> "C"
        else -> ""
    }

/** Dashed outline used by the "add food" button, matching the approved mock. */
internal fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.5.dp) =
    this.drawBehind {
        val stroke =
            Stroke(
                width = strokeWidth.toPx(),
                pathEffect =
                    PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                        0f,
                    ),
            )
        drawRoundRect(
            color = color,
            style = stroke,
            cornerRadius = CornerRadius(cornerRadius.toPx()),
        )
    }

/** Circular quick-add plus a dashed "add food" pill — the only content of a meal footer. */
@Composable
private fun MealActions(
    onAddFood: () -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onQuickAdd,
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(
            modifier =
                Modifier.weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .dashedBorder(MaterialTheme.colorScheme.outline, 19.dp)
                    .clickable(onClick = onAddFood),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(Res.string.action_add),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ValueColumn(
    label: String,
    value: String,
    suffix: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides color,
            LocalTextStyle provides MaterialTheme.typography.labelMedium,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)

            Text(
                text =
                    if (value == "0") {
                        stringResource(Res.string.em_dash)
                    } else {
                        value + (suffix?.let { " $suffix" } ?: "")
                    }
            )
        }
    }
}

@Composable
private fun BottomSheetContent(
    entry: MealEntryModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteDialog(
            onDismissRequest = { showDeleteDialog = false },
            onDeleteEntry = {
                onDelete()
                showDeleteDialog = false
            },
        )
    }

    Column(modifier = modifier) {
        MealFoodListItem(
            entry = entry,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RectangleShape,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_edit_entry)) },
            modifier = Modifier.clickable { onEdit() },
            leadingContent = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_delete_entry)) },
            modifier = Modifier.clickable { showDeleteDialog = true },
            leadingContent = {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
            },
            colors =
                ListItemDefaults.colors(
                    headlineColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                    containerColor = Color.Transparent,
                ),
        )
    }
}

@Composable
private fun DeleteDialog(onDismissRequest: () -> Unit, onDeleteEntry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDeleteEntry,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        title = { Text(stringResource(Res.string.action_delete_entry)) },
        text = { Text(stringResource(Res.string.description_delete_product_entry)) },
    )
}

// ──────────────────────────────────────────────────────────
// Flat-list composables used by VerticalMealsCards drag layout
// ──────────────────────────────────────────────────────────

@Composable
internal fun MealCardHeaderSection(
    meal: MealModel,
    onAddFood: () -> Unit,
    onQuickAdd: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = LocalDateFormatter.current
    val enDash = stringResource(Res.string.en_dash)
    val allDayString = stringResource(Res.string.headline_all_day)

    val timeString =
        remember(dateFormatter, meal, enDash, allDayString) {
            if (meal.isAllDay) {
                allDayString
            } else {
                buildString {
                    append(dateFormatter.formatTime(meal.from))
                    append(" $enDash ")
                    append(dateFormatter.formatTime(meal.to))
                }
            }
        }

    // No surface behind the header: in the approved design only the food rows carry a background,
    // which is what stops every meal reading as a heavy boxed section.
    //
    // Empty and filled headers are different layouts, so every difference between them is
    // interpolated (type sizes, padding) or faded (icon, totals, buttons) rather than swapped —
    // dropping the first food into a meal should morph, not blink.
    val isEmpty = meal.foods.isEmpty()
    val sizeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    val nameSize by animateFloatAsState(if (isEmpty) 15f else 19f, animationSpec = sizeSpec)
    val timeSize by animateFloatAsState(if (isEmpty) 11.5f else 12f, animationSpec = sizeSpec)
    val verticalPadding by animateDpAsState(if (isEmpty) 6.dp else 10.dp)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onLongClick = onLongClick, onClick = onAddFood)
                .padding(horizontal = 4.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = isEmpty,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Restaurant,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = meal.name,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = nameSize.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = timeString,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = timeSize.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = isEmpty,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = onQuickAdd,
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Surface(
                    onClick = onAddFood,
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(Res.string.action_add),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        MealTotals(meal)
    }
}

@Composable
internal fun MealCardFooterSection(
    meal: MealModel,
    onAddFood: () -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The compact header already carries the actions for an empty meal, so the footer collapses —
    // animated so the card grows into place instead of popping.
    AnimatedVisibility(
        visible = meal.foods.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        MealActions(
            onAddFood = onAddFood,
            onQuickAdd = onQuickAdd,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
context(scope: ReorderableCollectionItemScope)
internal fun MealCardEntrySection(
    entry: MealEntryModel,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    isDragging: Boolean,
    onEditEntry: (MealEntryModel) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    onToggleEaten: (MealEntryModel) -> Unit,
    onDragStopped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Every entry is its own rounded card with a gap below, instead of merging into one block.
    val shape = RoundedCornerShape(16.dp)

    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
        ) {
            BottomSheetContent(
                entry = entry,
                onEdit = {
                    coroutineScope.launch {
                        onEditEntry(entry)
                        sheetState.hide()
                        showBottomSheet = false
                    }
                },
                onDelete = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onDeleteEntry(entry)
                        showBottomSheet = false
                    }
                },
            )
        }
    }

    MealFoodListItem(
        entry = entry,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable { showBottomSheet = true }
                .hapticDraggableHandle(onDragStopped = onDragStopped),
        onToggleEaten = onToggleEaten,
    )
}
