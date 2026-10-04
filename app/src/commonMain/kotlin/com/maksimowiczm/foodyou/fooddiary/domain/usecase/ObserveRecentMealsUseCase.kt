package com.maksimowiczm.foodyou.fooddiary.domain.usecase

import com.maksimowiczm.foodyou.common.domain.food.sum
import com.maksimowiczm.foodyou.common.extension.combine
import com.maksimowiczm.foodyou.fooddiary.domain.entity.RecentMeal
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Observes recently logged meals for the "Recent meals" section of search.
 *
 * Only days **strictly before** the day being filled in are offered: the point is to repeat what
 * you ate previously, not to re-add something from the day you are already building. This follows
 * the target date, not today — filling in tomorrow's diary offers today and earlier.
 *
 * Within that, blocks from the same meal section come first (breakfast suggests breakfasts), then
 * most recent day first.
 */
class ObserveRecentMealsUseCase(
    private val entryRepository: FoodDiaryEntryRepository,
    private val mealRepository: MealRepository,
) {
    fun observe(
        currentMealId: Long?,
        currentDate: LocalDate?,
        limit: Int = 8,
    ): Flow<List<RecentMeal>> =
        // Fetch a wider window than `limit`: filtering out a whole day can discard several groups.
        entryRepository.observeRecentMealRefs(limit * 4).flatMapLatest { refs ->
            val filtered =
                if (currentDate == null) refs else refs.filter { it.date < currentDate }

            if (filtered.isEmpty()) {
                return@flatMapLatest flowOf(emptyList())
            }

            filtered
                .map { ref ->
                    combine(
                        mealRepository.observeMeal(ref.mealId),
                        entryRepository.observeAll(ref.mealId, ref.date),
                    ) { meal, entries ->
                        if (meal == null || entries.isEmpty()) {
                            null
                        } else {
                            RecentMeal(
                                mealId = ref.mealId,
                                date = ref.date,
                                mealName = meal.name,
                                icon = meal.icon,
                                itemNames = entries.map { it.name },
                                nutritionFacts = entries.map { it.nutritionFacts }.sum(),
                                entryCount = entries.size,
                            )
                        }
                    }
                }
                .combine()
                .map { list ->
                    list.filterNotNull()
                        .sortedWith(
                            compareByDescending<RecentMeal> { it.mealId == currentMealId }
                                .thenByDescending { it.date }
                        )
                        .take(limit)
                }
        }
}
