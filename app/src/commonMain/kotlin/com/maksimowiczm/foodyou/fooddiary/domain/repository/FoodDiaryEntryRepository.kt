package com.maksimowiczm.foodyou.fooddiary.domain.repository

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.RecentMealRef
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

interface FoodDiaryEntryRepository {
    fun observe(id: FoodDiaryEntryId): Flow<FoodDiaryEntry?>

    fun observeAll(mealId: Long, date: LocalDate): Flow<List<FoodDiaryEntry>>

    /**
     * Every entry between two dates inclusive, across all meals.
     *
     * Ordered by date, then meal, then position, so callers that aggregate get a stable sequence.
     */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<FoodDiaryEntry>>

    /** Recently logged meals as (mealId, date) references, most recently touched first. */
    fun observeRecentMealRefs(limit: Int): Flow<List<RecentMealRef>>

    suspend fun insert(
        measurement: Measurement,
        mealId: Long,
        date: LocalDate,
        food: DiaryFood,
        createdAt: LocalDateTime,
    ): FoodDiaryEntryId

    suspend fun update(entry: FoodDiaryEntry)

    suspend fun delete(id: FoodDiaryEntryId)

    suspend fun setEaten(id: FoodDiaryEntryId, isEaten: Boolean)

    suspend fun updatePositions(updates: List<Pair<FoodDiaryEntryId, Int>>)

    suspend fun moveToMeal(id: FoodDiaryEntryId, targetMealId: Long, date: LocalDate)
}
