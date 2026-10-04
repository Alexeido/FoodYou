package com.maksimowiczm.foodyou.app.ui.assistant.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.food.search.FoodCategory
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The sliver of Markdown these answers actually contain, rendered instead of shown raw.
 *
 * The model writes `**bold**` for the numbers that matter, `- ` lists for anything it enumerates,
 * `### ` headings with an emoji and `---` rules to break up a longer plan into sections, and a
 * ` ```foodcard ` fenced block wherever it wants a food breakdown to render as a real card instead
 * of another bullet list. That last one is not standard Markdown - it is the one piece of syntax
 * this app defines itself, and the system prompt teaches the model to use it. A fenced block was
 * chosen over some other marker because the model already reaches for fences naturally, so a
 * malformed one degrades to plain text instead of leaking a broken tag into the middle of a
 * sentence.
 *
 * Otherwise deliberately not a Markdown implementation: no links, no other code fences, no tables,
 * no nesting. Anything it does not recognise is left exactly as written, which is the right failure
 * mode for text that is going to be read by a person either way.
 */
@Composable
internal fun AssistantMarkdown(raw: String, modifier: Modifier = Modifier) {
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val lines = raw.split('\n')

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var index = 0
        while (index < lines.size) {
            val line = lines[index]

            if (FENCE_OPEN.matchEntire(line)?.groupValues?.get(1).equals(FOOD_CARD_LANG, ignoreCase = true)) {
                val close = (index + 1 until lines.size).firstOrNull { lines[it].trim() == "```" }
                val card = close?.let { parseFoodCard(lines.subList(index + 1, it).joinToString("\n")) }
                if (card != null) {
                    FoodCard(card, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    index = close + 1
                    continue
                }
                // Bloque mal formado o JSON invalido: se ignora el marcador y la linea sigue el
                // camino normal de abajo, en vez de tragarse el resto de la respuesta buscando un
                // cierre que quiza no llegue.
            }

            when {
                line.isBlank() -> Unit
                RULE.matches(line) -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
                else -> {
                    val heading = HEADING.matchEntire(line)
                    if (heading != null) {
                        val level = heading.groupValues[1].length
                        Text(
                            text = inlineOf(heading.groupValues[2]),
                            style =
                                if (level == 1) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                    } else {
                        val bullet = BULLET.matchEntire(line)
                        if (bullet != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("•", style = bodyStyle)
                                Text(inlineOf(bullet.groupValues[1]), style = bodyStyle)
                            }
                        } else {
                            Text(inlineOf(line), style = bodyStyle)
                        }
                    }
                }
            }
            index++
        }
    }
}

/** What a `foodcard` block decodes into. Every field but `items` is optional and just left off the card when absent. */
@Serializable
private data class FoodCardData(
    val title: String? = null,
    val items: List<FoodCardItem> = emptyList(),
    val kcal: Double? = null,
    val proteins: Double? = null,
    val carbohydrates: Double? = null,
    val fats: Double? = null,
)

@Serializable
private data class FoodCardItem(
    val name: String,
    val grams: Double? = null,
    val brand: String? = null,
    val kcal: Double? = null,
    /** One of the [FoodCategory] enum names - the same vocabulary `createManualEntry` uses. */
    val category: String? = null,
)

private val cardJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun parseFoodCard(body: String): FoodCardData? =
    runCatching { cardJson.decodeFromString<FoodCardData>(body) }.getOrNull()

@Composable
private fun FoodCard(data: FoodCardData, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (data.title != null) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            data.items.forEach { item -> FoodCardItemRow(item) }

            val hasTotals =
                data.kcal != null || data.proteins != null || data.carbohydrates != null || data.fats != null
            if (hasTotals) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    data.kcal?.let { MacroFigure("kcal", it.formatClipZeros("%.1f")) }
                    data.proteins?.let { MacroFigure("P", "${it.formatClipZeros("%.1f")}g") }
                    data.carbohydrates?.let { MacroFigure("C", "${it.formatClipZeros("%.1f")}g") }
                    data.fats?.let { MacroFigure("G", "${it.formatClipZeros("%.1f")}g") }
                }
            }
        }
    }
}

/**
 * One line per food: category emoji, then name (with brand in parentheses, truncated with an
 * ellipsis if it runs long) taking the flexible space, then grams/kcal fixed at the end so a long
 * name or brand can never push them off-screen or squeeze them.
 */
@Composable
private fun FoodCardItemRow(item: FoodCardItem) {
    val emoji =
        item.category
            ?.let { name -> runCatching { FoodCategory.valueOf(name) }.getOrNull() }
            ?.emoji
            ?: FoodCategory.UNKNOWN.emoji

    val label = if (item.brand.isNullOrBlank()) item.name else "${item.name} (${item.brand})"

    val trailing =
        listOfNotNull(
                item.grams?.let { "${it.formatClipZeros("%.1f")} g" },
                item.kcal?.let { "${it.formatClipZeros("%.1f")} kcal" },
            )
            .joinToString(" · ")

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier.size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing.isNotEmpty()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroFigure(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** `**bold**` and `*italic*` inside one line, leaving unmatched asterisks untouched. */
private fun inlineOf(line: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < line.length) {
        val match = EMPHASIS.find(line, cursor)
        if (match == null) {
            append(line.substring(cursor))
            return@buildAnnotatedString
        }

        append(line.substring(cursor, match.range.first))

        val bold = match.groupValues[1]
        val italic = match.groupValues[2]
        when {
            bold.isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            italic.isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        }

        cursor = match.range.last + 1
    }
}

/** `**...**` first, so a bold run is never mistaken for two italic ones. */
private val EMPHASIS = Regex("""\*\*(.+?)\*\*|\*([^*\n]+?)\*""")

private val BULLET = Regex("""^\s*[-*]\s+(.*)$""")

/** `#`, `##` or `###` followed by a space - deeper levels are treated the same as `###`. */
private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")

/** A line of three or more dashes on its own, the Markdown horizontal rule. */
private val RULE = Regex("""^-{3,}$""")

/** The opening line of any fenced block: ` ```lang ` with the language name captured. */
private val FENCE_OPEN = Regex("""^```(\S*)\s*$""")

private const val FOOD_CARD_LANG = "foodcard"
