package com.maksimowiczm.foodyou.app.ui.assistant.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.assistant.domain.journal.AssistantChange
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The Photoshop-style undo history: every reversible thing the assistant did in this
 * conversation, most recent first. Tapping an older, still-applied entry reverts it and
 * everything after it in one move - not just the single most recent change, which is what the
 * per-turn "Undo" button next to each answer already covers.
 */
@Composable
fun ChangeHistorySheet(
    changes: List<AssistantChange>,
    onRevertTo: (Long) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    var pendingRevert by rememberSaveable { mutableStateOf<Long?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column {
            Text(
                text = stringResource(Res.string.headline_assistant_change_history),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            if (changes.isEmpty()) {
                Text(
                    text = stringResource(Res.string.description_assistant_change_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn {
                    items(changes, key = { it.id }) { change ->
                        ChangeHistoryRow(
                            change = change,
                            onClick = { if (!change.undone) pendingRevert = change.id },
                        )
                    }
                }
            }
        }
    }

    val targetId = pendingRevert
    if (targetId != null) {
        AlertDialog(
            onDismissRequest = { pendingRevert = null },
            title = { Text(stringResource(Res.string.headline_assistant_revert_confirm)) },
            text = { Text(stringResource(Res.string.description_assistant_revert_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRevert = null
                        onRevertTo(targetId)
                    }
                ) {
                    Text(stringResource(Res.string.action_assistant_revert))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevert = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ChangeHistoryRow(change: AssistantChange, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dateFormatter = LocalDateFormatter.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = !change.undone, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint =
                if (change.undone) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = change.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (change.undone) FontWeight.Normal else FontWeight.Medium,
                textDecoration = if (change.undone) TextDecoration.LineThrough else null,
                color =
                    if (change.undone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = dateFormatter.formatDateTime(change.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
