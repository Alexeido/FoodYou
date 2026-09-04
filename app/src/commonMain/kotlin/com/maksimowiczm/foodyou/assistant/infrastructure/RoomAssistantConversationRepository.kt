package com.maksimowiczm.foodyou.assistant.infrastructure

import com.maksimowiczm.foodyou.assistant.domain.AssistantConversationRepository
import com.maksimowiczm.foodyou.assistant.domain.ChatTurn
import com.maksimowiczm.foodyou.assistant.domain.ConversationSnapshot
import com.maksimowiczm.foodyou.assistant.domain.ConversationSummary
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.Message
import com.maksimowiczm.foodyou.assistant.infrastructure.room.AssistantConversationEntity
import com.maksimowiczm.foodyou.assistant.infrastructure.room.AssistantDao
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal class RoomAssistantConversationRepository(
    private val dao: AssistantDao,
    private val json: Json,
) : AssistantConversationRepository {

    override suspend fun create(title: String?): Long {
        val now = Clock.System.now().epochSeconds
        return dao.insertConversation(
            AssistantConversationEntity(
                title = title,
                createdAt = now,
                updatedAt = now,
                turnsJson = json.encodeToString(turnsSerializer, emptyList()),
                apiMessagesJson = json.encodeToString(messagesSerializer, emptyList()),
            )
        )
    }

    override suspend fun save(
        id: Long,
        title: String?,
        turns: List<ChatTurn>,
        apiMessages: List<Message>,
    ) {
        dao.updateConversation(
            id = id,
            title = title,
            updatedAt = Clock.System.now().epochSeconds,
            turnsJson = json.encodeToString(turnsSerializer, turns),
            apiMessagesJson = json.encodeToString(messagesSerializer, apiMessages),
        )
    }

    override suspend fun load(id: Long): ConversationSnapshot? {
        val entity = dao.conversationById(id) ?: return null
        return ConversationSnapshot(
            turns = json.decodeFromString(turnsSerializer, entity.turnsJson),
            apiMessages = json.decodeFromString(messagesSerializer, entity.apiMessagesJson),
        )
    }

    override fun observeAll(): Flow<List<ConversationSummary>> =
        dao.observeConversations().map { rows -> rows.map { it.toSummary() } }

    override suspend fun delete(id: Long) = dao.deleteConversation(id)

    private companion object {
        val turnsSerializer = ListSerializer(ChatTurn.serializer())
        val messagesSerializer = ListSerializer(Message.serializer())
    }
}

private fun AssistantConversationEntity.toSummary() =
    ConversationSummary(
        id = id,
        title = title,
        updatedAt =
            Instant.fromEpochSeconds(updatedAt).toLocalDateTime(TimeZone.currentSystemDefault()),
    )
