package com.maksimowiczm.foodyou.update.infrastructure

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.maksimowiczm.foodyou.update.domain.StableRelease
import com.maksimowiczm.foodyou.update.domain.UpdateInstaller
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind

actual fun Module.updateInstallerDefinition() {
    singleOf(::AndroidUpdateInstaller).bind<UpdateInstaller>()
}

/**
 * Downloads the APK into the app's cache and opens the system installer on it.
 *
 * The file lives in `cache/updates/`, which the FileProvider exposes (res/xml/file_paths.xml):
 * the installer is another app and can only read it through a content URI. Earlier downloads are
 * deleted first, so the cache never holds more than one APK.
 */
internal class AndroidUpdateInstaller(private val context: Context) : UpdateInstaller {

    override suspend fun download(release: StableRelease, onProgress: (Float?) -> Unit): String =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "FoodYou-v${release.build}.apk")

            val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    error("Download answered ${connection.responseCode}")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                var lastReported = -1
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (total != null) {
                                // Un aviso por cada 1 %: más solo recompone la pantalla en balde.
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastReported) {
                                    lastReported = percent
                                    onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                                }
                            } else {
                                onProgress(null)
                            }
                        }
                    }
                }
                val expected = release.sha256?.lowercase()
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (expected != null && expected != actual) {
                    file.delete()
                    error("The downloaded APK does not match its fingerprint")
                }
            } catch (e: Throwable) {
                file.delete()
                throw e
            } finally {
                connection.disconnect()
            }
            file.absolutePath
        }

    override fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                )
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun install(file: String) {
        val uri =
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file))
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
