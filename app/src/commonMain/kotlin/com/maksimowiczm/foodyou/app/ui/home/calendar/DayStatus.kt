package com.maksimowiczm.foodyou.app.ui.home.calendar

internal enum class DayStatus {
    /** No diary entries recorded for this day — no dot shown. */
    None,

    /** Entries exist and total kcal is within ±15% of the energy goal — green dot. */
    NearGoal,

    /** Entries exist but total kcal is outside ±15% of the energy goal — purple dot. */
    OffGoal,
}
