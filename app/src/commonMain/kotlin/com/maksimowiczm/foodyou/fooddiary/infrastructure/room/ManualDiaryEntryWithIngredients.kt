package com.maksimowiczm.foodyou.fooddiary.infrastructure.room

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A manual entry together with whatever it is made of.
 *
 * Every read of a manual entry goes through this rather than the bare entity: an entry with an empty
 * ingredient list is an ordinary quick-add, and one with ingredients is a composed food. Loading
 * them together avoids a second query per row while the diary is being drawn.
 */
data class ManualDiaryEntryWithIngredients(
    @Embedded val entry: ManualDiaryEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val ingredients: List<ManualDiaryEntryIngredientEntity> = emptyList(),
)
