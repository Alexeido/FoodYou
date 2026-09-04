package com.maksimowiczm.foodyou.assistant.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "AssistantConversation", indices = [Index(value = ["updatedAt"])])
data class AssistantConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** The first message the person sent, truncated - null only for the instant before it. */
    val title: String?,

    /** Epoch seconds. */
    val createdAt: Long,
    val updatedAt: Long,

    /** Serialized `List<ChatTurn>`, what the screen renders. */
    val turnsJson: String,

    /** Serialized `List<Message>`, what actually gets replayed to the model. */
    val apiMessagesJson: String,
)
