package com.maksimowiczm.foodyou.assistant.infrastructure

import com.maksimowiczm.foodyou.assistant.domain.AssistantMemoryRepository
import com.maksimowiczm.foodyou.assistant.infrastructure.room.AssistantDao
import com.maksimowiczm.foodyou.assistant.infrastructure.room.AssistantMemoryEntity
import kotlin.time.Clock

internal class RoomAssistantMemoryRepository(private val dao: AssistantDao) :
    AssistantMemoryRepository {

    override suspend fun all(): Map<String, String> =
        dao.allMemory().associate { it.key to it.value }

    override suspend fun put(key: String, value: String) {
        dao.putMemory(
            AssistantMemoryEntity(
                key = key,
                value = value,
                updatedAt = Clock.System.now().epochSeconds,
            )
        )
    }

    override suspend fun remove(key: String) = dao.deleteMemory(key)
}
