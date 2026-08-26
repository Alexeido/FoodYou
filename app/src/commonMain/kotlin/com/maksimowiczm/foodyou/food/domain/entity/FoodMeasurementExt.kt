package com.maksimowiczm.foodyou.food.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.WeightCalculator
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.type

/**
 * Measurement types a food can actually be measured in. Liquids use millilitres/fluid ounces,
 * solids use grams/ounces — mixing them is rejected when saving by
 * [com.maksimowiczm.foodyou.fooddiary.domain.usecase.CreateFoodDiaryEntryUseCase].
 */
fun possibleMeasurementTypesFor(
    isLiquid: Boolean,
    totalWeight: Double?,
    servingWeight: Double?,
): List<MeasurementType> =
    MeasurementType.entries.filter { type ->
        when (type) {
            MeasurementType.Gram -> !isLiquid
            MeasurementType.Ounce -> !isLiquid
            MeasurementType.Milliliter -> isLiquid
            MeasurementType.FluidOunce -> isLiquid
            MeasurementType.Package -> totalWeight != null
            MeasurementType.Serving -> servingWeight != null
        }
    }

/** The measurement a food should open with when nothing valid was supplied. */
fun defaultMeasurementFor(
    isLiquid: Boolean,
    totalWeight: Double?,
    servingWeight: Double?,
): Measurement =
    when {
        servingWeight != null -> Measurement.Serving(1.0)
        totalWeight != null -> Measurement.Package(1.0)
        isLiquid -> Measurement.Milliliter(100.0)
        else -> Measurement.Gram(100.0)
    }

/**
 * Returns [measurement] only when it is valid for this food, otherwise the default one.
 *
 * A measurement can arrive from a stale suggestion, a navigation argument, or a row written before
 * the food was marked as liquid. Checking only that a weight is computable is not enough: `Gram` on
 * a liquid computes fine but is refused when the entry is saved, so the unit itself must be checked.
 */
fun sanitizeMeasurement(
    measurement: Measurement?,
    isLiquid: Boolean,
    totalWeight: Double?,
    servingWeight: Double?,
): Measurement {
    val fallback = defaultMeasurementFor(isLiquid, totalWeight, servingWeight)

    if (measurement == null) return fallback
    if (measurement.type !in possibleMeasurementTypesFor(isLiquid, totalWeight, servingWeight)) {
        return fallback
    }

    val weight =
        WeightCalculator.calculateWeight(
            measurement = measurement,
            totalWeight = totalWeight,
            servingWeight = servingWeight,
        )

    return if (weight != null) measurement else fallback
}

fun Food.sanitizedMeasurement(measurement: Measurement?): Measurement =
    sanitizeMeasurement(
        measurement = measurement,
        isLiquid = isLiquid,
        totalWeight = totalWeight,
        servingWeight = servingWeight,
    )
