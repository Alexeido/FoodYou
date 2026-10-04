package com.maksimowiczm.foodyou.assistant.domain.pad

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import kotlinx.datetime.LocalDate

/**
 * The assistant's scratchpad: where it copies a day, tries things, adds up and throws away, without
 * any of it reaching the diary.
 *
 * Two problems this solves that are easy to miss. It keeps a half-finished plan out of today's
 * totals - but more importantly, it takes the arithmetic away from the model. A week's plan is
 * hundreds of macro sums, and language models are bad at those; with [totals] the model stops adding
 * up and starts asking, and the sums are done by code that cannot get them wrong.
 *
 * Deliberately in memory only. If the app dies mid-plan the draft is lost, and nothing was written
 * to the diary, so nothing is inconsistent. That saves a table, a migration and a sync story.
 */
class AssistantPad {

    data class Item(
        val ref: Int,
        val mealId: Long,
        val date: LocalDate,
        val name: String,
        val foodId: Long?,
        /** Set instead of [foodId] when the row is one of the person's recipes. */
        val recipeId: Long? = null,
        val measurement: Measurement,
        /** Facts for the amount in [measurement], not per 100 g. */
        val facts: NutritionFacts,
        val grams: Double,
        /** True when this row was copied from the real diary rather than proposed by the model. */
        val fromDiary: Boolean,
    )

    private val items = mutableListOf<Item>()
    private var nextRef = 1

    val current: List<Item>
        get() = items.toList()

    val proposed: List<Item>
        get() = items.filterNot { it.fromDiary }

    fun clear() {
        items.clear()
        nextRef = 1
    }

    fun add(
        mealId: Long,
        date: LocalDate,
        name: String,
        foodId: Long?,
        measurement: Measurement,
        facts: NutritionFacts,
        grams: Double,
        fromDiary: Boolean = false,
        recipeId: Long? = null,
    ): Item {
        val item =
            Item(
                ref = nextRef++,
                mealId = mealId,
                date = date,
                name = name,
                foodId = foodId,
                recipeId = recipeId,
                measurement = measurement,
                facts = facts,
                grams = grams,
                fromDiary = fromDiary,
            )
        items.add(item)
        return item
    }

    fun remove(ref: Int): Boolean = items.removeAll { it.ref == ref }

    fun find(ref: Int): Item? = items.firstOrNull { it.ref == ref }

    fun replace(item: Item) {
        val index = items.indexOfFirst { it.ref == item.ref }
        if (index >= 0) items[index] = item
    }

    /** The sum the model would otherwise have to do in its head. */
    fun totals(): NutritionFacts =
        items.fold(NutritionFacts.Empty) { acc, item -> acc + item.facts }

    fun totalsFor(mealId: Long): NutritionFacts =
        items.filter { it.mealId == mealId }.fold(NutritionFacts.Empty) { acc, i -> acc + i.facts }

    val isEmpty: Boolean
        get() = items.isEmpty()
}
