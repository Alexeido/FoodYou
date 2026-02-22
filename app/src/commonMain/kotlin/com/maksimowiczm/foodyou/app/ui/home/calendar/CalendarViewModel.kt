package com.maksimowiczm.foodyou.app.ui.home.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.ManualDiaryEntryDao
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.MeasurementDao
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import kotlin.math.abs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

internal class CalendarViewModel(
    private val calendarDayStatusUseCase: CalendarDayStatusUseCase,
    private val goalsRepository: GoalsRepository,
    private val measurementDao: MeasurementDao,
    private val manualDiaryEntryDao: ManualDiaryEntryDao,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val today: LocalDate = dateProvider.now().date

    // Days that have any diary entries in the past year — used for purple backfill on old days
    private val activeDays: StateFlow<Set<LocalDate>> =
        combine(
            measurementDao.observeActiveDays(
                from = today.minus(365, DateTimeUnit.DAY).toEpochDays(),
                to = today.toEpochDays(),
            ),
            manualDiaryEntryDao.observeActiveDays(
                from = today.minus(365, DateTimeUnit.DAY).toEpochDays(),
                to = today.toEpochDays(),
            ),
        ) { food, manual ->
            (food + manual).map { LocalDate.fromEpochDays(it.toInt()) }.toSet()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = emptySet(),
        )

    /**
     * Calorie-based day statuses for a 90-day past window (today included). Future dates are never
     * colored. Days with entries outside this window are shown as [DayStatus.OffGoal].
     */
    val dayStatuses: StateFlow<Map<LocalDate, DayStatus>> =
        run {
            // Observe 90 days past + today; future dates never get a dot
            val window = (-90..0).map { today.plus(it, DateTimeUnit.DAY) }
            val statusFlows =
                window.map { date ->
                    combine(
                        calendarDayStatusUseCase.observeTotalKcal(date),
                        goalsRepository.observeDailyGoals(date),
                    ) { kcal, goal ->
                        val goalKcal = goal[NutritionFactsField.Energy]
                        date to
                            when {
                                kcal <= 0.0 -> DayStatus.None
                                goalKcal <= 0.0 ||
                                    abs(kcal - goalKcal) / goalKcal <= 0.10 -> DayStatus.NearGoal
                                else -> DayStatus.OffGoal
                            }
                    }
                }
            combine(statusFlows) { it.toMap() }
                .combine(activeDays) { colorMap, active ->
                    val result = colorMap.toMutableMap()
                    // Days with entries outside the calorie window (past only) → OffGoal dot
                    active.forEach { date ->
                        if (date <= today && (date !in result || result[date] == DayStatus.None)) {
                            result[date] = DayStatus.OffGoal
                        }
                    }
                    result.filterValues { it != DayStatus.None }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(2_000),
                    initialValue = emptyMap(),
                )
        }

    /** Number of consecutive green (NearGoal) days ending today (or yesterday if today isn't green yet). */
    val streak: StateFlow<Int> =
        dayStatuses
            .map { statuses ->
                val nearGoalDays = statuses.filterValues { it == DayStatus.NearGoal }.keys
                var count = 0
                var current = today
                // If today isn't green yet, start counting from yesterday
                if (current !in nearGoalDays) current = current.minus(1, DateTimeUnit.DAY)
                while (current in nearGoalDays) {
                    count++
                    current = current.minus(1, DateTimeUnit.DAY)
                }
                count
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = 0,
            )
}
