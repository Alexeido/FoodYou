package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * C4 - find something inside the diary history, as opposed to the food catalogue.
 *
 * Answers "when did I last eat salmon", which the catalogue search cannot: it knows salmon exists,
 * not that you ate it.
 */
class SearchDiaryUseCase(private val repository: FoodDiaryEntryRepository) {

    suspend operator fun invoke(
        query: String,
        from: LocalDate,
        to: LocalDate,
        limit: Int = 20,
    ): List<DiaryHit> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()

        return repository
            .observeRange(from, to)
            .first()
            .filter { it.food.name.contains(needle, ignoreCase = true) }
            .sortedByDescending { it.date }
            .take(limit)
            .map { entry ->
                DiaryHit(
                    date = entry.date,
                    mealId = entry.mealId,
                    name = entry.food.name,
                    grams = entry.weight,
                    energy = entry.nutritionFacts.energy.value ?: 0.0,
                )
            }
    }
}
