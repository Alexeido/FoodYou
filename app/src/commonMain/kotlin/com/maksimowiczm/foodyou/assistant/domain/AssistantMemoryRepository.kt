package com.maksimowiczm.foodyou.assistant.domain

/**
 * The handful of facts the user has told the assistant about themselves.
 *
 * Not a body-measurement domain and not meant to become one: four loose values so the assistant
 * stops asking for your weight in every new conversation. It becomes redundant the day an app that
 * really tracks this exists.
 */
interface AssistantMemoryRepository {
    suspend fun all(): Map<String, String>

    suspend fun put(key: String, value: String)

    suspend fun remove(key: String)
}
