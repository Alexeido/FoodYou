package com.maksimowiczm.foodyou.assistant.domain

import com.maksimowiczm.foodyou.assistant.infrastructure.openai.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** What the chat shows: one turn as the screen renders it, not as the API sees it. */
@Serializable
data class ChatTurn(
    val id: Long,
    val role: Role,
    val text: String,
    /** Tool names used producing this answer, for the collapsed "what it did" line. */
    val usedTools: List<String> = emptyList(),
    /** Set when this turn changed the diary, so the undo button can live beside it. */
    val changeId: Long? = null,
    val undone: Boolean = false,
    /**
     * The attached photo, base64-encoded, so the thread can show what was actually sent.
     *
     * Kept beside the text rather than only as a boolean flag: a conversation where you scroll back
     * and cannot see which photo you sent is impossible to reason about.
     */
    val imageBase64: String? = null,
    val error: ChatError? = null,
) {
    @Serializable
    enum class Role {
        User,
        Assistant,
    }
}

/** Errors the chat distinguishes, because "the key was rejected" is not "I do not know". */
@Serializable
enum class ChatError {
    Unauthorized,
    /** Offers a "Continue" button - a stalled reasoning chain usually just needs a nudge forward. */
    Timeout,
    Other,
}

/**
 * The live conversation, kept outside the ViewModel so it survives leaving the screen.
 *
 * Planning a week means going out to look at the diary and coming back, and losing the thread on the
 * way would make that flow unusable. Holds exactly one conversation at a time - the one currently
 * open - but which one that is can change: [restore] swaps in a different, previously saved
 * conversation loaded from the history screen, and [conversationId] is what the change journal
 * stamps onto every diary edit so the Photoshop-style history panel can show only this thread's own
 * changes. Persisting the conversation itself to disk is the view model's job, not this class's -
 * this is just the in-memory shape the screen renders from.
 */
class ConversationStore {

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    /** Null for a conversation that has not been saved yet - the very first message of a new one. */
    private val _conversationId = MutableStateFlow<Long?>(null)
    val conversationId: StateFlow<Long?> = _conversationId.asStateFlow()

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

    /** Assigns the id a brand-new conversation was just given when its first turn was saved. */
    fun assignConversationId(id: Long) {
        _conversationId.value = id
    }

    /** Swaps in a conversation loaded from storage, replacing everything currently held. */
    fun restore(id: Long, turns: List<ChatTurn>, apiMessages: List<Message>) {
        _turns.value = turns
        this.apiMessages.clear()
        this.apiMessages.addAll(apiMessages)
        nextId = (turns.maxOfOrNull { it.id } ?: 0) + 1
        _conversationId.value = id
    }

    fun clear() {
        _turns.value = emptyList()
        apiMessages.clear()
        nextId = 1
        _conversationId.value = null
    }

    val isEmpty: Boolean
        get() = _turns.value.isEmpty()
}
