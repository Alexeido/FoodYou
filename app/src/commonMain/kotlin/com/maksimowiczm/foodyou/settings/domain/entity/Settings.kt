package com.maksimowiczm.foodyou.settings.domain.entity

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferences

data class Settings(
    val lastRememberedVersion: String?,
    val hidePreviewDialog: Boolean,
    val showTranslationWarning: Boolean,
    val nutrientsOrder: List<NutrientsOrder>,
    val secureScreen: Boolean,
    val homeCardOrder: List<HomeCard>,
    val expandGoalCard: Boolean,
    val goalsCardStyle: GoalsCardStyle,
    val goalsFigureValue: GoalsFigureValue,
    val onboardingFinished: Boolean,
    val energyFormat: EnergyFormat,
    val appLaunchInfo: AppLaunchInfo,
    /** Compilación estable para la que se pulsó "Más tarde": de esa ya no se vuelve a avisar. */
    val dismissedUpdateBuild: Int? = null,
) : UserPreferences
