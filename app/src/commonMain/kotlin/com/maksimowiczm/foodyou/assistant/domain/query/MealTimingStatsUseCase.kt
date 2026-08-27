package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * C6 - when each meal tends to be logged.
 *
 * Built on createdAt, so it is the time the entry was written, not the time the food was eaten. Any
 * answer built on this should say so: someone who logs the whole day at night would otherwise be
 * told they dine at 23:40.
 */
class MealTimingStatsUseCase(
    private val repository: FoodDiaryEntryRepository,
    private val mealRepository: MealRepository,
) {

    suspend operator fun invoke(from: LocalDate, to: LocalDate): List<MealTiming> {
        val entries = repository.observeRange(from, to).first()
        if (entries.isEmpty()) return emptyList()

        val meals = mealRepository.observeMeals().first().associateBy { it.id }

        return entries
            .groupBy { it.mealId }
            .mapNotNull { (mealId, group) ->
                val meal = meals[mealId] ?: return@mapNotNull null
                val minutes = group.map { it.createdAt.hour * 60 + it.createdAt.minute }
                MealTiming(
                    mealId = mealId,
                    mealName = meal.name,
                    averageMinuteOfDay = minutes.sum() / minutes.size,
                    samples = minutes.size,
                )
            }
            .sortedBy { it.averageMinuteOfDay }
    }
}
