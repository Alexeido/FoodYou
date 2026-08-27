package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * C5 - nutrition totals over a range, bucketed.
 *
 * Aggregating in Kotlin over the domain entries rather than in SQL is deliberate: the weight of an
 * entry depends on its measurement type and the food's serving and package weights, and that logic
 * already exists and is exercised everywhere else in the app. Reimplementing it in SQL would make
 * the assistant's numbers drift from the ones on screen, which is the one failure mode nobody would
 * notice.
 */
class DailyTotalsUseCase(private val repository: FoodDiaryEntryRepository) {

    suspend operator fun invoke(
        from: LocalDate,
        to: LocalDate,
        grouping: TotalsGrouping = TotalsGrouping.Day,
        onlyEaten: Boolean = false,
    ): List<PeriodTotals> {
        val entries = repository.observeRange(from, to).first()
        val considered = if (onlyEaten) entries.filter { it.isEaten } else entries

        if (grouping == TotalsGrouping.Day) {
            // Every day in the range appears, including the empty ones: "which days did I go over"
            // has to be able to see a day with nothing logged.
            return generateDays(from, to).map { day ->
                val ofDay = considered.filter { it.date == day }
                totals(key = day.toString(), date = day, entries = ofDay)
            }
        }

        val buckets = considered.groupBy { entry -> bucketKey(entry.date, grouping) }
        return buckets.entries
            .sortedBy { it.key }
            .map { (key, ofBucket) -> totals(key = key, date = null, entries = ofBucket) }
    }

    private fun totals(key: String, date: LocalDate?, entries: List<FoodDiaryEntry>) =
        PeriodTotals(
            key = key,
            date = date,
            facts = entries.fold(NutritionFacts.Empty) { acc, entry -> acc + entry.nutritionFacts },
            entryCount = entries.size,
            eatenCount = entries.count { it.isEaten },
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
