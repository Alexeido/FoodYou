package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * C1 - what the user actually eats, most frequent first.
 *
 * This matters more than it looks: handing the model the foods already in the diary is what makes a
 * generated plan resemble what the user really buys, without a word of it in the prompt.
 */
class TopFoodsUseCase(private val repository: FoodDiaryEntryRepository) {

    suspend operator fun invoke(
        from: LocalDate,
        to: LocalDate,
        limit: Int = 15,
        mealId: Long? = null,
    ): List<FoodFrequency> {
        val entries =
            repository
                .observeRange(from, to)
                .first()
                .filter { mealId == null || it.mealId == mealId }

        return entries
            .groupBy { it.food.name }
            .map { (name, group) ->
                FoodFrequency(
                    name = name,
                    brand = null,
                    times = group.size,
                    totalGrams = group.sumOf { it.weight },
                    totalEnergy = group.sumOf { it.nutritionFacts.energy.value ?: 0.0 },
                )
            }
            .sortedWith(compareByDescending<FoodFrequency> { it.times }.thenByDescending { it.totalEnergy })
            .take(limit)
    }
}
