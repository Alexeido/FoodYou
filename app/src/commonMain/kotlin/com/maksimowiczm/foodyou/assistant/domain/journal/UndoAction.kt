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

    /** Undo of a manual entry insert: remove the rows that were created. */
    @Serializable
    @SerialName("deleteManualEntries")
    data class DeleteManualEntries(val ids: List<Long>) : UndoAction

    /** Undo of removing manual entries: recreate them. IDs are not preserved on restore. */
    @Serializable
    @SerialName("restoreManualEntries")
    data class RestoreManualEntries(val rows: List<ManualEntrySnapshot>) : UndoAction

    /**
     * Undo of creating recipes: remove them from the catalogue.
     *
     * Safe for the diary: an entry that logged the recipe keeps its own snapshot of the dish, so
     * the recipe going away does not change a past day.
     */
    @Serializable
    @SerialName("deleteRecipes")
    data class DeleteRecipes(val ids: List<Long>) : UndoAction

    /**
     * Undo of deleting recipes: write them back, with their original ids.
     *
     * Unlike manual entries the id matters here - the model may still be holding the recipeId from
     * the turn it created it, and a redo that brought the recipe back under a new id would leave
     * that reference pointing at nothing.
     */
    @Serializable
    @SerialName("restoreRecipes")
    data class RestoreRecipes(val rows: List<RecipeSnapshot>) : UndoAction
}

/** A Recipe row with its ingredients, flat enough to serialize and to put back as it was. */
@Serializable
data class RecipeSnapshot(
    val id: Long,
    val name: String,
    val servings: Int,
    val note: String?,
    val isLiquid: Boolean,
    val ingredients: List<RecipeIngredientSnapshot>,
    /** Defaulted so a journal written before this field existed still reads back. */
    val isFavorite: Boolean = false,
    val category: String? = null,
)

/** One ingredient: exactly one of [productId] or [recipeId] is set, as in the table itself. */
@Serializable
data class RecipeIngredientSnapshot(
    val productId: Long?,
    val recipeId: Long?,
    val measurement: MeasurementType,
    val quantity: Double,
)

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
    // Con valor por defecto para que los cambios ya guardados sigan leyendose.
    val createdByAssistant: Boolean = false,
)

@Serializable data class EatenState(val id: Long, val isEaten: Boolean)

/**
 * A ManualDiaryEntry row, flat enough to serialize. Nutrition facts are flattened to plain doubles
 * since only the four macros a manual entry ever carries need to round-trip.
 */
@Serializable
data class ManualEntrySnapshot(
    val id: Long,
    val mealId: Long,
    val epochDay: Long,
    val name: String,
    val kcal: Double,
    val proteins: Double,
    val carbohydrates: Double,
    val fats: Double,
    val isEaten: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
