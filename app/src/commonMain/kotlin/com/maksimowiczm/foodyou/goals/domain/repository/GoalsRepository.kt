package com.maksimowiczm.foodyou.goals.domain.repository

import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField
import com.maksimowiczm.foodyou.goals.domain.entity.DailyGoal
import com.maksimowiczm.foodyou.goals.domain.entity.WeeklyGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface GoalsRepository {
    suspend fun updateWeeklyGoals(weeklyGoals: WeeklyGoals)

    fun observeWeeklyGoals(): Flow<WeeklyGoals>

    fun observeDailyGoals(date: LocalDate): Flow<DailyGoal>

    /**
     * Nutrients beyond energy and macros that the person wants to reach (calcium, fiber...), in
     * the order they picked them. They show on the home card's details and the assistants are
     * told about them; every other micronutrient goal stays a reference value.
     */
    fun observeTrackedNutrients(): Flow<List<NutritionFactsField>>

    suspend fun setTrackedNutrients(fields: List<NutritionFactsField>)
}
