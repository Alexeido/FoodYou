package com.maksimowiczm.foodyou.fooddiary.infrastructure.repository

import androidx.room.RoomDatabase
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.infrastructure.room.immediateTransaction
import com.maksimowiczm.foodyou.common.infrastructure.room.toEntityNutrients
import com.maksimowiczm.foodyou.common.infrastructure.room.toNutritionFacts
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualEntryIngredient
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.ManualDiaryEntryDao
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.ManualDiaryEntryEntity
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.ManualDiaryEntryIngredientEntity
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.ManualDiaryEntryWithIngredients
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
            list.map(ManualDiaryEntryWithIngredients::toModel)
        }

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ManualDiaryEntry>> =
        dao.observeBetween(from.toEpochDays(), to.toEpochDays()).map { list ->
            list.map(ManualDiaryEntryWithIngredients::toModel)
        }

    override suspend fun insert(
        name: String,
        mealId: Long,
        date: LocalDate,
        nutritionFacts: NutritionFacts,
        createdAt: LocalDateTime,
        category: String?,
        isEaten: Boolean,
        createdByAssistant: Boolean,
        ingredients: List<ManualEntryIngredient>,
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
            category = category,
            isEaten = isEaten,
            createdByAssistant = createdByAssistant,
        )
        // La entrada y sus ingredientes van en la misma transaccion: media insercion dejaria una
        // comida compuesta sin nada dentro, que en el diario se lee como un anadido rapido normal.
        val id = database.immediateTransaction {
            val entryId = dao.insert(entity)
            if (ingredients.isNotEmpty()) {
                dao.insertIngredients(
                    ingredients.mapIndexed { index, ingredient ->
                        ManualDiaryEntryIngredientEntity(
                            entryId = entryId,
                            name = ingredient.name,
                            grams = ingredient.grams,
                            position = index,
                        )
                    }
                )
            }
            entryId
        }
        return ManualDiaryEntryId(id)
    }

    // Los ingredientes se reescriben enteros en vez de intentar casarlos uno a uno: la lista es
    // corta y descriptiva, y asi el modelo que entra es exactamente el que queda guardado.
    override suspend fun update(entry: ManualDiaryEntry) =
        database.immediateTransaction {
            dao.update(entry.toEntity())
            dao.deleteIngredients(entry.id.value)
            if (entry.ingredients.isNotEmpty()) {
                dao.insertIngredients(
                    entry.ingredients.mapIndexed { index, ingredient ->
                        ManualDiaryEntryIngredientEntity(
                            entryId = entry.id.value,
                            name = ingredient.name,
                            grams = ingredient.grams,
                            position = index,
                        )
                    }
                )
            }
        }

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

private fun ManualDiaryEntryWithIngredients.toModel(): ManualDiaryEntry =
    ManualDiaryEntry(
        id = ManualDiaryEntryId(entry.id),
        mealId = entry.mealId,
        date = LocalDate.fromEpochDays(entry.dateEpochDay.toInt()),
        name = entry.name,
        rawNutritionFacts =
            toNutritionFacts(
                nutrients = entry.nutrients,
                vitamins = entry.vitamins,
                minerals = entry.minerals,
            ),
        createdAt =
            Instant.fromEpochSeconds(entry.createdEpochSeconds)
                .toLocalDateTime(TimeZone.currentSystemDefault()),
        updatedAt =
            Instant.fromEpochSeconds(entry.updatedEpochSeconds)
                .toLocalDateTime(TimeZone.currentSystemDefault()),
        position = entry.position,
        category = entry.category,
        isEaten = entry.isEaten,
        createdByAssistant = entry.createdByAssistant,
        ingredients =
            ingredients
                .sortedBy { it.position }
                .map { ManualEntryIngredient(name = it.name, grams = it.grams) },
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
        category = category,
        isEaten = isEaten,
        createdByAssistant = createdByAssistant,
    )
}
