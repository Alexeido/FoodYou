package com.maksimowiczm.foodyou.app.ui.food.diary.component

import androidx.compose.runtime.*
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerState
import com.maksimowiczm.foodyou.app.ui.food.component.rememberMeasurementPickerState
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType

@Composable
fun rememberFoodMeasurementFormState(
    suggestions: List<Measurement>,
    possibleTypes: List<MeasurementType>,
    selectedMeasurement: Measurement,
): FoodMeasurementFormState {
    val measurementState =
        rememberMeasurementPickerState(
            suggestions = suggestions,
            possibleTypes = possibleTypes,
            selectedMeasurement = selectedMeasurement,
        )

    return remember(measurementState) {
        FoodMeasurementFormState(measurementState)
    }
}

@Stable
class FoodMeasurementFormState(
    val measurementState: MeasurementPickerState,
) {
    val isValid by derivedStateOf {
        measurementState.inputField.error == null
    }
}
