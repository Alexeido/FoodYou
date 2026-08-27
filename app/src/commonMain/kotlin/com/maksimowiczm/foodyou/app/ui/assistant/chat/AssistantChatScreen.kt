package com.maksimowiczm.foodyou.app.ui.assistant.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.assistant.domain.ChatError
import com.maksimowiczm.foodyou.assistant.domain.ChatTurn
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AssistantChatScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AssistantChatViewModel = koinViewModel()

    val turns by viewModel.turns.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val supportsVision by viewModel.supportsVision.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    LaunchedEffect(turns.size, working) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            var menuOpen by rememberSaveable { mutableStateOf(false) }
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(Res.string.headline_assistant))
                        if (model.isNotBlank()) {
                            Text(
                                text = model,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, null)
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_new_conversation)) },
                                onClick = {
                                    viewModel.newConversation()
                                    menuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_go_to_settings)) },
                                onClick = {
                                    onOpenSettings()
                                    menuOpen = false
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).imePadding()) {
            if (turns.isEmpty()) {
                EmptyChat(
                    // Desde recursos, no literales: la app es bilingue y esto es lo primero
                    // que se lee al abrir el chat.
                    suggestions =
                        listOf(
                            stringResource(Res.string.description_assistant_suggestion_fill_day),
                            stringResource(Res.string.description_assistant_suggestion_week),
                            stringResource(Res.string.description_assistant_suggestion_fat),
                        ),
                    onSuggestion = { viewModel.send(it) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(turns, key = { it.id }) { turn ->
                        when (turn.role) {
                            ChatTurn.Role.User -> UserBubble(turn)
                            ChatTurn.Role.Assistant ->
                                AssistantTurn(turn = turn, onUndo = viewModel::undo)
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = working != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            WorkingLine(working.orEmpty())
                        }
                    }
                }
            }

            ChatComposer(
                showCamera = supportsVision,
                onSend = { text, image -> viewModel.send(text, image?.dataUri) },
            )
        }
    }
}

@Composable
private fun UserBubble(turn: ChatTurn, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = turn.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AssistantTurn(
    turn: ChatTurn,
    onUndo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sin burbuja: la respuesta ocupa el ancho para que quepan las cifras y las acciones.
    Column(modifier = modifier.fillMaxWidth()) {
        if (turn.error != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text =
                            stringResource(
                                if (turn.error == ChatError.Unauthorized)
                                    Res.string.error_assistant_unauthorized
                                else Res.string.error_assistant_generic
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    // El detalle tecnico, que es lo unico que permite arreglar el problema.
                    // Antes se descartaba y solo quedaba "algo ha fallado", que no dice nada.
                    if (turn.text.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = turn.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.description_assistant_diary_untouched),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            return@Column
        }

        Text(text = turn.text, style = MaterialTheme.typography.bodyMedium)

        if (turn.changeId != null) {
            Spacer(Modifier.height(8.dp))
            UndoButton(undone = turn.undone, onClick = { onUndo(turn.changeId) })
        }
    }
}

@Composable
private fun UndoButton(undone: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.then(if (undone) Modifier else Modifier.clickable(onClick = onClick)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Text(
            text =
                stringResource(
                    if (undone) Res.string.description_assistant_undone else Res.string.action_undo
                ),
            style = MaterialTheme.typography.labelLarge,
            color =
                if (undone) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun WorkingLine(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier.size(5.dp)
                            .background(
                                color =
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.4f + index * 0.3f
                                    ),
                                shape = RoundedCornerShape(3.dp),
                            )
                    )
                }
            }
            Text(
                text = "$label...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyChat(
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(Res.string.headline_assistant_empty),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.description_assistant_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        suggestions.forEach { suggestion ->
            Surface(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onSuggestion(suggestion) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
