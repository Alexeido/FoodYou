package com.maksimowiczm.foodyou.assistant.infrastructure.journal

import com.maksimowiczm.foodyou.assistant.domain.ConversationStore
import com.maksimowiczm.foodyou.assistant.domain.journal.AssistantChange
import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.domain.journal.EatenState
import com.maksimowiczm.foodyou.assistant.domain.journal.ManualEntrySnapshot
import com.maksimowiczm.foodyou.assistant.domain.journal.MeasurementSnapshot
import com.maksimowiczm.foodyou.assistant.domain.journal.RecipeIngredientSnapshot
import com.maksimowiczm.foodyou.assistant.domain.journal.RecipeSnapshot
import com.maksimowiczm.foodyou.assistant.domain.journal.UndoAction
import com.maksimowiczm.foodyou.assistant.infrastructure.room.AssistantChangeEntity
import com.maksimowiczm.foodyou.assistant.infrastructure.room.AssistantDao
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeDao
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeIngredientEntity
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.MeasurementDao
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.MeasurementEntity
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

class RoomChangeJournal(
    private val dao: AssistantDao,
    private val measurementDao: MeasurementDao,
    private val recipeDao: RecipeDao,
    private val manualRepository: ManualDiaryEntryRepository,
    private val conversationStore: ConversationStore,
    private val json: Json,
) : ChangeJournal {

    override suspend fun record(summary: String, undo: UndoAction): Long =
        dao.insertChange(
            AssistantChangeEntity(
                createdAt = Clock.System.now().epochSeconds,
                summary = summary,
                undoPayload = json.encodeToString(UndoAction.serializer(), undo),
                // Reads the store directly rather than taking a parameter: threading a
                // conversation id through every mutating tool's call() just to reach this one
                // insert would touch a dozen files for something the tool itself never needs.
                conversationId = conversationStore.conversationId.value,
            )
        )

    override suspend fun snapshot(entryIds: List<Long>): UndoAction =
        UndoAction.RestoreMeasurements(
            entryIds.mapNotNull { id -> measurementDao.observeMeasurementById(id).first()?.toSnapshot() }
        )

    override suspend fun recent(limit: Int): List<AssistantChange> =
        dao.recentChanges(limit).map { it.toDomain() }

    override suspend fun recentForConversation(
        conversationId: Long,
        limit: Int,
    ): List<AssistantChange> =
        dao.recentChangesForConversation(conversationId, limit).map { it.toDomain() }

    override suspend fun undo(changeId: Long?): AssistantChange? {
        val entity =
            if (changeId == null) dao.lastAppliedChange() else dao.changeById(changeId)
        if (entity == null || entity.undone) return null

        val action = json.decodeFromString(UndoAction.serializer(), entity.undoPayload)

        // Capture the inverse before applying, so redo has a way back without re-deriving it.
        val redo = inverseOf(action)
        apply(action)

        dao.setUndone(
            id = entity.id,
            undone = true,
            redoPayload = json.encodeToString(UndoAction.serializer(), redo),
        )
        return entity.copy(undone = true).toDomain()
    }

    override suspend fun redo(): AssistantChange? {
        val entity = dao.lastUndoneChange() ?: return null
        val payload = entity.redoPayload ?: return null

        apply(json.decodeFromString(UndoAction.serializer(), payload))
        dao.setUndone(id = entity.id, undone = false, redoPayload = null)
        return entity.copy(undone = false).toDomain()
    }

    /**
     * What would put things back the way they are right now, if [action] were applied.
     *
     * Computed before the action runs, while the rows it is about to touch still exist.
     */
    private suspend fun inverseOf(action: UndoAction): UndoAction =
        when (action) {
            is UndoAction.DeleteMeasurements ->
                UndoAction.RestoreMeasurements(
                    action.ids.mapNotNull { id ->
                        measurementDao.observeMeasurementById(id).first()?.toSnapshot()
                    }
                )

            is UndoAction.RestoreMeasurements ->
                UndoAction.DeleteMeasurements(action.rows.map { it.id })

            is UndoAction.SetEaten ->
                UndoAction.SetEaten(
                    action.states.mapNotNull { state ->
                        measurementDao
                            .observeMeasurementById(state.id)
                            .first()
                            ?.let { EatenState(it.id, it.isEaten) }
                    }
                )

            is UndoAction.Batch ->
                // Reversed: the inverse of "do A then B" is "undo B then undo A".
                UndoAction.Batch(action.actions.reversed().map { inverseOf(it) })

            is UndoAction.DeleteManualEntries ->
                UndoAction.RestoreManualEntries(
                    action.ids.mapNotNull { id ->
                        manualRepository.observe(ManualDiaryEntryId(id)).first()?.toSnapshot()
                    }
                )

            is UndoAction.RestoreManualEntries ->
                UndoAction.DeleteManualEntries(action.rows.map { it.id })

            is UndoAction.DeleteRecipes ->
                UndoAction.RestoreRecipes(action.ids.mapNotNull { recipeSnapshot(it) })

            is UndoAction.RestoreRecipes -> UndoAction.DeleteRecipes(action.rows.map { it.id })
        }

    private suspend fun recipeSnapshot(id: Long): RecipeSnapshot? {
        val recipe = recipeDao.getRecipe(id) ?: return null
        return RecipeSnapshot(
            id = recipe.id,
            name = recipe.name,
            servings = recipe.servings,
            note = recipe.note,
            isLiquid = recipe.isLiquid,
            isFavorite = recipe.isFavorite,
            category = recipe.category,
            ingredients =
                recipeDao.getRecipeIngredients(id).map {
                    RecipeIngredientSnapshot(
                        productId = it.ingredientProductId,
                        recipeId = it.ingredientRecipeId,
                        measurement = it.measurement,
                        quantity = it.quantity,
                    )
                },
        )
    }

    private suspend fun apply(action: UndoAction) {
        when (action) {
            is UndoAction.DeleteMeasurements ->
                action.ids.forEach { measurementDao.deleteMeasurement(it) }

            is UndoAction.RestoreMeasurements ->
                measurementDao.insertMeasurements(action.rows.map { it.toEntity() })

            is UndoAction.SetEaten ->
                action.states.forEach { measurementDao.setEaten(it.id, it.isEaten) }

            is UndoAction.Batch -> action.actions.forEach { apply(it) }

            is UndoAction.DeleteManualEntries ->
                action.ids.forEach { manualRepository.delete(ManualDiaryEntryId(it)) }

            is UndoAction.RestoreManualEntries ->
                action.rows.forEach { row ->
                    val zone = TimeZone.currentSystemDefault()
                    val id =
                        manualRepository.insert(
                            name = row.name,
                            mealId = row.mealId,
                            date = LocalDate.fromEpochDays(row.epochDay.toInt()),
                            nutritionFacts =
                                NutritionFacts(
                                    energy = NutrientValue.Complete(row.kcal),
                                    proteins = NutrientValue.Complete(row.proteins),
                                    carbohydrates = NutrientValue.Complete(row.carbohydrates),
                                    fats = NutrientValue.Complete(row.fats),
                                ),
                            createdAt = Instant.fromEpochSeconds(row.createdAt).toLocalDateTime(zone),
                        )
                    // insert() always creates isEaten = true (the entity's own default); force it
                    // back to whatever the snapshot actually had.
                    if (!row.isEaten) {
                        manualRepository.observe(id).first()?.let {
                            manualRepository.update(it.copy(isEaten = false))
                        }
                    }
                }

            is UndoAction.DeleteRecipes -> action.ids.forEach { recipeDao.deleteById(it) }

            is UndoAction.RestoreRecipes ->
                action.rows.forEach { row ->
                    // Explicit id: @Insert keeps it, so the recipe comes back as the same one.
                    recipeDao.insertRecipeWithIngredients(
                        recipe =
                            RecipeEntity(
                                id = row.id,
                                name = row.name,
                                servings = row.servings,
                                note = row.note,
                                isLiquid = row.isLiquid,
                                isFavorite = row.isFavorite,
                                category = row.category,
                            ),
                        ingredients =
                            row.ingredients.map {
                                RecipeIngredientEntity(
                                    ingredientProductId = it.productId,
                                    ingredientRecipeId = it.recipeId,
                                    measurement = it.measurement,
                                    quantity = it.quantity,
                                )
                            },
                    )
                }
        }
    }
}

