package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Which conceptual tab is active, derived from the current [FoodFilter] + the active DB source. */
internal enum class SearchTab {
    Recent,
    YourFood,
    Database,
    Favorites,
}

internal fun FoodSource.Type.toFilterSource(): FoodFilter.Source =
    when (this) {
        FoodSource.Type.OpenFoodFacts -> FoodFilter.Source.OpenFoodFacts
        FoodSource.Type.USDA -> FoodFilter.Source.USDA
        FoodSource.Type.SwissFoodCompositionDatabase ->
            FoodFilter.Source.SwissFoodCompositionDatabase
        FoodSource.Type.Custom -> FoodFilter.Source.Custom
        FoodSource.Type.User -> FoodFilter.Source.YourFood
    }

/**
 * Section tabs: Recent · Yours · Database ▾ · Favorites. Remote sources collapse into the single
 * "Database" tab; its ▾ opens a picker to switch the active source among the enabled ones.
 */
@Composable
internal fun SearchTabs(
    activeTab: SearchTab,
    activeDbSource: FoodFilter.Source?,
    enabledRemoteSources: List<FoodSource.Type>,
    onRecent: () -> Unit,
    onYourFood: () -> Unit,
    onFavorites: () -> Unit,
    onDatabase: (FoodFilter.Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(46.dp)) {
            TabItem(
                label = stringResource(Res.string.headline_recent),
                selected = activeTab == SearchTab.Recent,
                onClick = onRecent,
                modifier = Modifier.weight(1f),
            )
            TabItem(
                label = stringResource(Res.string.headline_your_food_short),
                selected = activeTab == SearchTab.YourFood,
                onClick = onYourFood,
                modifier = Modifier.weight(1f),
            )

            TabItem(
                label = stringResource(Res.string.headline_favorites),
                selected = activeTab == SearchTab.Favorites,
                onClick = onFavorites,
                modifier = Modifier.weight(1f),
            )

            if (enabledRemoteSources.isNotEmpty()) {
                DatabaseTab(
                    activeDbSource = activeDbSource,
                    selected = activeTab == SearchTab.Database,
                    enabledRemoteSources = enabledRemoteSources,
                    onDatabase = onDatabase,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingChevron: Boolean = false,
) {
    val color =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingChevron) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(3.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                    )
        )
    }
}

@Composable
private fun DatabaseTab(
    activeDbSource: FoodFilter.Source?,
    selected: Boolean,
    enabledRemoteSources: List<FoodSource.Type>,
    onDatabase: (FoodFilter.Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val label =
        activeDbSource?.stringResource() ?: stringResource(Res.string.headline_database)

    Box(modifier = modifier) {
        TabItem(
            label = label,
            selected = selected,
            trailingChevron = enabledRemoteSources.size > 1,
            onClick = {
                if (enabledRemoteSources.size > 1) {
                    menuOpen = true
                } else {
                    activeDbSource?.let(onDatabase)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            enabledRemoteSources.forEach { type ->
                val source = type.toFilterSource()
                val isActive = source == activeDbSource
                DropdownMenuItem(
                    text = { Text(source.stringResource()) },
                    onClick = {
                        menuOpen = false
                        onDatabase(source)
                    },
                    leadingIcon = { source.Icon(Modifier.size(24.dp)) },
                    trailingIcon = {
                        if (isActive) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}
