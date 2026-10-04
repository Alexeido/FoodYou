package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Recipe")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val servings: Int,
    val note: String?,
    val isLiquid: Boolean,
    /**
     * Pinned by the person, like a product. The default is declared on the column as well as here
     * so the schema Room expects matches the one the migration creates, byte for byte.
     */
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    /** FoodCategory name; null for recipes made before it existed or without one. */
    val category: String? = null,
)
