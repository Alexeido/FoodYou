package com.maksimowiczm.foodyou.assistant.domain.query

import kotlinx.datetime.LocalDate

/**
 * C4 - find something inside the diary history, as opposed to the food catalogue.
 *
 * Answers "when did I last eat salmon", which the catalogue search cannot: it knows salmon exists,
 * not that you ate it.
 */
class SearchDiaryUseCase(private val diary: DiaryReader) {

    suspend operator fun invoke(
        query: String,
        from: LocalDate,
        to: LocalDate,
        limit: Int = 20,
    ): List<DiaryHit> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()

        return diary
            .range(from, to)
            .filter { it.name.contains(needle, ignoreCase = true) }
            .sortedByDescending { it.date }
            .take(limit)
            .map { entry ->
                DiaryHit(
                    date = entry.date,
                    mealId = entry.mealId,
                    name = entry.name,
                    grams = entry.grams ?: 0.0,
                    energy = entry.nutritionFacts.energy.value ?: 0.0,
                )
            }
    }
}
