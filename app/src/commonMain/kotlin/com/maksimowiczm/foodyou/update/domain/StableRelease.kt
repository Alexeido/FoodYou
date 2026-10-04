package com.maksimowiczm.foodyou.update.domain

import kotlinx.serialization.Serializable

/**
 * The version marked as stable on the update server, as `GET /stable` returns it.
 *
 * [build] is the fork's build number (v39 -> 39), the one each APK carries in
 * `BuildConfig.BUILD_NUMBER`; it alone decides whether this is newer than what is installed.
 */
@Serializable
data class StableRelease(
    val build: Int,
    /** The version name to show ("4.0.0"); older servers don't send it, then the build is shown. */
    val version: String? = null,
    val title: String? = null,
    val notes: String? = null,
    val imageUrl: String? = null,
    val apkUrl: String,
    /** Hex SHA-256 of the APK. The download is thrown away if it does not match. */
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val published: String? = null,
)
