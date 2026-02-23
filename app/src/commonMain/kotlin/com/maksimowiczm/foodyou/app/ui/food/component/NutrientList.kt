package com.maksimowiczm.foodyou.app.ui.food.shared.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalNutrientsOrder
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun NutrientList(
    facts: NutritionFacts,
    modifier: Modifier = Modifier,
    incompleteValue: (NutrientValue.Incomplete) -> (@Composable () -> Unit) =
        NutrientListDefaults::incompleteValue,
) {
    val order = LocalNutrientsOrder.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Energy(facts, incompleteValue)

        order.forEach {
            when (it) {
                NutrientsOrder.Proteins -> Proteins(facts, incompleteValue)
                NutrientsOrder.Fats -> Fats(facts, incompleteValue)
                NutrientsOrder.Carbohydrates -> Carbohydrates(facts, incompleteValue)
                NutrientsOrder.Other -> Other(facts, incompleteValue)
                NutrientsOrder.Vitamins -> Vitamins(facts, incompleteValue)
                NutrientsOrder.Minerals -> Minerals(facts, incompleteValue)
            }
        }
    }
}

@Composable
private fun Energy(
    facts: NutritionFacts,
    incompleteValue: (NutrientValue.Incomplete) -> (@Composable () -> Unit),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    val energyFormatter = LocalEnergyFormatter.current

    Nutrient(
        label = { Text(stringResource(Res.string.unit_energy)) },
        value = {
            when (val nut = facts.energy) {
                is NutrientValue.Complete -> Text(energyFormatter.formatEnergy(nut.value))
                is NutrientValue.Incomplete -> incompleteValue(nut)()
            }
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun Proteins(
    facts: NutritionFacts,
    incompleteValue: (NutrientValue.Incomplete) -> (@Composable () -> Unit),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    Nutrient(
        label = { Text(stringResource(Res.string.nutriment_proteins)) },
        value = { NutrientDisplay(facts.proteins, incompleteValue) },
        modifier = modifier,
        contentPadding = contentPadding,
        shape = MaterialTheme.shapes.medium,
        containerColor =
            LocalNutrientsPalette.current.proteinsOnSurfaceContainer.copy(alpha = .33f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Fats(
    facts: NutritionFacts,
    incompleteValue: (NutrientValue.Incomplete) -> (@Composable () -> Unit),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    val fats = @Composable { NutrientDisplay(facts.fats, incompleteValue) }
    val saturatedFats = @Composable { NutrientDisplay(facts.saturatedFats, incompleteValue) }
    val transFats = @Composable { NutrientDisplay(facts.transFats, incompleteValue) }
    val monounsaturatedFats =
        @Composable { NutrientDisplay(facts.monounsaturatedFats, incompleteValue) }
    val polyunsaturatedFats =
        @Composable { NutrientDisplay(facts.polyunsaturatedFats, incompleteValue) }
    val omega3Fats = @Composable { NutrientDisplay(facts.omega3, incompleteValue) }
    val omega6Fats = @Composable { NutrientDisplay(facts.omega6, incompleteValue) }

    NutrientGroup(
        title = {
            Nutrient(
                label = { Text(stringResource(Res.string.nutriment_fats)) },
                value = fats,
                contentPadding = contentPadding,
                shape = MaterialTheme.shapes.medium,
                containerColor =
                    LocalNutrientsPalette.current.fatsOnSurfaceContainer.copy(alpha = .33f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
        modifier = modifier,
    ) {
        if (facts.saturatedFats.hasValue()) Nutrient(
            label = { Text(stringResource(Res.string.nutriment_saturated_fats)) },
            value = saturatedFats,
        )
        if (facts.transFats.hasValue()) Nutrient(
            label = { Text(stringResource(Res.string.nutriment_trans_fats)) },
            value = transFats,
        )
        if (facts.monounsaturatedFats.hasValue()) Nutrient(
            label = { Text(stringResource(Res.string.nutriment_monounsaturated_fats)) },
            value = monounsaturatedFats,
        )

        if (facts.polyunsaturatedFats.hasValue() || facts.omega3.hasValue() || facts.omega6.hasValue()) {
            NutrientGroup(
                title = {
                    Nutrient(
                        label = { Text(stringResource(Res.string.nutriment_polyunsaturated_fats)) },
                        value = polyunsaturatedFats,
                    )
                }
            ) {
                if (facts.omega3.hasValue()) Nutrient(
                    label = { Text(stringResource(Res.string.nutriment_omega_3)) },
                    value = omega3Fats,
                )
                if (facts.omega6.hasValue()) Nutrient(
                    label = { Text(stringResource(Res.string.nutriment_omega_6)) },
                    value = omega6Fats,
                )
            }
        }
    }
}

@Composable
private fun Carbohydrates(
    facts: NutritionFacts,
    incompleteValue: (NutrientValue.Incomplete) -> (@Composable () -> Unit),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    NutrientGroup(
        title = {
            Nutrient(
                label = { Text(stringResource(Res.string.nutriment_carbohydrates)) },
                value = { NutrientDisplay(facts.carbohydrates, incompleteValue) },
                modifier = modifier,
                contentPadding = contentPadding,
                shape = MaterialTheme.shapes.medium,
                containerColor =
                    LocalNutrientsPalette.current.carbohydratesOnSurfaceContainer.copy(
                        alpha = .33f
                    ),
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    ) {
        if (facts.sugars.hasValue() || facts.addedSugars.hasValue()) {
            NutrientGroup(
                title = {
                    Nutrient(
                        label = { Text(stringResource(Res.string.nutriment_sugars)) },
                        value = { NutrientDisplay(facts.sugars, incompleteValue) },
                    )
                }
            ) {
                if (facts.addedSugars.hasValue()) Nutrient(
                    label = { Text(stringResource(Res.string.nutriment_added_sugars)) },
                    value = { NutrientDisplay(facts.addedSugars, incompleteValue) },
                )
            }
        }
        if (facts.dietaryFiber.hasValue() || facts.solubleFiber.hasValue() || facts.insolubleFiber.hasValue()) {
            NutrientGroup(
                title = {
                    Nutrient(
                        label = { Text(stringResource(Res.string.nutriment_fiber)) },
                        value = { NutrientDisplay(facts.dietaryFiber, incompleteValue) },
                    )
                }
            ) {
                if (facts.solubleFiber.hasValue()) Nutrient(
                    label = { Text(stringResource(Res.string.nutriment_soluble_fiber)) },
                    value = { NutrientDisplay(facts.solubleFiber, incompleteValue) },
                )
                if (facts.insolubleFiber.hasValue()) Nutrient(
                    label = { Text(stringResource(Res.string.nutriment_insoluble_fiber)) },
                    value = { NutrientDisplay(facts.insolubleFiber, incompleteValue) },
                )
            }
        }
    }
}

@Composable
private fun Other(
    facts: NutritionFacts,
    incompleteValue: (NutrientValue.Incomplete) -> (@Composable () -> Unit),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    val mg = stringResource(Res.string.unit_milligram_short)

    val entries = buildList {
        if (facts.salt.hasValue()) add(NutrientEntry(facts.salt.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.nutriment_salt)) },
                value = { NutrientDisplay(facts.salt, incompleteValue) },
                contentPadding = contentPadding,
            )
        })
        if (facts.cholesterol.hasValue()) add(NutrientEntry(facts.cholesterol.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.nutriment_cholesterol)) },
                value = { NutrientDisplay(facts.cholesterol * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.caffeine.hasValue()) add(NutrientEntry(facts.caffeine.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.nutriment_caffeine)) },
                value = { NutrientDisplay(facts.caffeine * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
    }.sortedByDescending { it.sortKey }

    if (entries.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(contentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.headline_other),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column { entries.forEach { it.content() } }
        }
    }
}

@Composable
private fun Vitamins(
    facts: NutritionFacts,
    incompleteValue: (NutrientValue.Incomplete) -> (@Composable () -> Unit),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    val mg = stringResource(Res.string.unit_milligram_short)
    val mcg = stringResource(Res.string.unit_microgram_short)

    val entries = buildList {
        if (facts.vitaminA.hasValue()) add(NutrientEntry(facts.vitaminA.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_a)) },
                value = { NutrientDisplay(facts.vitaminA * 1_000_000.0, incompleteValue, mcg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminB1.hasValue()) add(NutrientEntry(facts.vitaminB1.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_b1)) },
                value = { NutrientDisplay(facts.vitaminB1 * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminB2.hasValue()) add(NutrientEntry(facts.vitaminB2.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_b2)) },
                value = { NutrientDisplay(facts.vitaminB2 * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminB3.hasValue()) add(NutrientEntry(facts.vitaminB3.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_b3)) },
                value = { NutrientDisplay(facts.vitaminB3 * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminB5.hasValue()) add(NutrientEntry(facts.vitaminB5.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_b5)) },
                value = { NutrientDisplay(facts.vitaminB5 * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminB6.hasValue()) add(NutrientEntry(facts.vitaminB6.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_b6)) },
                value = { NutrientDisplay(facts.vitaminB6 * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminB7.hasValue()) add(NutrientEntry(facts.vitaminB7.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_b7)) },
                value = { NutrientDisplay(facts.vitaminB7 * 1_000_000.0, incompleteValue, mcg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminB9.hasValue()) add(NutrientEntry(facts.vitaminB9.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_b9)) },
                value = { NutrientDisplay(facts.vitaminB9 * 1_000_000.0, incompleteValue, mcg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminB12.hasValue()) add(NutrientEntry(facts.vitaminB12.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_b12)) },
                value = { NutrientDisplay(facts.vitaminB12 * 1_000_000.0, incompleteValue, mcg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminC.hasValue()) add(NutrientEntry(facts.vitaminC.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_c)) },
                value = { NutrientDisplay(facts.vitaminC * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminD.hasValue()) add(NutrientEntry(facts.vitaminD.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_d)) },
                value = { NutrientDisplay(facts.vitaminD * 1_000_000.0, incompleteValue, mcg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminE.hasValue()) add(NutrientEntry(facts.vitaminE.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_e)) },
                value = { NutrientDisplay(facts.vitaminE * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.vitaminK.hasValue()) add(NutrientEntry(facts.vitaminK.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.vitamin_k)) },
                value = { NutrientDisplay(facts.vitaminK * 1_000_000.0, incompleteValue, mcg) },
                contentPadding = contentPadding,
            )
        })
    }.sortedByDescending { it.sortKey }

    if (entries.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(contentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.headline_vitamins),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column { entries.forEach { it.content() } }
        }
    }
}

@Composable
private fun Minerals(
    facts: NutritionFacts,
    incompleteValue: (NutrientValue.Incomplete) -> (@Composable () -> Unit),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    val mg = stringResource(Res.string.unit_milligram_short)
    val mcg = stringResource(Res.string.unit_microgram_short)

    val entries = buildList {
        if (facts.manganese.hasValue()) add(NutrientEntry(facts.manganese.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_manganese)) },
                value = { NutrientDisplay(facts.manganese * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.magnesium.hasValue()) add(NutrientEntry(facts.magnesium.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_magnesium)) },
                value = { NutrientDisplay(facts.magnesium * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.potassium.hasValue()) add(NutrientEntry(facts.potassium.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_potassium)) },
                value = { NutrientDisplay(facts.potassium * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.calcium.hasValue()) add(NutrientEntry(facts.calcium.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_calcium)) },
                value = { NutrientDisplay(facts.calcium * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.copper.hasValue()) add(NutrientEntry(facts.copper.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_copper)) },
                value = { NutrientDisplay(facts.copper * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.zinc.hasValue()) add(NutrientEntry(facts.zinc.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_zinc)) },
                value = { NutrientDisplay(facts.zinc * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.sodium.hasValue()) add(NutrientEntry(facts.sodium.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_sodium)) },
                value = { NutrientDisplay(facts.sodium * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.iron.hasValue()) add(NutrientEntry(facts.iron.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_iron)) },
                value = { NutrientDisplay(facts.iron * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.phosphorus.hasValue()) add(NutrientEntry(facts.phosphorus.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_phosphorus)) },
                value = { NutrientDisplay(facts.phosphorus * 1_000.0, incompleteValue, mg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.selenium.hasValue()) add(NutrientEntry(facts.selenium.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_selenium)) },
                value = { NutrientDisplay(facts.selenium * 1_000_000.0, incompleteValue, mcg) },
                contentPadding = contentPadding,
            )
        })
        if (facts.chromium.hasValue()) add(NutrientEntry(facts.chromium.sortableValue()) {
            Nutrient(
                label = { Text(stringResource(Res.string.mineral_chromium)) },
                value = { NutrientDisplay(facts.chromium * 1_000_000.0, incompleteValue, mcg) },
                contentPadding = contentPadding,
            )
        })
    }.sortedByDescending { it.sortKey }

    if (entries.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(contentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.headline_minerals),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column { entries.forEach { it.content() } }
        }
    }
}

