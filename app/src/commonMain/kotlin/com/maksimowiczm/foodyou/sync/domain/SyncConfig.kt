package com.maksimowiczm.foodyou.sync.domain

import kotlinx.coroutines.flow.Flow

/**
 * Where and as whom to sync. Independent from the custom food database: its own address and its
 * own account, so sync can point somewhere else entirely - or be used without any food database.
 */
data class SyncConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val enabled: Boolean,
) {
    /** The address without a trailing slash, so paths can be appended safely. */
    val baseUrl: String
        get() = serverUrl.trim().trimEnd('/')
}

interface SyncConfigRepository {
    fun observe(): Flow<SyncConfig?>

    suspend fun save(config: SyncConfig)

    suspend fun clear()
}
