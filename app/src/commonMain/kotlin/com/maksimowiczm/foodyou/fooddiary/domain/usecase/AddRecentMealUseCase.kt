package com.maksimowiczm.foodyou.fooddiary.domain.usecase

import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * Re-adds a whole past meal into a target meal + day at once. Each source entry is re-created via
 * [CreateFoodDiaryEntryUseCase], so every new entry gets its own fresh diary snapshot.
 */
class AddRecentMealUseCase(
    private val entryRepository: FoodDiaryEntryRepository,
    private val createFoodDiaryEntryUseCase: CreateFoodDiaryEntryUseCase,
) {
    suspend fun add(
        sourceMealId: Long,
        sourceDate: LocalDate,
        targetMealId: Long,
        targetDate: LocalDate,
    ) {
        val entries = entryRepository.observeAll(sourceMealId, sourceDate).first()

        entries.forEach { entry ->
            val _ =
                createFoodDiaryEntryUseCase.createDiaryEntry(
                    measurement = entry.measurement,
                    mealId = targetMealId,
                    date = targetDate,
                    food = entry.food,
                )
        }
    }
}
