package com.maksimowiczm.foodyou.update

import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.update.domain.DismissedUpdateStore
import com.maksimowiczm.foodyou.update.domain.StableRelease
import com.maksimowiczm.foodyou.update.domain.UpdateController
import com.maksimowiczm.foodyou.update.domain.UpdateFailure
import com.maksimowiczm.foodyou.update.domain.UpdateInstaller
import com.maksimowiczm.foodyou.update.domain.UpdateSource
import com.maksimowiczm.foodyou.update.domain.UpdateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * The update flow without Android: what the app offers on launch, what "later" remembers, and
 * how it gets from the download to the system installer.
 */
class UpdateControllerTest {

    private class FakeSource(var release: StableRelease?, var fails: Boolean = false) : UpdateSource {
        var checks = 0

        override suspend fun stable(): StableRelease? {
            checks++
            if (fails) error("sin red")
            return release
        }

        override suspend fun image(url: String): ByteArray? = byteArrayOf(1, 2, 3)
    }

    private class FakeInstaller(var allowed: Boolean = true, var downloadFails: Boolean = false) :
        UpdateInstaller {
        val installed = mutableListOf<String>()
        var permissionScreens = 0

        override suspend fun download(release: StableRelease, onProgress: (Float?) -> Unit): String {
            onProgress(0.5f)
            if (downloadFails) error("cortada")
            onProgress(1f)
            return "/cache/updates/FoodYou-v${release.build}.apk"
        }

        override fun canInstall() = allowed

        override fun openInstallPermissionSettings() {
            permissionScreens++
        }

        override fun install(file: String) {
            installed += file
        }
    }

    private class MemoryDismissed : DismissedUpdateStore {
        var value: Int? = null

        override suspend fun get() = value

        override suspend fun set(build: Int) {
            value = build
        }
    }

    private object SilentLogger : Logger {
        override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private fun release(build: Int) =
        StableRelease(
            build = build,
            title = "Recetas mejores",
            imageUrl = "https://example.test/img.jpg",
            apkUrl = "https://example.test/stable/apk",
        )

    private fun TestScope.controller(
        source: UpdateSource,
        installer: UpdateInstaller = FakeInstaller(),
        dismissed: DismissedUpdateStore = MemoryDismissed(),
        currentBuild: Int = 38,
    ) = UpdateController(source, installer, dismissed, currentBuild, this, SilentLogger)

    @Test
    fun aNewerStableIsOfferedOnLaunchWithItsPicture() = runTest {
        val c = controller(FakeSource(release(39)))
        c.checkOnLaunch()
        advanceUntilIdle()

        val state = assertIs<UpdateState.Available>(c.state.value)
        assertEquals(39, state.release.build)
        assertEquals(3, state.image?.size)
    }

    @Test
    fun theSameOrAnOlderStableIsNotOffered() = runTest {
        for (build in listOf(38, 30)) {
            val c = controller(FakeSource(release(build)))
            c.checkOnLaunch()
            advanceUntilIdle()
            assertEquals(UpdateState.Idle, c.state.value)
        }
    }

    @Test
    fun launchChecksOnceAndStaysQuietWhenTheServerIsDown() = runTest {
        val source = FakeSource(release(39), fails = true)
        val c = controller(source)
        c.checkOnLaunch()
        c.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(1, source.checks)
        assertEquals(UpdateState.Idle, c.state.value)
    }

    @Test
    fun laterIsRememberedOnLaunchButNotWhenAskingFromSettings() = runTest {
        val dismissed = MemoryDismissed()
        val first = controller(FakeSource(release(39)), dismissed = dismissed)
        first.checkOnLaunch()
        advanceUntilIdle()
        first.later()
        advanceUntilIdle()
        assertEquals(39, dismissed.value)

        // Al abrir la app otra vez, esa versión ya no se ofrece...
        val again = controller(FakeSource(release(39)), dismissed = dismissed)
        again.checkOnLaunch()
        advanceUntilIdle()
        assertEquals(UpdateState.Idle, again.state.value)

        // ...pero si la persona la busca a propósito, sí.
        again.checkNow()
        advanceUntilIdle()
        assertIs<UpdateState.Available>(again.state.value)

        // Y una versión aún más nueva vuelve a avisar sola.
        val newer = controller(FakeSource(release(40)), dismissed = dismissed)
        newer.checkOnLaunch()
        advanceUntilIdle()
        assertIs<UpdateState.Available>(newer.state.value)
    }

    @Test
    fun checkingFromSettingsAlwaysAnswers() = runTest {
        val upToDate = controller(FakeSource(release(38)))
        upToDate.checkNow()
        advanceUntilIdle()
        assertEquals(UpdateState.UpToDate(38), upToDate.state.value)

        val offline = controller(FakeSource(release(39), fails = true))
        offline.checkNow()
        advanceUntilIdle()
        assertEquals(UpdateState.Failed(UpdateFailure.Check, null), offline.state.value)
    }

    @Test
    fun updatingDownloadsAndOpensTheInstaller() = runTest {
        val installer = FakeInstaller()
        val c = controller(FakeSource(release(39)), installer)
        c.checkNow()
        advanceUntilIdle()
        c.update()
        advanceUntilIdle()

        assertEquals(listOf("/cache/updates/FoodYou-v39.apk"), installer.installed)
        assertIs<UpdateState.Installing>(c.state.value)

        // Si se cerró el instalador, se reabre sin volver a descargar.
        c.installAgain()
        assertEquals(2, installer.installed.size)
    }

    @Test
    fun withoutPermissionItAsksForItThenInstalls() = runTest {
        val installer = FakeInstaller(allowed = false)
        val c = controller(FakeSource(release(39)), installer)
        c.checkNow()
        advanceUntilIdle()
        c.update()
        advanceUntilIdle()

        assertIs<UpdateState.NeedsPermission>(c.state.value)
        assertEquals(1, installer.permissionScreens)
        assertEquals(emptyList(), installer.installed)

        installer.allowed = true
        c.installAgain()
        assertEquals(1, installer.installed.size)
    }

    @Test
    fun aBrokenDownloadCanBeRetried() = runTest {
        val installer = FakeInstaller(downloadFails = true)
        val c = controller(FakeSource(release(39)), installer)
        c.checkNow()
        advanceUntilIdle()
        c.update()
        advanceUntilIdle()

        val failed = assertIs<UpdateState.Failed>(c.state.value)
        assertEquals(UpdateFailure.Download, failed.reason)

        installer.downloadFails = false
        c.update()
        advanceUntilIdle()
        assertIs<UpdateState.Installing>(c.state.value)
    }
}
