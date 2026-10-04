package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.runtime.*
import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField

@Immutable
internal data class DaySummaryModel(
    val energy: Int,
    val energyGoal: Int,
    val proteins: Int,
    val proteinsGoal: Int,
    val carbohydrates: Int,
    val carbohydratesGoal: Int,
    val fats: Int,
    val fatsGoal: Int,
    val tracked: List<TrackedNutrientModel> = emptyList(),
)

/**
 * A nutrient the person wants to reach, in grams. [complete] is false when some food of the day
 * doesn't say how much it has: the real figure is then at least [grams].
 */
@Immutable
internal data class TrackedNutrientModel(
    val field: NutritionFactsField,
    val grams: Double,
    val goalGrams: Double,
    val complete: Boolean,
)
