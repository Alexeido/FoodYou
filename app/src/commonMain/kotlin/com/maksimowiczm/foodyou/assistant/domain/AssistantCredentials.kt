package com.maksimowiczm.foodyou.assistant.domain

import kotlinx.coroutines.flow.Flow

/** The API key for an OpenAI-compatible endpoint. Never leaves the device unencrypted. */
data class AssistantCredentials(val apiKey: String)

/**
 * Storage for the key, encrypted at rest with the device's hardware-backed master key.
 *
 * Same shape as the custom food source credentials, and for the same reason: the key never sits in
 * plain text in preferences, and it cannot be read off a backup.
 */
interface AssistantCredentialsRepository {
    suspend fun save(credentials: AssistantCredentials)

    fun observe(): Flow<AssistantCredentials?>

    suspend fun clear()
}
