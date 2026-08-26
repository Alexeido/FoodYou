package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Uppercase, letter-spaced section header matching the design ("RECENT MEALS", "RECENT FOODS"). */
@Composable
internal fun SearchSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * Inline recent-search suggestions, anchored under the search bar. Unlike the old full-screen
 * search surface, this does not cover the screen — it's a small card that appears while the field
 * is focused and empty.
 */
@Composable
internal fun RecentSearchSuggestions(
    searches: List<String>,
    onFill: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (searches.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            searches.take(5).forEach { search ->
                ListItem(
                    modifier = Modifier.clickable { onSearch(search) },
                    headlineContent = { Text(search) },
                    leadingContent = {
                        Icon(imageVector = Icons.Outlined.History, contentDescription = null)
                    },
                    trailingContent = {
                        IconButton(onClick = { onFill(search) }) {
                            Icon(
                                imageVector = Icons.Outlined.NorthWest,
                                contentDescription =
                                    stringResource(Res.string.action_insert_suggested_search),
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
