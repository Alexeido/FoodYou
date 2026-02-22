package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.FoodErrorListItem
import com.maksimowiczm.foodyou.app.ui.common.component.FoodListItem
import com.maksimowiczm.foodyou.app.ui.common.component.FoodListItemSkeleton
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.valentinilk.shimmer.Shimmer
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private fun parseHeadlineBrand(headline: String): Pair<String, String?> {
    val open = headline.indexOf('(')
    if (open == -1) return Pair(headline, null)
    val close = headline.indexOf(')', startIndex = open + 1).takeIf { it > open } ?: return Pair(headline, null)
    val before = headline.substring(0, open).trimEnd()
    val inside = headline.substring(open + 1, close)
    val first = inside.split(',').firstOrNull()?.trim() ?: inside.trim()
    val brand = if (first.isEmpty()) null else first
    return Pair(before, brand)
}

private fun truncateHeadlineBrand(headline: String): String {
    val open = headline.indexOf('(')
    if (open == -1) return headline
    val close = headline.indexOf(')', startIndex = open + 1).takeIf { it > open } ?: return headline
    val before = headline.substring(0, open).trimEnd()
    val inside = headline.substring(open + 1, close)
    val first = inside.split(',').firstOrNull()?.trim() ?: inside.trim()
    return if (first.isEmpty()) headline else "$before ($first)"
}

@Composable
internal fun FoodSearchListItem(
    food: FoodSearch.Product,
    measurement: Measurement,
    onClick: () -> Unit,
    onToggleFavorite: ((com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val weight = food.weight(measurement)
    val factor = weight?.div(100)

    if (factor == null) {
        return FoodErrorListItem(
            headline = food.headline,
            errorMessage = stringResource(Res.string.error_measurement_error),
            modifier = modifier,
            onClick = onClick,
        )
    }

    val measurementFacts = food.nutritionFacts * factor
    val proteins = measurementFacts.proteins.value
    val carbohydrates = measurementFacts.carbohydrates.value
    val fats = measurementFacts.fats.value
    val energy = measurementFacts.energy.value
    val measurementString =
        measurement.stringResourceWithWeight(
            totalWeight = food.totalWeight,
            servingWeight = food.servingWeight,
            isLiquid = food.isLiquid,
        )

    if (
        proteins == null ||
        carbohydrates == null ||
        fats == null ||
        energy == null ||
        measurementString == null
    ) {
        return FoodErrorListItem(
            headline = food.headline,
            modifier = modifier,
            onClick = onClick,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
        )
    }

    FoodSearchListItem(
        headline = food.headline,
        proteins = proteins,
        carbohydrates = carbohydrates,
        fats = fats,
        energy = energy,
        measurement = { Text(measurementString) },
        categories = food.categories,
        isRecipe = false,
        onClick = onClick,
        productId = food.id,
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

    val factor = recipe.weight(measurement) / 100
    val measurementFacts = recipe.nutritionFacts * factor
    val proteins = measurementFacts.proteins.value
    val carbohydrates = measurementFacts.carbohydrates.value
    val fats = measurementFacts.fats.value
    val energy = measurementFacts.energy.value

    val measurementString =
        measurement.stringResourceWithWeight(
            totalWeight = recipe.totalWeight,
            servingWeight = recipe.servingWeight,
            isLiquid = recipe.isLiquid,
        )

    if (
        (proteins == null || proteins.isNaN()) ||
        (carbohydrates == null || carbohydrates.isNaN()) ||
        (fats == null || fats.isNaN()) ||
        (energy == null || energy.isNaN()) ||
        measurementString == null
    ) {
        return FoodErrorListItem(
            headline = food.headline,
            modifier = modifier,
            onClick = onClick,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
        )
    }

    FoodSearchListItem(
        headline = food.headline,
        proteins = proteins,
        carbohydrates = carbohydrates,
        fats = fats,
        energy = energy,
        measurement = { Text(measurementString) },
        categories = null,
        isRecipe = true,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun FoodSearchListItem(
    headline: String,
    proteins: Double,
    carbohydrates: Double,
    fats: Double,
    energy: Double,
    measurement: @Composable () -> Unit,
    categories: List<String>?,
    isRecipe: Boolean,
    onClick: () -> Unit,
    productId: com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product? = null,
    isFavorite: Boolean? = null,
    onToggleFavorite: ((com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val g = stringResource(Res.string.unit_gram_short)
    val category = categories?.let { getFoodCategoryFromTags(it) } ?: getFoodCategory(headline)

    FoodListItem(
        name = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodCategoryIcon(category = category)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    val (titleOnly, brand) = parseHeadlineBrand(headline)
                    Text(text = titleOnly)
                    brand?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        proteins = {
            val text = proteins.formatClipZeros()
            Text("$text $g")
        },
        carbohydrates = {
            val text = carbohydrates.formatClipZeros()
            Text("$text $g")
        },
        fats = {
            val text = fats.formatClipZeros()
            Text("$text $g")
        },
        calories = { Text(LocalEnergyFormatter.current.formatEnergy(energy.roundToInt())) },
        measurement = measurement,
        isRecipe = isRecipe,
        modifier = modifier,
        onClick = onClick,
        trailingContent = {
            if (!isRecipe && onToggleFavorite != null && productId != null && isFavorite != null) {
                IconButton(onClick = { onToggleFavorite(productId, !isFavorite) }) {
                    val icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}
