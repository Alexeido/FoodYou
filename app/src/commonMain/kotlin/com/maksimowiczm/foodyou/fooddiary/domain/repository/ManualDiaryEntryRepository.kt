package com.maksimowiczm.foodyou.fooddiary.domain.repository

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualEntryIngredient
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

interface ManualDiaryEntryRepository {
    fun observe(id: ManualDiaryEntryId): Flow<ManualDiaryEntry?>

    fun observeAll(mealId: Long, date: LocalDate): Flow<List<ManualDiaryEntry>>

    /** Every manual entry between two dates inclusive, across all meals. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ManualDiaryEntry>>

    suspend fun insert(
        name: String,
        mealId: Long,
        date: LocalDate,
        nutritionFacts: NutritionFacts,
        createdAt: LocalDateTime,
        category: String? = null,
        isEaten: Boolean = true,
        createdByAssistant: Boolean = false,
        /** Non-empty makes this a composed food; the macros above still cover the whole thing. */
        ingredients: List<ManualEntryIngredient> = emptyList(),
    ): ManualDiaryEntryId

    suspend fun update(entry: ManualDiaryEntry)

    suspend fun delete(id: ManualDiaryEntryId)

    suspend fun updatePositions(updates: List<Pair<ManualDiaryEntryId, Int>>)

    suspend fun moveToMeal(id: ManualDiaryEntryId, targetMealId: Long, date: LocalDate)
}
