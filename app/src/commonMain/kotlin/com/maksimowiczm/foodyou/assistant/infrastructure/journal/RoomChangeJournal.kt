package com.maksimowiczm.foodyou.assistant.infrastructure.journal

import com.maksimowiczm.foodyou.assistant.domain.journal.AssistantChange
import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.domain.journal.EatenState
import com.maksimowiczm.foodyou.assistant.domain.journal.MeasurementSnapshot
import com.maksimowiczm.foodyou.assistant.domain.journal.UndoAction
import com.maksimowiczm.foodyou.assistant.infrastructure.room.AssistantChangeEntity
import com.maksimowiczm.foodyou.assistant.infrastructure.room.AssistantDao
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.MeasurementDao
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.MeasurementEntity
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

class RoomChangeJournal(
    private val dao: AssistantDao,
    private val measurementDao: MeasurementDao,
    private val json: Json,
) : ChangeJournal {

    override suspend fun record(summary: String, undo: UndoAction): Long =
        dao.insertChange(
            AssistantChangeEntity(
                createdAt = Clock.System.now().epochSeconds,
                summary = summary,
                undoPayload = json.encodeToString(UndoAction.serializer(), undo),
            )
        )

    override suspend fun snapshot(entryIds: List<Long>): UndoAction =
        UndoAction.RestoreMeasurements(
            entryIds.mapNotNull { id -> measurementDao.observeMeasurementById(id).first()?.toSnapshot() }
        )

    override suspend fun recent(limit: Int): List<AssistantChange> =
        dao.recentChanges(limit).map { it.toDomain() }

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
    )

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
    )
