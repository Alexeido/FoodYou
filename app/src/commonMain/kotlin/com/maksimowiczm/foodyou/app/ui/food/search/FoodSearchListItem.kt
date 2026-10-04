package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.FoodErrorListItem
import com.maksimowiczm.foodyou.app.ui.common.component.FoodListItemSkeleton
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.food.component.parseHeadlineBrand
import com.maksimowiczm.foodyou.app.ui.food.component.stripBrandFromName
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.valentinilk.shimmer.Shimmer
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Compact number for the search list: at most one decimal, trailing ".0" clipped. */
private fun Double.compact1(): String {
    val rounded = (this * 10.0).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

@Composable
internal fun FoodSearchListItem(
    food: FoodSearch.Product,
    measurement: Measurement,
    onClick: () -> Unit,
    onToggleFavorite: ((FoodId, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Search always shows per-100 g/ml values so products are comparable at a glance, regardless of
    // each one's serving size.
    val facts = food.nutritionFacts
    val proteins = facts.proteins.value
    val carbohydrates = facts.carbohydrates.value
    val fats = facts.fats.value
    val energy = facts.energy.value

    if (proteins == null || carbohydrates == null || fats == null || energy == null) {
        return FoodErrorListItem(
            headline = food.headline,
            modifier = modifier,
            onClick = onClick,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
        )
    }

    CompactFoodSearchRow(
        headline = food.headline,
        proteins = proteins,
        carbohydrates = carbohydrates,
        fats = fats,
        energy = energy,
        categories = food.categories,
        isRecipe = false,
        onClick = onClick,
        foodId = food.id,
        isFavorite = food.isFavorite,
        onToggleFavorite = onToggleFavorite,
        modifier = modifier,
    )
}

/** Recipe has to be lazy loaded, so we use [ObserveFoodUseCase] to observe the recipe. */
@Composable
internal fun FoodSearchListItem(
    food: FoodSearch.Recipe,
    measurement: Measurement,
    onClick: () -> Unit,
    shimmer: Shimmer,
    modifier: Modifier = Modifier,
    onToggleFavorite: ((FoodId, Boolean) -> Unit)? = null,
) {
    val observeRecipeUseCase: ObserveFoodUseCase = koinInject()

    val recipe =
        observeRecipeUseCase
            .observe(food.id)
            .mapNotNull { it as? Recipe }
            .collectAsStateWithLifecycle(null)
            .value

    if (recipe == null) {
        return FoodListItemSkeleton(shimmer)
    }

    val facts = recipe.nutritionFacts
    val proteins = facts.proteins.value
    val carbohydrates = facts.carbohydrates.value
    val fats = facts.fats.value
    val energy = facts.energy.value

    if (
        (proteins == null || proteins.isNaN()) ||
            (carbohydrates == null || carbohydrates.isNaN()) ||
            (fats == null || fats.isNaN()) ||
            (energy == null || energy.isNaN())
    ) {
        return FoodErrorListItem(
            headline = food.headline,
            modifier = modifier,
            onClick = onClick,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
        )
    }

    CompactFoodSearchRow(
        headline = food.headline,
        proteins = proteins,
        carbohydrates = carbohydrates,
        fats = fats,
        energy = energy,
        categories = null,
        isRecipe = true,
        onClick = onClick,
        foodId = food.id,
        isFavorite = food.isFavorite,
        onToggleFavorite = onToggleFavorite,
        modifier = modifier,
    )
}

/**
 * Dense search result row, modelled on the design mock: the category icon sits in a contrast box
 * vertically centred against the whole row, the name/brand truncate with an ellipsis instead of
 * wrapping, and weight + energy + macros share a single compact line ("200 g · 210 kcal · 24P ·
 * 3.4G · 18C").
 *
 * Deliberately not built on [com.maksimowiczm.foodyou.app.ui.common.component.FoodListItem] — that
 * one is shared with the home/meal/recipe screens and stays as-is.
 */
@Composable
private fun CompactFoodSearchRow(
    headline: String,
    proteins: Double,
    carbohydrates: Double,
    fats: Double,
    energy: Double,
    categories: List<String>?,
    isRecipe: Boolean,
    onClick: () -> Unit,
    foodId: FoodId? = null,
    isFavorite: Boolean? = null,
    onToggleFavorite: ((FoodId, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val category =
        if (isRecipe) recipeCategory(null, headline)
        else categories?.let { getFoodCategoryFromTags(it) } ?: getFoodCategory(headline)
    val palette = LocalNutrientsPalette.current
    val (rawTitle, brand) = parseHeadlineBrand(headline)
    // Drop the brand when it's repeated inside the name ("Altramuces Hacendado" -> "Altramuces").
    val titleOnly = stripBrandFromName(rawTitle, brand)

    Surface(onClick = onClick, modifier = modifier, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(
                        LocalTextStyle provides LocalTextStyle.current.copy(fontSize = 17.sp)
                    ) {
                        FoodCategoryIcon(category = category)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleOnly,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (brand != null) {
                    Text(
                        text = brand,
                        style =
                            MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // energy · macros, all on one line. Values are per 100 g/ml, so no unit label is
                // shown — it would be the same on every row.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodySmall
                    ) {
                        Text(
                            text = LocalEnergyFormatter.current.formatEnergy(energy.roundToInt()),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = "${proteins.compact1()}P",
                            color = palette.proteinsOnSurfaceContainer,
                            maxLines = 1,
                        )
                        Text(
                            text = "${fats.compact1()}G",
                            color = palette.fatsOnSurfaceContainer,
                            maxLines = 1,
                        )
                        Text(
                            text = "${carbohydrates.compact1()}C",
                            color = palette.carbohydratesOnSurfaceContainer,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Category badge — disabled for now to give the macros all the width they need.
            // Re-enable by uncommenting; `category` is still computed above for the icon.
            // if (!isRecipe && category != FoodCategory.UNKNOWN) {
            //     Surface(
            //         shape = RoundedCornerShape(8.dp),
            //         color = MaterialTheme.colorScheme.surfaceContainerHigh,
            //     ) {
            //         Text(
            //             text = category.label,
            //             style = MaterialTheme.typography.labelSmall,
            //             color = MaterialTheme.colorScheme.onSurfaceVariant,
            //             maxLines = 1,
            //             overflow = TextOverflow.Ellipsis,
            //             modifier =
            //                 Modifier.widthIn(max = 84.dp)
            //                     .padding(horizontal = 8.dp, vertical = 3.dp),
            //         )
            //     }
            // }

            // Recetas tambien: una receta es lo que mas se repite, y es justo lo que uno quiere
            // tener a mano en favoritos.
            if (onToggleFavorite != null && foodId != null && isFavorite != null) {
                IconButton(
                    onClick = { onToggleFavorite(foodId, !isFavorite) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector =
                            if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
