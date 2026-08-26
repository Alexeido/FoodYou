package com.maksimowiczm.foodyou.settings.domain.entity

/**
 * How the daily-goal card draws its macronutrients. The calorie block on the left is the same in
 * every style; only the figure beside it changes.
 */
enum class GoalsCardStyle {
    /** Thick vertical bars — the app's original look. */
    Bars,

    /** Consumed over goal with a thin track, in narrow columns. */
    Columns,

    /** A closed ring showing the day's percentage. */
    Ring,

    /** An open gauge showing the day's percentage. */
    Arc,

    /** One stacked bar showing how calories are split between macros. */
    Stacked,

    /** Figures only, no graphics — the shortest style. */
    Numbers,

    /** Three horizontal bars, each with its own goal. */
    HorizontalBars;

    /** Styles whose right-hand side is wide enough that "calories left" would crowd the card. */
    val showsEnergyLeft: Boolean
        get() = this != Columns && this != Numbers

    /** Styles that carry no macro figures of their own, so they list them under the calories. */
    val showsInlineMacros: Boolean
        get() = this == Ring || this == Arc
}
