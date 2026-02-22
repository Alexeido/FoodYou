package com.maksimowiczm.foodyou.fooddiary.infrastructure.repository

import androidx.room.RoomDatabase
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.infrastructure.room.immediateTransaction
import com.maksimowiczm.foodyou.common.infrastructure.room.toEntityNutrients
import com.maksimowiczm.foodyou.common.infrastructure.room.toNutritionFacts
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.ManualDiaryEntryDao
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.ManualDiaryEntryEntity
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal class RoomManualDiaryEntryRepository(
    private val database: RoomDatabase,
    private val dao: ManualDiaryEntryDao,
) : ManualDiaryEntryRepository {
    override fun observe(id: ManualDiaryEntryId): Flow<ManualDiaryEntry?> =
        dao.observe(id.value).map { it?.toModel() }

    override fun observeAll(mealId: Long, date: LocalDate): Flow<List<ManualDiaryEntry>> =
        dao.observeAll(mealId, date.toEpochDays()).map { list ->
            list.map(ManualDiaryEntryEntity::toModel)
        }

    override suspend fun insert(
        name: String,
        mealId: Long,
        date: LocalDate,
        nutritionFacts: NutritionFacts,
        createdAt: LocalDateTime,
    ): ManualDiaryEntryId {
        val epochDay = date.toEpochDays()
        val position = dao.getMaxPosition(mealId, epochDay) + 1
        val createdAtSeconds = createdAt.toInstant(TimeZone.currentSystemDefault()).epochSeconds
        val (nutrients, vitamins, minerals) = toEntityNutrients(nutritionFacts)
        val entity = ManualDiaryEntryEntity(
            mealId = mealId,
            dateEpochDay = epochDay,
            name = name,
            nutrients = nutrients,
            vitamins = vitamins,
            minerals = minerals,
            createdEpochSeconds = createdAtSeconds,
            updatedEpochSeconds = createdAtSeconds,
            position = position,
        )
        val id = dao.insert(entity)
        return ManualDiaryEntryId(id)
    }

    override suspend fun update(entry: ManualDiaryEntry) = dao.update(entry.toEntity())

    override suspend fun delete(id: ManualDiaryEntryId) = dao.delete(id.value)

    override suspend fun updatePositions(updates: List<Pair<ManualDiaryEntryId, Int>>) =
        database.immediateTransaction {
            updates.forEach { (id, position) -> dao.updatePosition(id.value, position) }
        }

    override suspend fun moveToMeal(id: ManualDiaryEntryId, targetMealId: Long, date: LocalDate) =
        database.immediateTransaction {
            val epochDay = date.toEpochDays()
            val newPosition = dao.getMaxPosition(targetMealId, epochDay) + 1
            val now = Clock.System.now().epochSeconds
            dao.updatePositionAndMeal(id.value, targetMealId, newPosition, now)
        }
}

private fun ManualDiaryEntryEntity.toModel(): ManualDiaryEntry =
    ManualDiaryEntry(
        id = ManualDiaryEntryId(id),
        mealId = mealId,
        date = LocalDate.fromEpochDays(dateEpochDay.toInt()),
        name = name,
        rawNutritionFacts =
            toNutritionFacts(nutrients = nutrients, vitamins = vitamins, minerals = minerals),
        createdAt =
            Instant.fromEpochSeconds(createdEpochSeconds)
                .toLocalDateTime(TimeZone.currentSystemDefault()),
        updatedAt =
            Instant.fromEpochSeconds(updatedEpochSeconds)
                .toLocalDateTime(TimeZone.currentSystemDefault()),
        position = position,
    )

private fun ManualDiaryEntry.toEntity(): ManualDiaryEntryEntity {
    val (nutrients, vitamins, minerals) = toEntityNutrients(nutritionFacts)

    return ManualDiaryEntryEntity(
        id = id.value,
        mealId = mealId,
        dateEpochDay = date.toEpochDays(),
        name = name,
        nutrients = nutrients,
        vitamins = vitamins,
        minerals = minerals,
        createdEpochSeconds = createdAt.toInstant(TimeZone.currentSystemDefault()).epochSeconds,
        updatedEpochSeconds = updatedAt.toInstant(TimeZone.currentSystemDefault()).epochSeconds,
        position = position,
    )
}
