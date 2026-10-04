package com.maksimowiczm.foodyou.common.infrastructure.system

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.maksimowiczm.foodyou.common.system.InstallationId
import java.security.MessageDigest

/**
 * Built from ANDROID_ID, which since Android 8 is already scoped to this app's signing key and
 * this device user, and survives uninstalling and reinstalling - so reinstalling does not look
 * like a new phone. It changes only on a factory reset.
 *
 * Hashed with an app-specific prefix before it leaves the phone: the server gets a stable value
 * it can count, not the device's id.
 */
internal class AndroidInstallationId(private val context: Context) : InstallationId {

    private val id: String? by lazy {
        @SuppressLint("HardwareIds")
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        raw?.takeIf { it.isNotBlank() && it != BROKEN_ANDROID_ID }?.let(::hash)
    }

    override fun get(): String? = id

    private fun hash(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("foodyou-custom-food-source:$raw".toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        /** Returned by a batch of old Android 2.2 devices; shared, so useless as an id. */
        const val BROKEN_ANDROID_ID = "9774d56d682e549c"
    }
}
