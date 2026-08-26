package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceType
import com.maksimowiczm.foodyou.common.infrastructure.room.Minerals
import com.maksimowiczm.foodyou.common.infrastructure.room.Nutrients
import com.maksimowiczm.foodyou.common.infrastructure.room.Vitamins

@Entity(tableName = "Product")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    val barcode: String?,
    /**
     * Immutable EAN as it comes from the remote source. Used as the identity anchor to
     * recognize the same product on re-search and to look it up on manual refresh, even when the
     * user overrides the visible [barcode] with a local one. `null` for user-created products.
     */
    val sourceBarcode: String? = null,
    @Embedded val nutrients: Nutrients,
    @Embedded val vitamins: Vitamins,
    @Embedded val minerals: Minerals,
    val packageWeight: Double?,
    val servingWeight: Double?,
    val note: String?,
    val sourceType: FoodSourceType,
    val sourceUrl: String? = null,
    val isLiquid: Boolean,
    val categories: String? = null,
    val isFavorite: Boolean = false,
    /**
     * True when the user hand-edited this product. Edited products are durable: excluded from the
     * stale-mirror purge and never overwritten by a passive re-search insert.
     */
    val isEdited: Boolean = false,
)
