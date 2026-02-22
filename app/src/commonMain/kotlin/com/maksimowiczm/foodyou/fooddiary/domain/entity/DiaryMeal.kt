package com.maksimowiczm.foodyou.fooddiary.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.sum

data class DiaryMeal(val meal: Meal, val entries: List<DiaryEntry>) {
    // Sum only the nutrition facts of entries that are marked as eaten.
    val nutritionFacts: NutritionFacts = entries.filter { it.isEaten }.map { it.nutritionFacts }.sum()
}
