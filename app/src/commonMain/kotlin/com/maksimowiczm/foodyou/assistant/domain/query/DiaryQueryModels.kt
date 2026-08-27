package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import kotlinx.datetime.LocalDate

/** How [DailyTotalsUseCase] buckets a date range. */
enum class TotalsGrouping {
    Day,
    Weekday,
    Week,
    Month;

    companion object {
        fun fromWireName(value: String?): TotalsGrouping =
            when (value?.lowercase()) {
                null, "day", "dia" -> Day
                "weekday", "diasemana" -> Weekday
                "week", "semana" -> Week
                "month", "mes" -> Month
                else -> Day
            }
    }
}

/**
 * Totals for one bucket of a range.
 *
 * [date] is only set when the bucket is a single day - a weekday or month bucket spans several, so
 * the caller must not assume one.
 */
data class PeriodTotals(
    val key: String,
    val date: LocalDate?,
    val facts: NutritionFacts,
    val entryCount: Int,
    val eatenCount: Int,
) {
    val energy: Double
        get() = facts.energy.value ?: 0.0
}

/** One food and how much of it was logged over a range. */
data class FoodFrequency(
    val name: String,
    val brand: String?,
    val times: Int,
    val totalGrams: Double,
    val totalEnergy: Double,
)

/** One brand and how often it shows up. This is what makes the model pick shops you actually use. */
data class BrandFrequency(val brand: String, val times: Int, val totalEnergy: Double)

/** How much of a single nutrient one food contributed over a range. */
data class NutrientContribution(
    val name: String,
    val brand: String?,
    val amount: Double,
    val share: Double,
)

/** A diary entry matching a text search. */
data class DiaryHit(
    val date: LocalDate,
    val mealId: Long,
    val name: String,
    val grams: Double,
    val energy: Double,
)

/** When a meal actually gets logged, averaged over a range. */
data class MealTiming(
    val mealId: Long,
    val mealName: String,
    val averageMinuteOfDay: Int,
    val samples: Int,
) {
    val averageHour: Int
        get() = averageMinuteOfDay / 60

    val averageMinute: Int
        get() = averageMinuteOfDay % 60
}
