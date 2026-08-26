package com.maksimowiczm.foodyou.fooddiary.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import kotlinx.datetime.LocalDate

/** A reference to a past logged meal (a meal section on a specific day). */
data class RecentMealRef(val mealId: Long, val date: LocalDate)

/**
 * A hydrated recent meal shown in the search "Recent meals" section: the whole block's summary,
 * ready to be re-added at once.
 */
data class RecentMeal(
    val mealId: Long,
    val date: LocalDate,
    val mealName: String,
    val icon: String?,
    val itemNames: List<String>,
    val nutritionFacts: NutritionFacts,
    val entryCount: Int,
)