private fun AssistantChangeEntity.toDomain() =
    AssistantChange(
        id = id,
        createdAt =
            Instant.fromEpochSeconds(createdAt).toLocalDateTime(TimeZone.currentSystemDefault()),
        summary = summary,
        undone = undone,
        conversationId = conversationId,
    )

internal fun MeasurementEntity.toSnapshot() =
    MeasurementSnapshot(
        id = id,
        mealId = mealId,
        epochDay = epochDay,
        productId = productId,
        recipeId = recipeId,
        measurement = measurement,
        quantity = quantity,
        isEaten = isEaten,
        createdAt = createdAt,
        updatedAt = updatedAt,
        position = position,
        createdByAssistant = createdByAssistant,
    )

internal fun ManualDiaryEntry.toSnapshot(): ManualEntrySnapshot {
    val zone = TimeZone.currentSystemDefault()
    return ManualEntrySnapshot(
        id = id.value,
        mealId = mealId,
        epochDay = date.toEpochDays().toLong(),
        name = name,
        kcal = nutritionFacts.energy.value ?: 0.0,
        proteins = nutritionFacts.proteins.value ?: 0.0,
        carbohydrates = nutritionFacts.carbohydrates.value ?: 0.0,
        fats = nutritionFacts.fats.value ?: 0.0,
        isEaten = isEaten,
        createdAt = createdAt.toInstant(zone).epochSeconds,
        updatedAt = updatedAt.toInstant(zone).epochSeconds,
    )
}

internal fun MeasurementSnapshot.toEntity() =
    MeasurementEntity(
        id = id,
        mealId = mealId,
        epochDay = epochDay,
        productId = productId,
        recipeId = recipeId,
        measurement = measurement,
        quantity = quantity,
        isEaten = isEaten,
        createdAt = createdAt,
        updatedAt = updatedAt,
        position = position,
        createdByAssistant = createdByAssistant,
    )
