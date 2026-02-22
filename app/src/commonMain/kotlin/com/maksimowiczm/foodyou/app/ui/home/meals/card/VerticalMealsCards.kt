package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.Shimmer
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

internal fun LazyListScope.verticalMealsCardsItems(
    meals: List<MealModel>?,
    localFlatItems: List<MealListItem>,
    reorderState: ReorderableLazyListState,
    onAdd: (mealId: Long) -> Unit,
    onQuickAdd: (mealId: Long) -> Unit,
    onEditEntry: (MealEntryModel) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    onToggleEaten: (MealEntryModel) -> Unit,
    onDragStopped: () -> Unit,
    onLongClick: (mealId: Long) -> Unit,
    shimmer: Shimmer,
    horizontalPadding: Dp,
) {
    if (meals == null) {
        repeat(4) { i ->
            item(key = "meal_skeleton_$i") {
                MealCardSkeleton(
                    shimmer = shimmer,
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding)
                        .padding(bottom = 8.dp),
                )
            }
        }
        return
    }

    itemsIndexed(localFlatItems, key = { _, item -> item.key }) { index, item ->
        when (item) {
            is MealListItem.Header -> {
                Column(Modifier.padding(horizontal = horizontalPadding)) {
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    MealCardHeaderSection(
                        meal = item.meal,
                        onAddFood = { onAdd(item.meal.id) },
                        onLongClick = { onLongClick(item.meal.id) },
                    )
                }
            }

            is MealListItem.Entry -> {
                val prevItem = localFlatItems.getOrNull(index - 1)
                val nextItem = localFlatItems.getOrNull(index + 1)
                // Ghost counts as a group boundary: an entry adjacent to a Ghost is still
                // visually first/last in its meal section.
                val isFirstInGroup =
                    prevItem is MealListItem.Header || prevItem is MealListItem.Ghost
                val isLastInGroup =
                    nextItem is MealListItem.Footer || nextItem is MealListItem.Ghost

                ReorderableItem(state = reorderState, key = item.key) { isDragging ->
                    MealCardEntrySection(
                        entry = item.entry,
                        isFirstInGroup = isFirstInGroup,
                        isLastInGroup = isLastInGroup,
                        isDragging = isDragging,
                        onEditEntry = onEditEntry,
                        onDeleteEntry = onDeleteEntry,
                        onToggleEaten = onToggleEaten,
                        onDragStopped = onDragStopped,
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
            }

            is MealListItem.Footer -> {
                MealCardFooterSection(
                    meal = item.meal,
                    onAddFood = { onAdd(item.meal.id) },
                    onQuickAdd = { onQuickAdd(item.meal.id) },
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
            }

            is MealListItem.Ghost -> {
                // Drop target for empty meals. Wrapped in ReorderableItem so the reorder
                // library treats it as a valid swap destination, but it has no drag handle
                // so it can never be dragged itself.
                // Styled with surfaceVariant + full 12dp rounding to match a combined
                // first+last MealCardEntrySection — blends seamlessly with the meal card.
                ReorderableItem(state = reorderState, key = item.key) { _ ->
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = horizontalPadding)
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(0.dp),
                    ) {}
                }
            }
        }
    }
}
