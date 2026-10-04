package com.maksimowiczm.foodyou.update.domain

import com.maksimowiczm.foodyou.common.log.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Remembers the release the person put off with "later", so it does not nag on every launch. */
interface DismissedUpdateStore {
    suspend fun get(): Int?

    suspend fun set(build: Int)
}

/** What the update dialog is showing. */
sealed interface UpdateState {
    /** Nothing to show. */
    data object Idle : UpdateState

    /** A check the person asked for, from Settings, is running. */
    data object Checking : UpdateState

    /** The person asked and there is nothing newer. */
    data class UpToDate(val build: Int) : UpdateState

    data class Available(val release: StableRelease, val image: ByteArray?) : UpdateState

    data class Downloading(val release: StableRelease, val image: ByteArray?, val progress: Float?) :
        UpdateState

    /** Downloaded, but Android has not let this app install apps yet. */
    data class NeedsPermission(val release: StableRelease, val file: String) : UpdateState

    /**
     * The system installer has been opened. If the person backs out of it, this stays so they
     * can try again without downloading a second time.
     */
    data class Installing(val release: StableRelease, val file: String) : UpdateState

    data class Failed(val reason: UpdateFailure, val release: StableRelease?) : UpdateState
}

enum class UpdateFailure {
    /** The server could not be reached when the person asked. */
    Check,

    /** The APK did not download whole, or did not match its fingerprint. */
    Download,
}

/**
 * The whole update flow, independent of any screen: the dialog at the root of the app and the
 * button in Settings both drive this one instance, so a download started from one carries on
 * whatever screen is open.
 */
class UpdateController(
    private val source: UpdateSource,
    private val installer: UpdateInstaller,
    private val dismissed: DismissedUpdateStore,
    private val currentBuild: Int,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null
    private var launchChecked = false

    /**
     * The quiet check when the app opens: once per process, says nothing if there is no update or
     * the server cannot be reached, and skips the release the person already put off.
     */
    fun checkOnLaunch() {
        if (launchChecked) return
        launchChecked = true
        launchFlow {
            val release =
                try {
                    source.stable()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(TAG, e) { "Update check failed" }
                    null
                }
            if (release != null && release.build > currentBuild && release.build != dismissed.get()) {
                _state.value = UpdateState.Available(release, release.imageUrl?.let { source.image(it) })
            }
        }
    }

    /** The "check for updates" button: always answers, and ignores an earlier "later". */
    fun checkNow() {
        _state.value = UpdateState.Checking
        launchFlow {
            _state.value =
                try {
                    val release = source.stable()
                    if (release != null && release.build > currentBuild) {
                        UpdateState.Available(release, release.imageUrl?.let { source.image(it) })
                    } else {
                        UpdateState.UpToDate(currentBuild)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(TAG, e) { "Update check failed" }
                    UpdateState.Failed(UpdateFailure.Check, null)
                }
        }
    }

    /** "Later": this release is not offered again on launch (Settings can still find it). */
    fun later() {
        val current = _state.value
        if (current is UpdateState.Available) {
            scope.launch { dismissed.set(current.release.build) }
        }
        close()
    }

    fun close() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    fun update() {
        val (release, image) =
            when (val current = _state.value) {
                is UpdateState.Available -> current.release to current.image
                is UpdateState.Failed -> (current.release ?: return) to null
                else -> return
            }
        _state.value = UpdateState.Downloading(release, image, null)
        launchFlow {
            val file =
                try {
                    installer.download(release) { progress ->
                        _state.value = UpdateState.Downloading(release, image, progress)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(TAG, e) { "Update download failed" }
                    _state.value = UpdateState.Failed(UpdateFailure.Download, release)
                    return@launchFlow
                }
            install(release, file)
        }
    }

    /** After granting the permission, or after backing out of the installer. */
    fun installAgain() {
        when (val current = _state.value) {
            is UpdateState.NeedsPermission -> install(current.release, current.file)
            is UpdateState.Installing -> install(current.release, current.file)
            else -> Unit
        }
    }

    fun openPermissionSettings() = installer.openInstallPermissionSettings()

    private fun install(release: StableRelease, file: String) {
        if (!installer.canInstall()) {
            _state.value = UpdateState.NeedsPermission(release, file)
            installer.openInstallPermissionSettings()
            return
        }
        _state.value = UpdateState.Installing(release, file)
        installer.install(file)
    }

    private fun launchFlow(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    private companion object {
        const val TAG = "UpdateController"
    }
}
