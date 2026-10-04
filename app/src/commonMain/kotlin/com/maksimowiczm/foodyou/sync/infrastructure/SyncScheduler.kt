package com.maksimowiczm.foodyou.sync.infrastructure

import androidx.room.RoomDatabase
import com.maksimowiczm.foodyou.sync.domain.SyncApi
import com.maksimowiczm.foodyou.sync.domain.SyncConfigRepository
import com.maksimowiczm.foodyou.sync.domain.SyncEvent
import com.maksimowiczm.foodyou.sync.domain.SyncedGoals
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Syncs while the app is closed. Platform-specific (WorkManager on Android). */
interface BackgroundSync {
    /** Keeps (or cancels) the periodic sync while the app is closed. */
    fun setPeriodic(enabled: Boolean)

    /** A sync as soon as there is network, even if the app is being closed. */
    fun syncSoon()
}

/**
 * When to sync, so the app never has to wait for it:
 *
 * - a couple of seconds after the diary changes (several quick ticks go together);
 * - while the app is in the foreground, the moment the server says another device changed
 *   something (a live connection), with a sync every 30 seconds only while that connection is
 *   down, and every 5 minutes as a safety net while it is up;
 * - when the app goes to the background, handed to the system so it finishes even if closed;
 * - every 15 minutes while closed, as often as Android allows.
 */
@OptIn(FlowPreview::class)
internal class SyncScheduler(
    private val engine: SyncEngine,
    private val configRepository: SyncConfigRepository,
    private val api: SyncApi,
    private val database: RoomDatabase,
    private val background: BackgroundSync,
    private val scope: CoroutineScope,
    private val goals: SyncedGoals? = null,
) {
    private var started = false
    private var foreground: Job? = null

    fun start() {
        if (started) return
        started = true

        scope.launch {
            configRepository
                .observe()
                .map { it?.enabled == true }
                .distinctUntilChanged()
                .collect { enabled ->
                    background.setPeriodic(enabled)
                    if (enabled) engine.sync()
                }
        }

        scope.launch {
            database.invalidationTracker
                .createFlow(*WATCHED_TABLES)
                .drop(1) // el estado inicial no es un cambio
                .debounce(CHANGE_DEBOUNCE_MS)
                .collect {
                    // Lo que escribe la propia sincronización también avisa aquí; solo se
                    // sincroniza si hay cambios locales de verdad por mandar.
                    if (engine.pendingChanges() > 0) engine.sync()
                }
        }

        // Las metas no son tablas: avisan ellas de sus cambios.
        goals?.let { synced ->
            scope.launch {
                synced.changes.drop(1).debounce(CHANGE_DEBOUNCE_MS).collect {
                    engine.goalsChanged()
                    if (engine.pendingChanges() > 0) engine.sync()
                }
            }
        }
    }

    /** True while the live connection is open: polling can then relax. */
    @kotlin.concurrent.Volatile private var live = false

    /** The app came to the foreground. */
    fun onForeground() {
        foreground?.cancel()
        foreground =
            scope.launch {
                launch { liveConnection() }
                var sinceFull = 0L
                while (isActive) {
                    if (!live || sinceFull >= LIVE_SAFETY_INTERVAL_MS) {
                        engine.sync()
                        sinceFull = 0
                    }
                    delay(FOREGROUND_INTERVAL_MS)
                    sinceFull += FOREGROUND_INTERVAL_MS
                }
            }
    }

    /** Keeps the live connection open while in the foreground, reconnecting with a growing wait. */
    private suspend fun liveConnection() {
        var wait = MIN_RECONNECT_MS
        while (currentCoroutineContext().isActive) {
            val config = configRepository.observe().first()
            if (config == null || !config.enabled) {
                live = false
                delay(FOREGROUND_INTERVAL_MS)
                continue
            }
            try {
                api.events(config).collect { event ->
                    when (event) {
                        SyncEvent.Ready -> {
                            live = true
                            wait = MIN_RECONNECT_MS
                        }
                        SyncEvent.Changed -> engine.sync()
                    }
                }
            } catch (e: CancellationException) {
                live = false
                throw e
            } catch (_: Exception) {
                // Sin red, servidor reiniciado, el móvil cambió de wifi a datos... se reintenta.
            }
            live = false
            delay(wait)
            wait = (wait * 2).coerceAtMost(MAX_RECONNECT_MS)
        }
    }

    /** The app went to the background (or is being closed). */
    fun onBackground() {
        foreground?.cancel()
        foreground = null
        background.syncSoon()
    }

    suspend fun syncNow() = engine.sync()

    private companion object {
        val WATCHED_TABLES =
            arrayOf(
                "Meal",
                "Measurement",
                "ManualDiaryEntry",
                "ManualDiaryEntryIngredient",
                "Recipe",
                "RecipeIngredient",
                "AssistantMemory",
            )
        const val CHANGE_DEBOUNCE_MS = 2_000L
        const val FOREGROUND_INTERVAL_MS = 30_000L
        const val LIVE_SAFETY_INTERVAL_MS = 5 * 60_000L
        const val MIN_RECONNECT_MS = 2_000L
        const val MAX_RECONNECT_MS = 60_000L
    }
}
