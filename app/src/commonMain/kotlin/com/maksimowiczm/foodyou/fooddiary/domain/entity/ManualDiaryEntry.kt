package com.maksimowiczm.foodyou.fooddiary.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import kotlin.jvm.JvmInline
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@JvmInline value class ManualDiaryEntryId(val value: Long)

/**
 * One component of a composed entry, for showing what a dish was made of.
 *
 * Descriptive only - the parent entry carries the macros for the whole thing, so these never take
 * part in any sum.
 */
data class ManualEntryIngredient(val name: String, val grams: Double? = null)

/**
 * Represents a manually added diary entry. When a user adds a entry without referring to any food
 * item.
 *
 * @param id The unique identifier of the manual diary entry.
 * @param mealId The identifier of the meal to which this entry belongs.
 * @param date The date of the diary entry.
 * @param nutritionFacts The nutrition facts for the food item based on the weight.
 * @param createdAt The timestamp when the entry was created.
 * @param updatedAt The timestamp when the entry was last updated.
 */
data class ManualDiaryEntry(
    val id: ManualDiaryEntryId,
    override val mealId: Long,
    override val date: LocalDate,
    override val name: String,
    private val rawNutritionFacts: NutritionFacts,
    override val isEaten: Boolean = true,
    override val createdAt: LocalDateTime,
    override val updatedAt: LocalDateTime,
    override val position: Int = 0,
    /**
     * What kind of food this is, so the diary can draw an icon instead of a "?" placeholder.
     *
     * A plain category name rather than the UI enum, matching how [Product][
     * com.maksimowiczm.foodyou.food.domain.entity.Product] keeps its own categories: the mapping to
     * an icon belongs to the screen, not here.
     */
    val category: String? = null,
    /** Set only by the assistant, so the diary can show which entries it added. */
    val createdByAssistant: Boolean = false,
    /**
     * What this is made of, when it is a composed food - a burger rather than six loose rows.
     *
     * Empty for an ordinary quick add, which is what makes [isComposed] a derived fact rather than
     * a flag that could drift out of step with the rows it describes.
     */
    val ingredients: List<ManualEntryIngredient> = emptyList(),
) : DiaryEntry {
    override val nutritionFacts: NutritionFacts = rawNutritionFacts

    val isComposed: Boolean
        get() = ingredients.isNotEmpty()
}
