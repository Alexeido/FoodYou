package com.maksimowiczm.foodyou.app.ui.assistant.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.assistant.domain.AgentEvent
import com.maksimowiczm.foodyou.assistant.domain.AgentLoop
import com.maksimowiczm.foodyou.assistant.domain.AssistantPreferences
import com.maksimowiczm.foodyou.assistant.domain.ChatError
import com.maksimowiczm.foodyou.assistant.domain.ChatTurn
import com.maksimowiczm.foodyou.assistant.domain.ConversationStore
import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.Message
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class AssistantChatViewModel(
    private val agentLoop: AgentLoop,
    private val conversation: ConversationStore,
    private val journal: ChangeJournal,
    private val preferences: AssistantPreferences,
    private val dateProvider: DateProvider,
) : ViewModel() {

    val turns: StateFlow<List<ChatTurn>> = conversation.turns

    val model: StateFlow<String> =
        preferences
            .observe()
            .map { it.model }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), "")

    val supportsVision: StateFlow<Boolean> =
        preferences
            .observe()
            .map { it.supportsVision }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), false)

    /**
     * The ephemeral status line. Null when nothing is running.
     *
     * It disappears when the turn ends and never becomes part of the thread: the pad and the tool
     * calls are the assistant's business, and the person asking only wants the result.
     */
    private val _working = MutableStateFlow<String?>(null)
    val working: StateFlow<String?> = _working.asStateFlow()

    private var runningJob: Job? = null

    val isRunning: Boolean
        get() = runningJob?.isActive == true

    fun send(text: String, imageDataUri: String? = null) {
        if (text.isBlank() || isRunning) return
        val hasImage = imageDataUri != null

        conversation.addTurn(
            ChatTurn(id = 0, role = ChatTurn.Role.User, text = text, hasImage = hasImage)
        )

        val userMessage =
            if (hasImage) {
                // Partes de texto e imagen, con la imagen en linea como data URI. Es la forma
                // que se verifico contra la API; el array solo aparece cuando hay foto.
                Message(
                    role = "user",
                    content =
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", text)
                                }
                            )
                            add(
                                buildJsonObject {
                                    put("type", "image_url")
                                    put(
                                        "image_url",
                                        buildJsonObject { put("url", imageDataUri!!) },
                                    )
                                }
                            )
                        },
                )
            } else {
                Message(role = "user", content = JsonPrimitive(text))
            }

        conversation.rememberApiMessage(userMessage)

        runningJob =
            viewModelScope.launch {
                val today = dateProvider.observeDate().first()
                val before = journal.recent(1).firstOrNull()?.id

                agentLoop
                    .run(
                        history = conversation.history.dropLast(1),
                        userMessage = userMessage,
                        today = today,
                        useVisionModel = hasImage,
                    )
                    .collect { event ->
                        when (event) {
                            is AgentEvent.Working -> _working.value = event.label

                            is AgentEvent.ToolRan -> Unit

                            is AgentEvent.Answer -> {
                                _working.value = null
                                val after = journal.recent(1).firstOrNull()
                                // Solo se ofrece deshacer si de verdad cambio algo en este turno.
                                val changeId = after?.id?.takeIf { it != before }

                                conversation.rememberApiMessage(
                                    Message(role = "assistant", content = JsonPrimitive(event.text))
                                )
                                conversation.addTurn(
                                    ChatTurn(
                                        id = 0,
                                        role = ChatTurn.Role.Assistant,
                                        text = event.text,
                                        usedTools = event.usedTools,
                                        changeId = changeId,
                                    )
                                )
                            }

                            is AgentEvent.Failed -> {
                                _working.value = null
                                conversation.addTurn(
                                    ChatTurn(
                                        id = 0,
                                        role = ChatTurn.Role.Assistant,
                                        text = event.message,
                                        error =
                                            if (event.isAuthError) ChatError.Unauthorized
                                            else ChatError.Other,
                                    )
                                )
                            }
                        }
                    }
            }
    }

    /** Undo lives beside the message that made the change, not in a snackbar that vanishes. */
    fun undo(changeId: Long) {
        viewModelScope.launch {
            journal.undo(changeId)?.let { conversation.markUndone(changeId) }
        }
    }

    fun newConversation() {
        runningJob?.cancel()
        _working.value = null
        conversation.clear()
    }

}
