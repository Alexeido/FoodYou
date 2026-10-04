package com.maksimowiczm.foodyou.app.ui.assistant.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.assistant.domain.AssistantConversationRepository
import com.maksimowiczm.foodyou.assistant.domain.ConversationStore
import com.maksimowiczm.foodyou.assistant.domain.ConversationSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class AssistantConversationsViewModel(
    private val repository: AssistantConversationRepository,
    private val conversationStore: ConversationStore,
) : ViewModel() {

    val conversations: StateFlow<List<ConversationSummary>> =
        repository
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** Loads [id] into the one live conversation and calls [onDone] once it is ready to show. */
    fun open(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.load(id)?.let { snapshot ->
                conversationStore.restore(id, snapshot.turns, snapshot.apiMessages)
            }
            onDone()
        }
    }
}
