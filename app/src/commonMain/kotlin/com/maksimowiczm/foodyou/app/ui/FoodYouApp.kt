package com.maksimowiczm.foodyou.app.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.navigation.FoodYouAppNavHost
import com.maksimowiczm.foodyou.app.ui.changelog.AppUpdateChangelogModalBottomSheet
import com.maksimowiczm.foodyou.app.ui.changelog.PreviewReleaseDialog
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatterProvider
import com.maksimowiczm.foodyou.app.ui.common.utility.NutrientsOrderProvider
import com.maksimowiczm.foodyou.app.ui.language.TranslationWarningStartupDialog
import com.maksimowiczm.foodyou.app.ui.onboarding.Onboarding
import com.maksimowiczm.foodyou.app.ui.theme.FoodYouTheme
import com.maksimowiczm.foodyou.app.ui.update.UpdateHost
import androidx.lifecycle.compose.LifecycleStartEffect
import com.maksimowiczm.foodyou.sync.infrastructure.SyncScheduler
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoodYouApp(onDatabaseBackup: () -> Unit) {
    val viewModel: AppViewModel = koinViewModel()
    val nutrientsOrder by viewModel.nutrientsOrder.collectAsStateWithLifecycle()
    val onboardingFinished by viewModel.onboardingFinished.collectAsStateWithLifecycle()
    val energyFormatter by viewModel.energyFormatter.collectAsStateWithLifecycle()

    // La sincronización arranca con la app y sigue su ciclo de vida: cada 30 s mientras está a la
    // vista, y un último envío al pasar a segundo plano.
    val syncScheduler: SyncScheduler = koinInject()
    LaunchedEffect(syncScheduler) { syncScheduler.start() }
    LifecycleStartEffect(syncScheduler) {
        syncScheduler.onForeground()
        onStopOrDispose { syncScheduler.onBackground() }
    }

    NutrientsOrderProvider(nutrientsOrder) {
        EnergyFormatterProvider(energyFormatter) {
            FoodYouTheme {
                PreviewReleaseDialog()
                TranslationWarningStartupDialog()

                if (onboardingFinished) {
                    Surface {
                        FoodYouAppNavHost(onDatabaseBackup)
                        AppUpdateChangelogModalBottomSheet()
                        UpdateHost()
                    }
                } else {
                    Onboarding(onFinish = viewModel::finishOnboarding)
                }
            }
        }
    }
}
