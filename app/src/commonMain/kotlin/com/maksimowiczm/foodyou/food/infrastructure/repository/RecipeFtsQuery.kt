package com.maksimowiczm.foodyou.food.infrastructure.repository

/**
 * Turns free text into a safe FTS4 query for the recipe index, or null when there is nothing to
 * search for.
 *
 * The index is what makes "albondigas" find "Albóndigas" (unicode61 with remove_diacritics=2 folds
 * accents and case), which a plain LIKE never did. But MATCH gives meaning to quotes, dashes, `*`
 * and the upper-case words AND/OR/NOT/NEAR, and the text comes from a language model that writes
 * whatever it likes - "pan-con tomate" or "salsa AND queso" would either fail or search for
 * something else. So only letters and digits survive, each word goes lower-case and quoted, and
 * each is a prefix: `"albondigas*" "caseras*"`, every word required, as in the app's own search.
 */
internal fun recipeFtsQuery(text: String): String? {
    val words =
        text
            .lowercase()
            .split(NON_WORD)
            .filter { it.isNotBlank() }
            .take(MAX_WORDS)
    if (words.isEmpty()) return null
    return words.joinToString(" ") { "\"$it*\"" }
}

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/** A dish name, not a paragraph: past this it is noise, and every word narrows the match. */
private const val MAX_WORDS = 6
