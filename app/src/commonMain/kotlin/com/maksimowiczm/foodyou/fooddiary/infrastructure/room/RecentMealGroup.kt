package com.maksimowiczm.foodyou.fooddiary.infrastructure.room

/** Projection for [MeasurementDao.observeRecentMealGroups]: one logged meal on one day. */
data class RecentMealGroup(val mealId: Long, val epochDay: Long, val lastCreatedAt: Long)
