package com.maksimowiczm.foodyou.app.ui.home.calendar

import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * Computes total calories consumed on a given date.
 *
 * Unlike [com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase], this use
 * case does NOT subscribe to a per-second time observable, so it only emits when diary data
 * actually changes — making it safe to run simultaneously for many dates.
 */
internal class CalendarDayStatusUseCase(
    private val mealRepository: MealRepository,
    private val foodEntryRepository: FoodDiaryEntryRepository,
    private val manualEntryRepository: ManualDiaryEntryRepository,
) {
    fun observeTotalKcal(date: LocalDate): Flow<Double> =
        mealRepository.observeMeals().flatMapLatest { meals ->
            val entryFlows =
                meals.map { meal ->
                    combine(
                        manualEntryRepository.observeAll(mealId = meal.id, date = date),
                        foodEntryRepository.observeAll(mealId = meal.id, date = date),
                    ) { manual, food ->
                        (manual + food)
                            .filter { it.isEaten }
                            .sumOf { it.nutritionFacts.energy.value ?: 0.0 }
                    }
                }
            if (entryFlows.isEmpty()) flowOf(0.0)
            else combine(entryFlows) { kcals -> kcals.sum() }
        }
}
