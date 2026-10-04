package com.maksimowiczm.foodyou.app.ui.food.component

/** Words that must not be left dangling at the end after stripping a brand ("Jamoncito de"). */
private val DANGLING_WORDS =
    setOf("de", "del", "la", "el", "los", "las", "con", "y", "of", "the", "a", "al")

/**
 * Splits a `"Name (Brand)"` headline into its name and brand parts. Returns the headline unchanged
 * with a `null` brand when it carries no parenthesised brand.
 */
fun parseHeadlineBrand(headline: String): Pair<String, String?> {
    val open = headline.indexOf('(')
    if (open == -1) return Pair(headline, null)
    val close =
        headline.indexOf(')', startIndex = open + 1).takeIf { it > open } ?: return Pair(headline, null)
    val before = headline.substring(0, open).trimEnd()
    val inside = headline.substring(open + 1, close)
    val first = inside.split(',').firstOrNull()?.trim() ?: inside.trim()
    val brand = if (first.isEmpty()) null else first
    return Pair(before, brand)
}

/**
 * Drops a brand that is repeated inside the product name, so "Altramuces Hacendado" + brand
 * "Hacendado" shows as just "Altramuces".
 *
 * Only strips the brand when it sits at the very start or the very end of the name (on a word
 * boundary), never in the middle — otherwise "Jamoncito de Pavo" by brand "Pavo" would be mangled.
 * It also refuses to leave the name ending on a preposition, and never returns blank.
 */
fun stripBrandFromName(name: String, brand: String?): String {
    if (brand.isNullOrBlank()) return name

    val n = name.trim()
    val b = brand.trim()
    if (b.isEmpty() || n.length <= b.length || n.equals(b, ignoreCase = true)) return name

    val stripped =
        when {
            n.regionMatches(n.length - b.length, b, 0, b.length, ignoreCase = true) &&
                n[n.length - b.length - 1].isWhitespace() -> n.substring(0, n.length - b.length)

            n.regionMatches(0, b, 0, b.length, ignoreCase = true) && n[b.length].isWhitespace() ->
                n.substring(b.length)

            else -> return name
        }

    val cleaned = stripped.trim().trimEnd('-', ',', '·').trim()
    if (cleaned.isBlank()) return name

    // Don't leave something like "Jamoncito de".
    val lastWord = cleaned.substringAfterLast(' ').lowercase()
    if (lastWord in DANGLING_WORDS) return name

    return cleaned
}

/**
 * Convenience for display: parses a `"Name (Brand)"` headline and removes the brand when the name
 * repeats it. Returns the display name and the brand to show as a subtitle.
 */
fun splitFoodName(headline: String): Pair<String, String?> {
    val (rawName, brand) = parseHeadlineBrand(headline)
    return stripBrandFromName(rawName, brand) to brand
}
