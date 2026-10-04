package com.maksimowiczm.foodyou.app.ui.food.diary.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksimowiczm.foodyou.app.ui.common.component.FoodErrorListItem
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPicker
import com.maksimowiczm.foodyou.app.ui.food.component.splitFoodName
import com.maksimowiczm.foodyou.app.ui.food.diary.component.MacroSummaryRow
import com.maksimowiczm.foodyou.app.ui.food.diary.component.rememberFoodMeasurementFormState
import com.maksimowiczm.foodyou.app.ui.food.search.diaryCategory
import com.maksimowiczm.foodyou.app.ui.home.meals.card.CompactDiaryRow
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.compose.extension.horizontal
import com.maksimowiczm.foodyou.common.compose.extension.vertical
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipeIngredient
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The ingredients of a recipe entry, as they are for the current portion.
 *
 * Each one is drawn with the same row as the diary itself - category icon, brand on its own line,
 * one line of figures - so a dish reads like the meal it belongs to.
 *
 * @param onEdit When set, tapping an ingredient opens its editor to change its amount for this
 *   entry only. Called with the index and the new amount, in whatever unit was picked.
 */
@Composable
internal fun Ingredients(
    ingredients: List<DiaryFoodRecipeIngredient>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onEdit: ((index: Int, measurement: Measurement) -> Unit)? = null,
) {
    // Por indice y no por el objeto: el objeto cambia en cuanto se mueve la racion.
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    editing?.let { index ->
        val ingredient = ingredients.getOrNull(index)
        if (ingredient == null || onEdit == null) {
            editing = null
        } else {
            IngredientEditorSheet(
                ingredient = ingredient,
                onDismiss = { editing = null },
                onConfirm = { measurement ->
                    onEdit(index, measurement)
                    editing = null
                },
            )
        }
    }

    val verticalPadding = contentPadding.vertical()
    val horizontal = contentPadding.horizontal()

    Column(modifier.padding(verticalPadding)) {
        Text(
            text = stringResource(Res.string.headline_ingredients),
            modifier = Modifier.padding(horizontal).padding(bottom = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        ingredients.forEachIndexed { index, ingredient ->
            val facts = ingredient.nutritionFacts
            val proteins = facts.proteins.value
            val carbs = facts.carbohydrates.value
            val fats = facts.fats.value
            val energy = facts.energy.value
            val measurementString =
                ingredient.measurement.stringResourceWithWeight(
                    totalWeight = ingredient.food.totalWeight,
                    servingWeight = ingredient.food.servingWeight,
                    isLiquid = ingredient.food.isLiquid,
                )
            val onClick = onEdit?.let { { editing = index } }

            if (
                proteins == null ||
                    carbs == null ||
                    fats == null ||
                    energy == null ||
                    measurementString == null
            ) {
                // Still tappable: the amount can be fixed even if the food lacks some macros.
                FoodErrorListItem(
                    headline = ingredient.food.name,
                    errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
                    contentPadding = horizontal.add(vertical = 8.dp),
                    onClick = onClick,
                )
            } else {
                val category = remember(ingredient.food) { ingredient.food.diaryCategory() }
                CompactDiaryRow(
                    name = ingredient.food.name,
                    category = category,
                    energy = energy.roundToInt(),
                    proteins = proteins,
                    fats = fats,
                    carbohydrates = carbs,
                    measurementText = measurementString,
                    isEaten = false,
                    isQuickAdded = false,
                    isRecipe = ingredient.food is DiaryFoodRecipe,
                    onToggleEaten = null,
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RectangleShape,
                    onClick = onClick,
                )
            }
        }
    }
}

/**
 * Changes one ingredient's amount with the same tools as the food screen: every unit the food
 * supports (grams, its serving - a slice, if that is how the product defines it - or its package),
 * the quick chips, and the macro cells, so "this much cheese is 10 g of protein" works here too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientEditorSheet(
    ingredient: DiaryFoodRecipeIngredient,
    onDismiss: () -> Unit,
    onConfirm: (Measurement) -> Unit,
) {
    val food = ingredient.food
    val ui = remember(food) { food.toFoodDetailUi() }
    val types = remember(food) { food.measurementTypes() }
    val suggestions =
        remember(food, ingredient.measurement) {
            (listOf(ingredient.measurement) + food.measurementSuggestions()).distinct()
        }
    val state =
        rememberFoodMeasurementFormState(
            suggestions = suggestions,
            possibleTypes = types,
            selectedMeasurement = ingredient.measurement,
        )
    val (name, brand) = remember(food.name) { splitFoodName(food.name) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(ui.emoji, fontSize = 19.sp) }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (brand != null) {
                        Text(
                            text = brand,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Text(
                text = stringResource(Res.string.description_change_ingredient_amount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            MacroSummaryRow(ui = ui, measurementState = state.measurementState)

            MeasurementPicker(
                state = state.measurementState,
                servingWeight = ui.servingWeight,
                totalWeight = ui.totalWeight,
                isLiquid = ui.isLiquid,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                Button(
                    onClick = { onConfirm(state.measurementState.measurement) },
                    enabled = state.isValid,
                ) {
                    Text(stringResource(Res.string.action_save))
                }
            }
        }
    }
}
