package com.maksimowiczm.foodyou.assistant.domain.query

import kotlinx.datetime.LocalDate

/**
 * C3 - which foods a given nutrient came from over a range.
 *
 * The basis for "where is all this fat coming from" and for "what do I cut to save 300 kcal without
 * losing protein", which is the question the model cannot answer from totals alone.
 */
class NutrientAttributionUseCase(private val diary: DiaryReader) {

    suspend operator fun invoke(
        nutrient: NutrientSelector,
        from: LocalDate,
        to: LocalDate,
        limit: Int = 10,
    ): List<NutrientContribution> {
        val entries = diary.range(from, to)

        val perFood =
            entries
                .groupBy { it.name }
                .map { (name, group) -> name to group.sumOf { nutrient.of(it.nutritionFacts) } }
                .filter { (_, amount) -> amount > 0.0 }

        val total = perFood.sumOf { (_, amount) -> amount }
        if (total <= 0.0) return emptyList()

        return perFood
            .sortedByDescending { (_, amount) -> amount }
            .take(limit)
            .map { (name, amount) ->
                NutrientContribution(
                    name = name,
                    brand = null,
                    amount = amount,
                    share = amount / total,
                )
            }
    }
}
