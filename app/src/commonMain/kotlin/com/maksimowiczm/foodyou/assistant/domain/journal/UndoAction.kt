package com.maksimowiczm.foodyou.assistant.domain.journal

import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How to put the diary back the way it was.
 *
 * The journal stores the *previous state*, not the operation that was performed. Knowing that entry
 * 412 was deleted does not let anything restore it; the whole row has to have been kept. That is
 * the requirement the entire "act now, undo later" model rests on, and it is the reason these are
 * snapshots rather than commands.
 *
 * Restoring a deleted entry only needs the Measurement row back: deleting an entry leaves its
 * embedded DiaryProduct behind, so the food it points at is still there.
 */
@Serializable
sealed interface UndoAction {

    /** Undo of an insert: remove the rows that were created. */
    @Serializable
    @SerialName("deleteMeasurements")
    data class DeleteMeasurements(val ids: List<Long>) : UndoAction

    /** Undo of a delete or of an edit: write these rows back exactly as they were. */
    @Serializable
    @SerialName("restoreMeasurements")
    data class RestoreMeasurements(val rows: List<MeasurementSnapshot>) : UndoAction

    /** Undo of marking things eaten. */
    @Serializable
    @SerialName("setEaten")
    data class SetEaten(val states: List<EatenState>) : UndoAction

    /** Several undos applied in order. A whole plan is one entry in the history, not thirty. */
    @Serializable
    @SerialName("batch")
    data class Batch(val actions: List<UndoAction>) : UndoAction
}

/** A Measurement row, flat enough to serialize without touching the food snapshot it points at. */
@Serializable
data class MeasurementSnapshot(
    val id: Long,
    val mealId: Long,
    val epochDay: Long,
    val productId: Long?,
    val recipeId: Long?,
    val measurement: MeasurementType,
    val quantity: Double,
    val isEaten: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val position: Int,
)

@Serializable data class EatenState(val id: Long, val isEaten: Boolean)
