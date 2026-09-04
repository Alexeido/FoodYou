package com.maksimowiczm.foodyou.fooddiary.infrastructure.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.maksimowiczm.foodyou.common.infrastructure.room.Minerals
import com.maksimowiczm.foodyou.common.infrastructure.room.Nutrients
import com.maksimowiczm.foodyou.common.infrastructure.room.Vitamins

@Entity(
    tableName = "ManualDiaryEntry",
    foreignKeys =
        [
            ForeignKey(
                entity = MealEntity::class,
                parentColumns = ["id"],
                childColumns = ["mealId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["mealId"]), Index(value = ["dateEpochDay"])],
)
data class ManualDiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val dateEpochDay: Long,
    val name: String,
    @Embedded val nutrients: Nutrients,
    @Embedded val vitamins: Vitamins,
    @Embedded val minerals: Minerals,
    val createdEpochSeconds: Long,
    val updatedEpochSeconds: Long,

    /** User-defined position for ordering within a meal+day. Lower = earlier. */
    val position: Int = 0,

    /** [com.maksimowiczm.foodyou.common.domain.food.FoodCategory] name, null when unknown. */
    val category: String? = null,

    /**
     * Whether this has actually been eaten, as opposed to still being a plan.
     *
     * It used to live only in the domain model with a default of `true`, so nothing the assistant
     * added could ever be left pending - it was marked eaten the instant it was written.
     */
    val isEaten: Boolean = true,

    /** Set only by the assistant's own tool, so the diary can mark what it added. */
    val createdByAssistant: Boolean = false,
)
