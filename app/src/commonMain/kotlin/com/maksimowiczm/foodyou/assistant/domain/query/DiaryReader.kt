package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * One line of the diary, flattened out of whichever entity backs it.
 *
 * Bug found by testing: every read use case in this package used to be built only on
 * [FoodDiaryEntryRepository], which only sees products and recipes. An entry created by
 * [com.maksimowiczm.foodyou.assistant.domain.tool.write.CreateManualEntryTool] - or added by hand
 * from the diary UI without a matching product - lives in a separate table and was invisible to
 * every question the assistant could answer about its own diary. [DiaryLine] and [DiaryReader]
 * exist so that gap can't reopen quietly: every query in this package reads through here now, never
 * through [FoodDiaryEntryRepository] directly.
 */
data class DiaryLine(
    val date: LocalDate,
    val mealId: Long,
    val name: String,
    val nutritionFacts: NutritionFacts,
    /** Null for a manual entry: it was never a weight, just a total the model estimated. */
    val grams: Double?,
    val isEaten: Boolean,
    val createdAt: LocalDateTime,
)

class DiaryReader(
    private val entryRepository: FoodDiaryEntryRepository,
    private val manualRepository: ManualDiaryEntryRepository,
) {
    suspend fun range(from: LocalDate, to: LocalDate): List<DiaryLine> {
        val fromProducts =
            entryRepository.observeRange(from, to).first().map { entry ->
                DiaryLine(
                    date = entry.date,
                    mealId = entry.mealId,
                    name = entry.food.name,
                    nutritionFacts = entry.nutritionFacts,
                    grams = entry.weight,
                    isEaten = entry.isEaten,
                    createdAt = entry.createdAt,
                )
            }

        val fromManual =
            manualRepository.observeRange(from, to).first().map { entry ->
                DiaryLine(
                    date = entry.date,
                    mealId = entry.mealId,
                    name = entry.name,
                    nutritionFacts = entry.nutritionFacts,
                    grams = null,
                    isEaten = entry.isEaten,
                    createdAt = entry.createdAt,
                )
            }

        return (fromProducts + fromManual).sortedWith(compareBy({ it.date }, { it.mealId }))
    }
}
