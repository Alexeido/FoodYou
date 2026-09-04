package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksimowiczm.foodyou.app.ui.common.component.FoodErrorListItem
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.app.ui.food.component.splitFoodName
import com.maksimowiczm.foodyou.app.ui.food.search.FoodCategory
import com.maksimowiczm.foodyou.app.ui.food.search.FoodCategoryIcon
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MealFoodListItem(
    entry: MealEntryModel,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    onToggleEaten: (MealEntryModel) -> Unit = {},
) {
    when (entry) {
        is FoodMealEntryModel ->
            MealFoodListItem(
                entry = entry,
                color = color,
                contentColor = contentColor,
                shape = shape,
                modifier = modifier,
                onToggleEaten = onToggleEaten,
            )

        is ManualMealEntryModel ->
            MealFoodListItem(
                entry = entry,
                color = color,
                contentColor = contentColor,
                shape = shape,
                modifier = modifier,
                onToggleEaten = onToggleEaten,
            )
    }
}

@Composable
internal fun MealFoodListItem(
    entry: FoodMealEntryModel,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    onToggleEaten: (MealEntryModel) -> Unit = {},
) {
    val measurementString =
        entry.measurement.stringResourceWithWeight(
            totalWeight = entry.totalWeight,
            servingWeight = entry.servingWeight,
            isLiquid = entry.isLiquid,
        )

    val proteins = entry.proteins
    val carbohydrates = entry.carbohydrates
    val fats = entry.fats
    val energy = entry.energy

    if (measurementString == null) {
        FoodErrorListItem(
            headline = entry.name,
            errorMessage = stringResource(Res.string.error_measurement_error),
            modifier = modifier,
        )
        return
    }

    if (proteins == null || carbohydrates == null || fats == null || energy == null) {
        FoodErrorListItem(
            headline = entry.name,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
            modifier = modifier,
        )
        return
    }

    CompactDiaryRow(
        name = entry.name,
        category = entry.category,
        energy = energy,
        proteins = proteins,
        fats = fats,
        carbohydrates = carbohydrates,
        measurementText = measurementString,
        isEaten = entry.isEaten,
        isQuickAdded = false,
        onToggleEaten = { onToggleEaten(entry) },
        color = color,
        contentColor = contentColor,
        shape = shape,
        modifier = modifier,
    )
}

@Composable
internal fun MealFoodListItem(
    entry: ManualMealEntryModel,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    onToggleEaten: (MealEntryModel) -> Unit = {},
) {
    val proteins = entry.proteins
    val carbohydrates = entry.carbohydrates
    val fats = entry.fats
    val energy = entry.energy

    if (proteins == null || carbohydrates == null || fats == null || energy == null) {
        FoodErrorListItem(
            headline = entry.name,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
            modifier = modifier,
        )
        return
    }

    CompactDiaryRow(
        name = entry.name,
        category = entry.category,
        energy = energy,
        proteins = proteins,
        fats = fats,
        carbohydrates = carbohydrates,
        measurementText = null,
        isEaten = entry.isEaten,
        isQuickAdded = true,
        isFromAssistant = entry.createdByAssistant,
        isComposed = entry.isComposed,
        ingredientCount = entry.ingredients.size,
        onToggleEaten = { onToggleEaten(entry) },
        color = color,
        contentColor = contentColor,
        shape = shape,
        modifier = modifier,
    )
}

/**
 * Dense diary row: category icon in a contrast box, name and brand truncated to one line each, and
 * every figure on a single line — `150 kcal · 8P · 8G · 12C · 239.9 g`.
 *
 * Deliberately not built on the shared
 * [com.maksimowiczm.foodyou.app.ui.common.component.FoodListItem]: that one spreads the same data
 * over three lines and is still used by the recipe and goals screens.
 */
@Composable
private fun CompactDiaryRow(
    name: String,
    category: FoodCategory,
    energy: Int,
    proteins: Double,
    fats: Double,
    carbohydrates: Double,
    measurementText: String?,
    isEaten: Boolean,
    isQuickAdded: Boolean,
    isFromAssistant: Boolean = false,
    isComposed: Boolean = false,
    ingredientCount: Int = 0,
    onToggleEaten: () -> Unit,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNutrientsPalette.current
    val (displayName, brand) = remember(name) { splitFoodName(name) }
    val dot = "·"

    Surface(modifier = modifier, color = color, contentColor = contentColor, shape = shape) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(
                        LocalTextStyle provides LocalTextStyle.current.copy(fontSize = 19.sp)
                    ) {
                        FoodCategoryIcon(category = category)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // El plato compuesto lleva su propio icono ademas del de autoria: dice que
                    // detras de esta fila hay varios ingredientes, y que tocarla los enseña.
                    if (isComposed) {
                        Icon(
                            imageVector = Icons.Outlined.LunchDining,
                            contentDescription =
                                stringResource(Res.string.description_composed_food),
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp).padding(start = 2.dp),
                        )
                    }
                    // Distingue quien lo puso: el rayo es el anadido rapido de la persona, el
                    // robot marca lo que anadio el asistente y conviene revisar.
                    if (isFromAssistant) {
                        Icon(
                            imageVector = Icons.Outlined.SmartToy,
                            contentDescription =
                                stringResource(Res.string.description_added_by_assistant),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(start = 2.dp),
                        )
                    } else if (isQuickAdded) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(start = 2.dp),
                        )
                    }
                }

                if (brand != null) {
                    Text(
                        text = brand,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // One figures line, middot separated, with only the energy emphasised.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(
                        LocalTextStyle provides
                            MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = LocalEnergyFormatter.current.formatEnergy(energy),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(dot)
                        Text(
                            text = proteins.formatClipZeros("%.1f") + "P",
                            color = palette.proteinsOnSurfaceContainer,
                        )
                        Text(dot)
                        Text(
                            text = fats.formatClipZeros("%.1f") + "G",
                            color = palette.fatsOnSurfaceContainer,
                        )
                        Text(dot)
                        Text(
                            text = carbohydrates.formatClipZeros("%.1f") + "C",
                            color = palette.carbohydratesOnSurfaceContainer,
                        )
                        if (measurementText != null) {
                            Text(dot)
                            Text(
                                text = measurementText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (isComposed) {
                            // Una entrada manual no tiene medida que enseñar, asi que el hueco lo
                            // ocupa lo unico que aqui aporta algo: cuantas cosas lleva dentro.
                            Text(dot)
                            Text(
                                text =
                                    pluralStringResource(
                                        Res.plurals.description_ingredient_count,
                                        ingredientCount,
                                        ingredientCount,
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            EatenCheckbox(checked = isEaten, onToggle = onToggleEaten)
        }
    }
}

/**
 * 22dp rounded checkbox from the approved design. Material3's [androidx.compose.material3.Checkbox]
 * is nearly square and carries a 48dp touch target that pushes the row's content aside.
 */
@Composable
private fun EatenCheckbox(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (checked) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    }
                )
                .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
