package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.runtime.*
import com.maksimowiczm.foodyou.app.ui.food.search.FoodCategory
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualEntryIngredient
import kotlinx.datetime.LocalTime

@Immutable
internal data class MealModel(
    val id: Long,
    val name: String,
    val from: LocalTime,
    val to: LocalTime,
    val isAllDay: Boolean,
    val foods: List<MealEntryModel>,
    val energy: Int,
    val proteins: Double,
    val carbohydrates: Double,
    val fats: Double,
)

@Immutable
internal sealed interface MealEntryModel {
    val mealId: Long
    val name: String
    val energy: Int?
    val proteins: Double?
    val carbohydrates: Double?
    val fats: Double?
    val isEaten: Boolean
    val category: FoodCategory
}

@Immutable
internal data class FoodMealEntryModel(
    val id: FoodDiaryEntryId,
    override val mealId: Long,
    override val name: String,
    override val energy: Int?,
    override val proteins: Double?,
    override val carbohydrates: Double?,
    override val fats: Double?,
    override val isEaten: Boolean,
    override val category: FoodCategory,
    val measurement: Measurement,
    val weight: Double?,
    val isLiquid: Boolean,
    val isRecipe: Boolean,
    val servingWeight: Double?,
    val totalWeight: Double?,
    /** Logged by the AI assistant rather than by hand. */
    val createdByAssistant: Boolean = false,
) : MealEntryModel

@Immutable
internal data class ManualMealEntryModel(
    val id: ManualDiaryEntryId,
    override val mealId: Long,
    override val name: String,
    override val energy: Int?,
    override val proteins: Double?,
    override val carbohydrates: Double?,
    override val fats: Double?,
    override val isEaten: Boolean = true,
    override val category: FoodCategory = FoodCategory.UNKNOWN,
    /** Marks the row as the assistant's work rather than the person's own quick add. */
    val createdByAssistant: Boolean = false,
    /**
     * What the dish was made of, when it is a composed food.
     *
     * Empty for a plain quick add. Non-empty earns the row its own icon and fills the breakdown
     * shown when it is tapped - one burger in the diary instead of six loose ingredients.
     */
    val ingredients: List<ManualEntryIngredient> = emptyList(),
) : MealEntryModel {
    val isComposed: Boolean
        get() = ingredients.isNotEmpty()
}
