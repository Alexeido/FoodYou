package com.maksimowiczm.foodyou.app.ui.home.meals.card

internal sealed interface MealListItem {
    val key: String

    data class Header(val meal: MealModel) : MealListItem {
        override val key = "h_${meal.id}"
    }

    data class Entry(val mealId: Long, val entry: MealEntryModel) : MealListItem {
        override val key = when (entry) {
            is FoodMealEntryModel -> "f_${entry.id.value}"
            is ManualMealEntryModel -> "m_${entry.id.value}"
        }
    }

    data class Footer(val meal: MealModel) : MealListItem {
        override val key = "fo_${meal.id}"
    }

    /**
     * Placeholder injected between Header and Footer for meals with no entries.
     * Acts as a ReorderableItem drop target so dragged entries can be dropped into
     * an empty meal. Ignored by [toReorderedAssignments] — never persisted to the DB.
     */
    data class Ghost(val mealId: Long) : MealListItem {
        override val key = "ghost_$mealId"
    }
}

internal fun List<MealModel>.toFlatItems(): List<MealListItem> {
    // seenKeys deduplicates entries that temporarily appear in two meals during Room's
    // intermediate re-query state after a cross-meal move (race condition in combine()).
    val seenKeys = mutableSetOf<String>()
    return flatMap { meal ->
        buildList {
            add(MealListItem.Header(meal))
            val uniqueFoods = meal.foods.filter { entry ->
                val key = when (entry) {
                    is FoodMealEntryModel -> "f_${entry.id.value}"
                    is ManualMealEntryModel -> "m_${entry.id.value}"
                }
                seenKeys.add(key) // returns false (and filters out) if key was already seen
            }
            if (uniqueFoods.isEmpty()) {
                add(MealListItem.Ghost(meal.id))
            } else {
                uniqueFoods.forEach { add(MealListItem.Entry(meal.id, it)) }
            }
            add(MealListItem.Footer(meal))
        }
    }
}

/**
 * Ensures every empty meal (Header immediately followed by Footer) has exactly one Ghost,
 * and every non-empty meal has no Ghost. Call this after every drag swap so that the
 * drop-zone invariant is maintained throughout the drag gesture.
 */
internal fun List<MealListItem>.withSyncedGhosts(): List<MealListItem> {
    // 1. Strip all existing ghosts
    val withoutGhosts = filter { it !is MealListItem.Ghost }
    // 2. Re-inject a Ghost wherever a Header is immediately followed by a Footer (empty meal)
    return buildList {
        withoutGhosts.forEachIndexed { index, item ->
            add(item)
            if (item is MealListItem.Header) {
                if (withoutGhosts.getOrNull(index + 1) is MealListItem.Footer) {
                    add(MealListItem.Ghost(item.meal.id))
                }
            }
        }
    }
}

internal fun List<MealListItem>.toReorderedAssignments(): List<Triple<MealEntryModel, Long, Int>> {
    var currentMealId = -1L
    var pos = 0
    val assignments = mutableListOf<Triple<MealEntryModel, Long, Int>>()
    forEach { item ->
        when (item) {
            is MealListItem.Header -> { currentMealId = item.meal.id; pos = 0 }
            is MealListItem.Entry -> { assignments += Triple(item.entry, currentMealId, pos++) }
            is MealListItem.Footer -> Unit
            is MealListItem.Ghost -> Unit // not a real entry — never persisted
        }
    }
    return assignments
}
