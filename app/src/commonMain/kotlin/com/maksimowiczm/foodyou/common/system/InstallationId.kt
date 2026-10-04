package com.maksimowiczm.foodyou.common.system

/**
 * An opaque id for this install of the app on this device, sent to the user's own custom food
 * source so its owner can tell how many phones use one account.
 *
 * Never the raw hardware id: implementations hash it, so the server only ever sees a value that
 * means nothing outside it. Null when the platform cannot give a stable one.
 */
fun interface InstallationId {
    fun get(): String?
}
