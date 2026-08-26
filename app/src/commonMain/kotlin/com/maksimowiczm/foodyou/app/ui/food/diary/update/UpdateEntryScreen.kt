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
import androidx.compose.material.icons.rounded.Check
import com.maksimowiczm.foodyou.app.ui.food.diary.component.FoodDetailUi
import com.maksimowiczm.foodyou.app.ui.food.diary.component.FoodEntryDetailScaffold
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
    val food = entry.food
    val ui =
        remember(food) {
            FoodDetailUi(
                name = food.name,
                emoji =
                    when (food) {
                        is DiaryFoodProduct -> getFoodCategoryFromTags(food.categories).emoji
                        is DiaryFoodRecipe -> FoodCategory.UNKNOWN.emoji
                    },
                nutritionFacts = food.nutritionFacts,
                isLiquid = food.isLiquid,
                note = food.note,
                totalWeight = food.totalWeight,
                servingWeight = food.servingWeight,
                source = (food as? DiaryFoodProduct)?.source,
                weightOf = food::weight,
            )
        }

    FoodEntryDetailScaffold(
        ui = ui,
        measurementState = state.measurementState,
        isValid = state.isValid,
        onBack = onBack,
        onConfirm = onSave,
        // Editing an entry that already exists: this confirms the change rather than adding a row.
        confirmIcon = Icons.Rounded.Check,
        confirmDescription = stringResource(Res.string.action_save),
        fabVisible = true,
        canUnpack = food is DiaryFoodRecipe,
        onUnpack = onUnpack,
        animatedVisibilityScope = animatedVisibilityScope,
        modifier = modifier,
        ingredients =
            if (food is DiaryFoodRecipe) {
                { measurement ->
                    Ingredients(
                        ingredients = food.unpack(measurement),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            } else {
                null
            },
    )
}