@Composable
private fun Nutrient(
    label: @Composable () -> Unit,
    value: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    shape: Shape = RectangleShape,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = LocalContentColor.current,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                label()
                value()
            }
        }
    }
}

private fun NutrientValue.hasValue(): Boolean =
    !(this is NutrientValue.Incomplete && this.value == null)

private class NutrientEntry(val sortKey: Double, val content: @Composable () -> Unit)

private fun NutrientValue.sortableValue(): Double = when (this) {
    is NutrientValue.Complete -> value
    is NutrientValue.Incomplete -> value ?: 0.0
}

@Composable
private fun NutrientGroup(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        title()
        Column(modifier = Modifier.padding(start = 16.dp)) { content() }
    }
}

@Composable
private fun NutrientDisplay(
    nutrientValue: NutrientValue,
    incompleteValue: (NutrientValue.Incomplete) -> (@Composable () -> Unit),
    suffix: String = stringResource(Res.string.unit_gram_short),
) {
    when (nutrientValue) {
        is NutrientValue.Complete -> {
            val value = nutrientValue.value.formatClipZeros() + " " + suffix
            Text(value)
        }

        is NutrientValue.Incomplete -> {
            incompleteValue(nutrientValue)()
        }
    }
}

object NutrientListDefaults {
    val incompletePrefix: String
        @Composable get() = "*"

    fun incompleteValue(value: NutrientValue.Incomplete): @Composable () -> Unit = {
        val value = value.value?.formatClipZeros()

        val str =
            value?.let { "$incompletePrefix $it ${stringResource(Res.string.unit_gram_short)}" }
                ?: stringResource(Res.string.not_available_short)

        Text(text = str, color = MaterialTheme.colorScheme.outline)
    }
}
