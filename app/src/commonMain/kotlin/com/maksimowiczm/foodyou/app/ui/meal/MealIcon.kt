package com.maksimowiczm.foodyou.app.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.BreakfastDining
import androidx.compose.material.icons.outlined.BrunchDining
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private const val MAT_PREFIX = "mat:"
private const val EMOJI_PREFIX = "emoji:"

/** Curated Material icons for meal sections, keyed by a stable id stored as `mat:<id>`. */
internal val materialMealIcons: Map<String, ImageVector> =
    linkedMapOf(
        "Restaurant" to Icons.Outlined.Restaurant,
        "BreakfastDining" to Icons.Outlined.BreakfastDining,
        "BakeryDining" to Icons.Outlined.BakeryDining,
        "BrunchDining" to Icons.Outlined.BrunchDining,
        "LunchDining" to Icons.Outlined.LunchDining,
        "DinnerDining" to Icons.Outlined.DinnerDining,
        "RamenDining" to Icons.Outlined.RamenDining,
        "RiceBowl" to Icons.Outlined.RiceBowl,
        "SetMeal" to Icons.Outlined.SetMeal,
        "Fastfood" to Icons.Outlined.Fastfood,
        "LocalPizza" to Icons.Outlined.LocalPizza,
        "Egg" to Icons.Outlined.Egg,
        "Cookie" to Icons.Outlined.Cookie,
        "Cake" to Icons.Outlined.Cake,
        "Icecream" to Icons.Outlined.Icecream,
        "LocalCafe" to Icons.Outlined.LocalCafe,
        "EmojiFoodBeverage" to Icons.Outlined.EmojiFoodBeverage,
        "LocalDrink" to Icons.Outlined.LocalDrink,
        "LocalBar" to Icons.Outlined.LocalBar,
    )

internal val emojiMealIcons: List<String> =
    listOf(
        "🍳", "🥐", "🥯", "🧇", "🥞", "☕", "🥗", "🍝", "🍜", "🍲", "🍛", "🍚", "🍱", "🍣",
        "🍙", "🍕", "🍔", "🌮", "🌯", "🥙", "🍗", "🍖", "🥩", "🍤", "🧀", "🥓", "🌭", "🥪",
        "🍟", "🥟", "🍰", "🧁", "🍦", "🍩", "🍪", "🍫", "🍿", "🥤", "🍺", "🍷", "🥛", "🍎",
        "🍌", "🍓", "🥑", "🥦", "🥕",
    )

/** Renders a meal section's icon (Material or emoji), or a neutral fallback when none is set. */
@Composable
internal fun MealSectionIcon(
    icon: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColorOrDefault(),
) {
    when {
        icon != null && icon.startsWith(MAT_PREFIX) -> {
            val vector = materialMealIcons[icon.removePrefix(MAT_PREFIX)]
            if (vector != null) {
                Icon(imageVector = vector, contentDescription = null, tint = tint, modifier = modifier)
            } else {
                Icon(Icons.Outlined.Restaurant, null, tint = tint, modifier = modifier)
            }
        }

        icon != null && icon.startsWith(EMOJI_PREFIX) -> {
            Text(
                text = icon.removePrefix(EMOJI_PREFIX),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = modifier,
            )
        }

        else ->
            Icon(
                imageVector = Icons.Outlined.Restaurant,
                contentDescription = null,
                tint = tint,
                modifier = modifier,
            )
    }
}

@Composable
private fun LocalContentColorOrDefault(): Color =
    androidx.compose.material3.LocalContentColor.current

fun materialMealIconValue(id: String): String = "$MAT_PREFIX$id"

fun emojiMealIconValue(emoji: String): String = "$EMOJI_PREFIX$emoji"

/** Dialog with two sections — curated Material icons and custom emoji — to pick a meal-section icon. */
@Composable
internal fun MealIconPickerDialog(
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
        title = { Text(stringResource(Res.string.headline_choose_icon)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionLabel(stringResource(Res.string.headline_icons_material))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    materialMealIcons.forEach { (id, vector) ->
                        val value = materialMealIconValue(id)
                        PickCell(selected = current == value, onClick = { onPick(value) }) {
                            Icon(
                                imageVector = vector,
                                contentDescription = id,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                SectionLabel(stringResource(Res.string.headline_icons_emoji))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojiMealIcons.forEach { emoji ->
                        val value = emojiMealIconValue(emoji)
                        PickCell(selected = current == value, onClick = { onPick(value) }) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PickCell(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor =
            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp).size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
