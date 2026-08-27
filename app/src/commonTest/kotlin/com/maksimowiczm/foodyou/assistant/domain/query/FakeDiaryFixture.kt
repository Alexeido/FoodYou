package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.RecentMealRef
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * A hand-seeded diary the query use cases run against.
 *
 * Deliberately a fake repository rather than an in-memory Room database: what these tests are
 * checking is the aggregation, and the aggregation is pure. The SQL range query underneath is two
 * lines of `BETWEEN` and belongs in an instrumented test, where a real database can prove it.
 */
internal class FakeDiaryRepository(private val entries: List<FoodDiaryEntry>) :
    FoodDiaryEntryRepository {

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<FoodDiaryEntry>> =
        flowOf(entries.filter { it.date >= from && it.date <= to })

    override fun observe(id: FoodDiaryEntryId): Flow<FoodDiaryEntry?> =
        flowOf(entries.firstOrNull { it.id == id })

    override fun observeAll(mealId: Long, date: LocalDate): Flow<List<FoodDiaryEntry>> =
        flowOf(entries.filter { it.mealId == mealId && it.date == date })

    override fun observeRecentMealRefs(limit: Int): Flow<List<RecentMealRef>> = flowOf(emptyList())

    override suspend fun insert(
        measurement: Measurement,
        mealId: Long,
        date: LocalDate,
        food: DiaryFood,
        createdAt: LocalDateTime,
    ): FoodDiaryEntryId = error("not needed by these tests")

    override suspend fun update(entry: FoodDiaryEntry) = error("not needed by these tests")

    override suspend fun delete(id: FoodDiaryEntryId) = error("not needed by these tests")

    override suspend fun setEaten(id: FoodDiaryEntryId, isEaten: Boolean) =
        error("not needed by these tests")

    override suspend fun updatePositions(updates: List<Pair<FoodDiaryEntryId, Int>>) =
        error("not needed by these tests")

    override suspend fun moveToMeal(id: FoodDiaryEntryId, targetMealId: Long, date: LocalDate) =
        error("not needed by these tests")
}

/** Per-100 g facts, only the fields these tests assert on. */
internal fun facts(
    energy: Double,
    proteins: Double = 0.0,
    carbohydrates: Double = 0.0,
    fats: Double = 0.0,
    saturatedFats: Double = 0.0,
    dietaryFiber: Double = 0.0,
) = NutritionFacts(
    energy = NutrientValue.Complete(energy),
    proteins = NutrientValue.Complete(proteins),
    carbohydrates = NutrientValue.Complete(carbohydrates),
    fats = NutrientValue.Complete(fats),
    saturatedFats = NutrientValue.Complete(saturatedFats),
    dietaryFiber = NutrientValue.Complete(dietaryFiber),
)

internal fun product(name: String, per100: NutritionFacts) =
    DiaryFoodProduct(
        name = name,
        nutritionFacts = per100,
        servingWeight = null,
        totalWeight = null,
        isLiquid = false,
        source = FoodSource(FoodSource.Type.User),
        note = null,
    )

private var nextId = 1L

/**
 * One entry of [grams] of [name]. Measured in grams so the weight is the number given and the
 * expected totals stay readable in the assertions.
 */
internal fun entry(
    date: LocalDate,
    name: String,
    per100: NutritionFacts,
    grams: Double,
    mealId: Long = 1L,
    isEaten: Boolean = true,
    createdAt: LocalDateTime = LocalDateTime(date.year, 1, 1, 12, 0),
) = FoodDiaryEntry(
    id = FoodDiaryEntryId(nextId++),
    mealId = mealId,
    date = date,
    measurement = Measurement.Gram(grams),
    food = product(name, per100),
    isEaten = isEaten,
    createdAt = createdAt,
    updatedAt = createdAt,
    position = 0,
)

internal fun day(iso: String): LocalDate = LocalDate.parse(iso)
