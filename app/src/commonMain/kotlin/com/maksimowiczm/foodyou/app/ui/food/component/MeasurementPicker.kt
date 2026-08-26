package com.maksimowiczm.foodyou.app.ui.food.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.form.FormField
import com.maksimowiczm.foodyou.app.ui.common.form.nullableFloatParser
import com.maksimowiczm.foodyou.app.ui.common.form.positiveFloatValidator
import com.maksimowiczm.foodyou.app.ui.common.form.rememberFormField
import com.maksimowiczm.foodyou.app.ui.common.utility.Saver
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResource
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun MeasurementPicker(
    state: MeasurementPickerState,
    modifier: Modifier = Modifier,
    servingWeight: Double? = null,
    totalWeight: Double? = null,
    isLiquid: Boolean = false,
) {
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(state.inputField.value, state.type) {
        val value = state.inputField.value ?: return@LaunchedEffect
        val measurement = Measurement.from(state.type, value.toDouble())
        latestState.measurement = measurement
    }

    Column(modifier) {
        Input(
            formField = state.inputField,
            type = state.type,
            types = state.possibleTypes,
            onSelect = { state.type = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(8.dp))

        // One scrolling row rather than a wrapping grid: the suggestions are a shortcut, not a
        // list to read, and two rows of them pushed the nutrients off screen.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.suggestions) { measurement ->
                val chipLabel =
                    measurement.stringResourceWithWeight(
                        totalWeight = totalWeight,
                        servingWeight = servingWeight,
                        isLiquid = isLiquid,
                    ) ?: measurement.stringResource()
                val selected = state.measurement == measurement
                FilterChip(
                    selected = selected,
                    onClick = {
                        state.inputField.textFieldState.setTextAndPlaceCursorAtEnd(
                            text = measurement.rawValue.formatClipZeros()
                        )
                        state.type = measurement.type
                    },
                    label = {
                        Text(text = chipLabel, style = MaterialTheme.typography.labelLarge)
                    },
                    shape = RoundedCornerShape(10.dp),
                )
            }
        }
    }
}

@Composable
private fun Input(
    formField: FormField<Float?, String>,
    type: MeasurementType,
    types: List<MeasurementType>,
    onSelect: (MeasurementType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Toned down on purpose: the strong primary container is reserved for the screen's single
    // primary action (the FAB), so the amount field doesn't compete with it.
    val inputColor by
        animateColorAsState(
            targetValue =
                if (formField.error == null) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
        )
    val contentColor by
        animateColorAsState(
            targetValue =
                if (formField.error == null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
        )

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.height(52.dp).weight(1f),
            color = inputColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            BasicTextField(
                state = formField.textFieldState,
                modifier = Modifier.height(52.dp).padding(horizontal = 16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.merge(LocalContentColor.current),
                lineLimits = TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(LocalContentColor.current),
                decorator = { Box(contentAlignment = Alignment.CenterStart) { it() } },
            )
        }

        Surface(
            onClick = { expanded = true },
            modifier = Modifier.height(52.dp).width(124.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = type.stringResource(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Outlined.KeyboardArrowDown, contentDescription = null)

                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        types.forEach {
                            DropdownMenuItem(
                                text = { Text(it.stringResource()) },
                                onClick = {
                                    onSelect(it)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberMeasurementPickerState(
    suggestions: List<Measurement>,
    possibleTypes: List<MeasurementType>,
    selectedMeasurement: Measurement,
): MeasurementPickerState =
    // Keyed on the incoming measurement: `rememberSaveable`/`rememberTextFieldState` below keep
    // their value per composition slot, so without this the unit and amount of the previously
    // opened food are restored for the next one — which is how a liquid ended up opening in grams
    // after any solid had been measured.
    key(selectedMeasurement) {
        val inputField =
            rememberFormField(
                initialValue = selectedMeasurement.rawValue.toFloat(),
                parser = nullableFloatParser(onNotANumber = { "Invalid number format" }),
                validator =
                    positiveFloatValidator(
                        onNotPositive = { "Value must be positive" },
                        onNull = { "Value cannot be empty" },
                    ),
                textFieldState =
                    rememberTextFieldState(selectedMeasurement.rawValue.formatClipZeros()),
                validateFirst = true,
            )
        val typeState = rememberSaveable { mutableStateOf(selectedMeasurement.type) }
        val measurementState =
            rememberSaveable(selectedMeasurement, stateSaver = Measurement.Saver) {
                mutableStateOf(selectedMeasurement)
            }

        // Safety net: never sit on a unit this food can't be measured in.
        LaunchedEffect(possibleTypes, selectedMeasurement) {
            if (possibleTypes.isNotEmpty() && typeState.value !in possibleTypes) {
                typeState.value = selectedMeasurement.type
            }
        }

        remember(suggestions, possibleTypes, inputField, typeState, measurementState) {
            MeasurementPickerState(
                suggestions = suggestions,
                possibleTypes = possibleTypes,
                inputField = inputField,
                measurementState = measurementState,
                typeState = typeState,
            )
        }
    }

class MeasurementPickerState(
    val suggestions: List<Measurement>,
    val possibleTypes: List<MeasurementType>,
    val inputField: FormField<Float?, String>,
    measurementState: MutableState<Measurement>,
    typeState: MutableState<MeasurementType>,
) {
    var measurement by measurementState
    var type by typeState

    /**
     * Sets the amount from a computed weight (used by the tappable macro cells).
     *
     * Picks the unit the food actually supports: grams for solids, millilitres for liquids (the app
     * treats 1 ml as 1 g throughout). Forcing [MeasurementType.Gram] here used to leave liquids on a
     * unit they can't be saved with.
     */
    fun setWeightGrams(grams: Float) {
        type =
            when {
                MeasurementType.Gram in possibleTypes -> MeasurementType.Gram
                MeasurementType.Milliliter in possibleTypes -> MeasurementType.Milliliter
                else -> type
            }
        inputField.textFieldState.setTextAndPlaceCursorAtEnd(grams.toDouble().formatClipZeros())
    }
}
