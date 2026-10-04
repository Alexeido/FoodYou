package com.maksimowiczm.foodyou.update.domain

/** Where the stable version is published. */
interface UpdateSource {
    /** The current stable release, or null when none is published. Throws on network errors. */
    suspend fun stable(): StableRelease?

    /** The release's picture, if it has one and it can be fetched; never throws. */
    suspend fun image(url: String): ByteArray?
}

/** Downloads an APK and hands it to the system installer. Platform-specific. */
interface UpdateInstaller {
    /**
     * Downloads [release] and checks its fingerprint.
     *
     * @param onProgress from 0 to 1, or null while the size is unknown.
     * @return a handle to the downloaded file, for [install].
     */
    suspend fun download(release: StableRelease, onProgress: (Float?) -> Unit): String

    /** Whether this app may open the installer (Android asks once per app). */
    fun canInstall(): Boolean

    /** Opens the system screen where that permission is granted. */
    fun openInstallPermissionSettings()

    /** Opens the system installer for a file returned by [download]. */
    fun install(file: String)
}
