package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/** The raw entries between two dates, for when the model needs the detail and not a total. */
class DiaryRangeUseCase(private val repository: FoodDiaryEntryRepository) {

    suspend operator fun invoke(
        from: LocalDate,
        to: LocalDate,
        mealId: Long? = null,
        onlyEaten: Boolean? = null,
    ): List<FoodDiaryEntry> =
        repository
            .observeRange(from, to)
            .first()
            .filter { mealId == null || it.mealId == mealId }
            .filter { onlyEaten == null || it.isEaten == onlyEaten }
            .sortedWith(compareBy({ it.date }, { it.mealId }, { it.position }))
}
