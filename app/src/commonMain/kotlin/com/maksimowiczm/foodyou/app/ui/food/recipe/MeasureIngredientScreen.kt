package com.maksimowiczm.foodyou.app.ui.food.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.component.IncompleteFoodsList
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.app.ui.food.component.EnergyProgressIndicator
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPicker
import com.maksimowiczm.foodyou.app.ui.food.component.rememberMeasurementPickerState
import com.maksimowiczm.foodyou.app.ui.food.shared.component.NutrientList
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.domain.food.isComplete
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.Product
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import com.maksimowiczm.foodyou.app.ui.food.diary.component.FoodDetailUi
import com.maksimowiczm.foodyou.app.ui.food.diary.component.FoodEntryDetailScaffold
import com.maksimowiczm.foodyou.app.ui.food.search.FoodCategory
import com.maksimowiczm.foodyou.app.ui.food.search.getFoodCategoryFromTags
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.sanitizedMeasurement
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MeasureIngredientScreen(
    onBack: () -> Unit,
    measurement: Measurement,
    viewModel: MeasureIngredientViewModel,
    onSave: (Measurement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val possibleMeasurements = viewModel.possibleMeasurements.collectAsStateWithLifecycle().value
    val suggestions = viewModel.suggestions.collectAsStateWithLifecycle().value
    val food = viewModel.food.collectAsStateWithLifecycle().value

    if (possibleMeasurements == null || suggestions == null || food == null) {
        // TODO loading state
        return
    }

    // Reject units that don't apply to this food (e.g. grams for a liquid), not just ones whose
    // weight can't be computed.
    val selectedMeasurement = remember(measurement, food) { food.sanitizedMeasurement(measurement) }

    val measurementPickerState =
        rememberMeasurementPickerState(
            suggestions = suggestions,
            possibleTypes = possibleMeasurements,
            selectedMeasurement = selectedMeasurement,
        )

    val ui =
        remember(food) {
            FoodDetailUi(
                name = food.headline,
                emoji =
                    when (food) {
                        is Product -> getFoodCategoryFromTags(food.categories).emoji
                        else -> FoodCategory.UNKNOWN.emoji
                    },
                nutritionFacts = food.nutritionFacts,
                isLiquid = food.isLiquid,
                note = (food as? Product)?.note,
                totalWeight = food.totalWeight,
                servingWeight = food.servingWeight,
                source = (food as? Product)?.source,
                weightOf = { food.weight(it) ?: 0.0 },
            )
        }

    FoodEntryDetailScaffold(
        ui = ui,
        measurementState = measurementPickerState,
        isValid = true,
        onBack = onBack,
        onConfirm = { onSave(measurementPickerState.measurement) },
        // Same gesture as the diary: this puts the food into the recipe being built.
        confirmIcon = Icons.AutoMirrored.Filled.PlaylistAdd,
        confirmDescription = stringResource(Res.string.action_save),
        fabVisible = true,
        canUnpack = false,
        onUnpack = {},
        modifier = modifier,
        extraNutrientContent =
            if (food is Recipe) {
                {
                    val incompleteIngredients =
                        food
                            .flatIngredients()
                            .filter { it is Product }
                            .filterNot { it.nutritionFacts.isComplete }

                    IncompleteFoodsList(
                        foods = incompleteIngredients.map { it.headline }.distinct(),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                null
            },
    )
}
