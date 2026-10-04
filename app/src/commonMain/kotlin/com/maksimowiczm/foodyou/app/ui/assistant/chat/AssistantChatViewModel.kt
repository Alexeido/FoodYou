package com.maksimowiczm.foodyou.app.ui.assistant.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.assistant.domain.AgentEvent
import com.maksimowiczm.foodyou.assistant.domain.AgentLoop
import com.maksimowiczm.foodyou.assistant.domain.AssistantConversationRepository
import com.maksimowiczm.foodyou.assistant.domain.AssistantKeepAlive
import com.maksimowiczm.foodyou.assistant.domain.AssistantPreferences
import com.maksimowiczm.foodyou.assistant.domain.ChatError
import com.maksimowiczm.foodyou.assistant.domain.ChatTurn
import com.maksimowiczm.foodyou.assistant.domain.ConversationStore
import com.maksimowiczm.foodyou.assistant.domain.journal.AssistantChange
import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.Message
import com.maksimowiczm.foodyou.common.compose.image.PickedImage
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private val conversationRepository: AssistantConversationRepository,
    private val preferences: AssistantPreferences,
    private val dateProvider: DateProvider,
    private val keepAlive: AssistantKeepAlive,
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
    private var workingPump: Job? = null

    init {
        resumeLastConversation()
    }

    /**
     * Opening the assistant continues the most recent conversation instead of a blank one. The
     * store survives leaving the screen but not the app being closed, and after that every visit
     * used to start from zero. "New conversation" still gives a blank chat, and that choice sticks.
     */
    private fun resumeLastConversation() {
        if (!conversation.shouldResumeLast) return
        viewModelScope.launch {
            val last = conversationRepository.observeAll().first().firstOrNull() ?: return@launch
            val snapshot = conversationRepository.load(last.id) ?: return@launch
            // Si mientras cargaba ya se ha escrito algo, eso manda.
            if (conversation.shouldResumeLast) {
                conversation.restore(last.id, snapshot.turns, snapshot.apiMessages)
            }
        }
    }

    val isRunning: Boolean
        get() = runningJob?.isActive == true

    /** The Photoshop-style panel's list, scoped to whichever conversation is currently open. */
    private val _changeHistory = MutableStateFlow<List<AssistantChange>>(emptyList())
    val changeHistory: StateFlow<List<AssistantChange>> = _changeHistory.asStateFlow()

    fun loadChangeHistory() {
        viewModelScope.launch {
            val id = conversation.conversationId.value
            _changeHistory.value = if (id == null) emptyList() else journal.recentForConversation(id)
        }
    }

    /**
     * Jumps back to right before [changeId], undoing it and everything this conversation did after
     * it - the same move as clicking an old state in Photoshop's history panel.
     */
    fun revertTo(changeId: Long) {
        viewModelScope.launch {
            val id = conversation.conversationId.value ?: return@launch
            journal
                .recentForConversation(id)
                .filter { !it.undone && it.id >= changeId }
                .sortedByDescending { it.id }
                .forEach { change ->
                    journal.undo(change.id)
                    conversation.markUndone(change.id)
                }
            persistConversation()
            loadChangeHistory()
        }
    }

    fun send(text: String, image: PickedImage? = null) {
        if (text.isBlank() || isRunning) return
        val hasImage = image != null

        conversation.addTurn(
            ChatTurn(
                id = 0,
                role = ChatTurn.Role.User,
                text = text,
                imageBase64 = image?.base64,
            )
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
                                        buildJsonObject { put("url", image!!.dataUri) },
                                    )
                                }
                            )
                        },
                )
            } else {
                Message(role = "user", content = JsonPrimitive(text))
            }

        conversation.rememberApiMessage(userMessage)

        // Cada paso de la cadena de herramientas manda su propia etiqueta, pero algunas terminan
        // en milisegundos - sin esto, el texto cambiaba mas rapido de lo que se puede leer y daba
        // la sensacion de estar siempre en el mismo sitio. Esta cola las va soltando de una en una
        // con un hueco minimo entre ellas; en cuanto llega la respuesta de verdad se corta al
        // momento, nunca se retrasa el final por terminar de enseñar pasos ya pasados.
        workingPump?.cancel()
        val labels = Channel<String>(Channel.UNLIMITED)
        workingPump =
            viewModelScope.launch {
                for (label in labels) {
                    _working.value = label
                    delay(MIN_WORKING_VISIBLE_MS)
                }
            }

        runningJob =
            viewModelScope.launch {
                // Una fila solo nace cuando de verdad se manda un primer mensaje - una conversacion
                // abandonada sin enviar nada nunca aparece como guardada. A partir de aqui el id ya
                // esta puesto antes de que corra ninguna herramienta, para que el primer cambio que
                // haga el asistente en este hilo quede etiquetado con el.
                if (conversation.conversationId.value == null) {
                    val id = conversationRepository.create(title = text.take(60))
                    conversation.assignConversationId(id)
                }

                val today = dateProvider.observeDate().first()
                val before = journal.recent(1).firstOrNull()?.id

                // Sin esto, bloquear la pantalla o salir de la app a mitad de una cadena de
                // herramientas revienta la peticion con "Software caused connection abort": el
                // sistema cierra las conexiones de red de un proceso en segundo plano bastante
                // rapido. Se detiene siempre en el finally, tambien si el turno falla o se cancela.
                keepAlive.start()
                try {
                    agentLoop
                        .run(
                            history = conversation.history.dropLast(1),
                            userMessage = userMessage,
                            today = today,
                        )
                        .collect { event -> handleAgentEvent(event, before, labels) }
                } finally {
                    keepAlive.stop()
                }
            }
    }

    private suspend fun handleAgentEvent(event: AgentEvent, before: Long?, labels: Channel<String>) {
        when (event) {
            is AgentEvent.Working -> labels.trySend(event.label)

            is AgentEvent.ToolRan -> Unit

            is AgentEvent.Answer -> {
                workingPump?.cancel()
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
                persistConversation()
            }

            is AgentEvent.Failed -> {
                workingPump?.cancel()
                _working.value = null
                conversation.addTurn(
                    ChatTurn(
                        id = 0,
                        role = ChatTurn.Role.Assistant,
                        text = event.message,
                        error =
                            when {
                                event.isAuthError -> ChatError.Unauthorized
                                event.isTimeout -> ChatError.Timeout
                                else -> ChatError.Other
                            },
                    )
                )
                persistConversation()
            }
        }
    }

    /** Title is left null here: `save` only fills it in the first time, right at `create`. */
    private fun persistConversation() {
        val id = conversation.conversationId.value ?: return
        viewModelScope.launch {
            conversationRepository.save(
                id = id,
                title = null,
                turns = conversation.turns.value,
                apiMessages = conversation.history,
            )
        }
    }

    /** Undo lives beside the message that made the change, not in a snackbar that vanishes. */
    fun undo(changeId: Long) {
        viewModelScope.launch {
            journal.undo(changeId)?.let {
                conversation.markUndone(changeId)
                persistConversation()
            }
        }
    }

    fun newConversation() {
        runningJob?.cancel()
        workingPump?.cancel()
        _working.value = null
        _changeHistory.value = emptyList()
        conversation.clear()
    }

    private companion object {
        const val MIN_WORKING_VISIBLE_MS = 900L
    }
}
