package com.maksimowiczm.foodyou.assistant.domain

import com.maksimowiczm.foodyou.assistant.infrastructure.openai.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the chat shows: one turn as the screen renders it, not as the API sees it. */
data class ChatTurn(
    val id: Long,
    val role: Role,
    val text: String,
    /** Tool names used producing this answer, for the collapsed "what it did" line. */
    val usedTools: List<String> = emptyList(),
    /** Set when this turn changed the diary, so the undo button can live beside it. */
    val changeId: Long? = null,
    val undone: Boolean = false,
    val hasImage: Boolean = false,
    val error: ChatError? = null,
) {
    enum class Role {
        User,
        Assistant,
    }
}

/** Errors the chat distinguishes, because "the key was rejected" is not "I do not know". */
enum class ChatError {
    Unauthorized,
    Other,
}

/**
 * The live conversation, kept outside the ViewModel so it survives leaving the screen.
 *
 * Planning a week means going out to look at the diary and coming back, and losing the thread on the
 * way would make that flow unusable. In memory only: a conversation is worth keeping for the length
 * of a session, not forever.
 */
class ConversationStore {

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    /** The API-shaped history, which is what the loop actually replays. */
    private val apiMessages = mutableListOf<Message>()

    val history: List<Message>
        get() = apiMessages.toList()

    private var nextId = 1L

    fun addTurn(turn: ChatTurn): ChatTurn {
        val withId = turn.copy(id = nextId++)
        _turns.value = _turns.value + withId
        return withId
    }

    fun replaceLast(transform: (ChatTurn) -> ChatTurn) {
        val current = _turns.value
        if (current.isEmpty()) return
        _turns.value = current.dropLast(1) + transform(current.last())
    }

    fun markUndone(changeId: Long) {
        _turns.value =
            _turns.value.map { if (it.changeId == changeId) it.copy(undone = true) else it }
    }

    fun rememberApiMessage(message: Message) {
        apiMessages.add(message)
    }

    fun clear() {
        _turns.value = emptyList()
        apiMessages.clear()
        nextId = 1
    }

    val isEmpty: Boolean
        get() = _turns.value.isEmpty()
}
