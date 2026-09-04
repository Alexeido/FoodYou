package com.maksimowiczm.foodyou.assistant.domain.query

import kotlinx.datetime.LocalDate

/**
 * C1 - what the user actually eats, most frequent first.
 *
 * This matters more than it looks: handing the model the foods already in the diary is what makes a
 * generated plan resemble what the user really buys, without a word of it in the prompt.
 */
class TopFoodsUseCase(private val diary: DiaryReader) {

    suspend operator fun invoke(
        from: LocalDate,
        to: LocalDate,
        limit: Int = 15,
        mealId: Long? = null,
    ): List<FoodFrequency> {
        val lines = diary.range(from, to).filter { mealId == null || it.mealId == mealId }

        return lines
            .groupBy { it.name }
            .map { (name, group) ->
                FoodFrequency(
                    name = name,
                    brand = null,
                    times = group.size,
                    totalGrams = group.sumOf { it.grams ?: 0.0 },
                    totalEnergy = group.sumOf { it.nutritionFacts.energy.value ?: 0.0 },
                )
            }
            .sortedWith(compareByDescending<FoodFrequency> { it.times }.thenByDescending { it.totalEnergy })
            .take(limit)
    }
}
