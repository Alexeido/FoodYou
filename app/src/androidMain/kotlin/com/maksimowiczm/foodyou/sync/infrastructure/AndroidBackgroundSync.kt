package com.maksimowiczm.foodyou.sync.infrastructure

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind

actual fun Module.backgroundSyncDefinition() {
    singleOf(::AndroidBackgroundSync).bind<BackgroundSync>()
}

/**
 * WorkManager runs the sync when the app is not open: once right after it is closed, and every
 * 15 minutes (Android's minimum) while there is network. Neither needs the app to be running.
 */
internal class AndroidBackgroundSync(private val context: Context) : BackgroundSync {

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun setPeriodic(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(network)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(PERIODIC)
            workManager.cancelUniqueWork(SOON)
        }
    }

    override fun syncSoon() {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                SOON,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build(),
            )
    }

    private companion object {
        const val PERIODIC = "sync-periodic"
        const val SOON = "sync-soon"
    }
}

internal class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {

    private val engine: SyncEngine by inject()

    override suspend fun doWork(): Result =
        when (val outcome = engine.sync()) {
            // Sin red o el servidor caído: que WorkManager lo reintente con su espera.
            is SyncOutcome.Failed ->
                if (outcome.reason == SyncFailure.Network || outcome.reason == SyncFailure.Server) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            else -> Result.success()
        }
}
