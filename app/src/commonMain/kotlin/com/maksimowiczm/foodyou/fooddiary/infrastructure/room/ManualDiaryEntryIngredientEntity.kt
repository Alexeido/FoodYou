package com.maksimowiczm.foodyou.fooddiary.infrastructure.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One component of a composed diary entry - the beef patty inside "hamburguesa".
 *
 * Deliberately descriptive only: the parent [ManualDiaryEntryEntity] already carries the macros for
 * the whole thing, and these rows exist so the diary can answer "what was in it?" without turning a
 * single burger into six separate entries. They are not measured against the product catalogue and
 * they never contribute to any total - if they did, everything would be counted twice.
 */
@Entity(
    tableName = "ManualDiaryEntryIngredient",
    foreignKeys =
        [
            ForeignKey(
                entity = ManualDiaryEntryEntity::class,
                parentColumns = ["id"],
                childColumns = ["entryId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["entryId"])],
)
data class ManualDiaryEntryIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val name: String,

    /** Null when the amount is not known - the row then shows just the name. */
    val grams: Double? = null,

    /** Keeps the order the ingredients were given in; lower comes first. */
    val position: Int = 0,
)
