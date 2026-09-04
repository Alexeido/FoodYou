package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * C5 - nutrition totals over a range, bucketed.
 *
 * Reads through [DiaryReader] rather than a single repository so a manual entry - the assistant's
 * own [com.maksimowiczm.foodyou.assistant.domain.tool.write.CreateManualEntryTool], or one the user
 * added by hand - counts towards the total exactly like the Home screen already does. Aggregating in
 * Kotlin rather than in SQL is deliberate too: the weight of a product entry depends on its
 * measurement type and the food's serving/package weights, logic that already exists and is
 * exercised everywhere else in the app.
 */
class DailyTotalsUseCase(private val diary: DiaryReader) {

    suspend operator fun invoke(
        from: LocalDate,
        to: LocalDate,
        grouping: TotalsGrouping = TotalsGrouping.Day,
        onlyEaten: Boolean = false,
    ): List<PeriodTotals> {
        val entries = diary.range(from, to)
        val considered = if (onlyEaten) entries.filter { it.isEaten } else entries

        if (grouping == TotalsGrouping.Day) {
            // Every day in the range appears, including the empty ones: "which days did I go over"
            // has to be able to see a day with nothing logged.
            return generateDays(from, to).map { day ->
                val ofDay = considered.filter { it.date == day }
                totals(key = day.toString(), date = day, entries = ofDay)
            }
        }

        if (grouping == TotalsGrouping.Meal) {
            val buckets = considered.groupBy { it.mealId }
            return buckets.entries
                .sortedBy { it.key }
                .map { (mealId, ofMeal) ->
                    totals(key = mealId.toString(), date = null, entries = ofMeal, mealId = mealId)
                }
        }

        val buckets = considered.groupBy { entry -> bucketKey(entry.date, grouping) }
        return buckets.entries
            .sortedBy { it.key }
            .map { (key, ofBucket) -> totals(key = key, date = null, entries = ofBucket) }
    }

    private fun totals(
        key: String,
        date: LocalDate?,
        entries: List<DiaryLine>,
        mealId: Long? = null,
    ) =
        PeriodTotals(
            key = key,
            date = date,
            facts = entries.fold(NutritionFacts.Empty) { acc, entry -> acc + entry.nutritionFacts },
            entryCount = entries.size,
            eatenCount = entries.count { it.isEaten },
            mealId = mealId,
        )

    private fun bucketKey(date: LocalDate, grouping: TotalsGrouping): String =
        when (grouping) {
            TotalsGrouping.Day -> date.toString()
            TotalsGrouping.Weekday -> date.dayOfWeek.isoDayNumber.toString().padStart(2, '0')
            TotalsGrouping.Week -> {
                // Monday of that week: sorts correctly and needs no ISO week arithmetic.
                val monday =
                    LocalDate.fromEpochDays(date.toEpochDays() - (date.dayOfWeek.isoDayNumber - 1))
                monday.toString()
            }
            // "2026-08-26" -> "2026-08". Slicing the ISO form dodges the month-accessor rename.
            TotalsGrouping.Month -> date.toString().take(7)
            // Day/Meal ya se resuelven antes de llegar aqui, en invoke().
            TotalsGrouping.Meal -> error("Meal grouping is handled in invoke(), not here")
        }

    private fun generateDays(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to < from) return emptyList()
        val days = mutableListOf<LocalDate>()
        var cursor = from.toEpochDays()
        val last = to.toEpochDays()
        while (cursor <= last) {
            days.add(LocalDate.fromEpochDays(cursor))
            cursor += 1
        }
        return days
    }
}
