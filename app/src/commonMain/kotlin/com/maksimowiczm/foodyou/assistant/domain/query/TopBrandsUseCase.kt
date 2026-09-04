package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.app.ui.food.component.parseHeadlineBrand
import kotlinx.datetime.LocalDate

/**
 * C2 - which brands show up most in the diary.
 *
 * The diary stores an embedded snapshot whose name already carries the brand in "Name (Brand)"
 * form, so the brand is recovered with the same parser the UI uses rather than by joining back to
 * the product table - which would miss every entry whose product has since been purged. A manual
 * entry's name never has that shape, so it simply contributes no brand - harmless to include.
 */
class TopBrandsUseCase(private val diary: DiaryReader) {

    suspend operator fun invoke(from: LocalDate, to: LocalDate, limit: Int = 10): List<BrandFrequency> {
        val entries = diary.range(from, to)

        return entries
            .mapNotNull { entry ->
                val brand = parseHeadlineBrand(entry.name).second?.trim()
                if (brand.isNullOrEmpty()) null else brand to entry
            }
            .groupBy({ it.first }, { it.second })
            .map { (brand, group) ->
                BrandFrequency(
                    brand = brand,
                    times = group.size,
                    totalEnergy = group.sumOf { it.nutritionFacts.energy.value ?: 0.0 },
                )
            }
            .sortedByDescending { it.times }
            .take(limit)
    }
}
