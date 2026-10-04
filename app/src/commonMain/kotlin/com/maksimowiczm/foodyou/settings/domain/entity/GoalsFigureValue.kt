package com.maksimowiczm.foodyou.settings.domain.entity

/**
 * What the ring and arc styles print in the middle of their figure. Only those two styles have a
 * centre to fill; every other style ignores this.
 */
enum class GoalsFigureValue {
    /** How much of the day's goal is used, as a percentage. */
    Percentage,

    /** The calories eaten so far. */
    Energy,
}
