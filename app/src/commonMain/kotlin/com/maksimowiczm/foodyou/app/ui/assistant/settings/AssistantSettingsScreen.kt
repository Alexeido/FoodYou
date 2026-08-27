package com.maksimowiczm.foodyou.app.ui.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.compose.extension.add
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AssistantSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AssistantSettingsViewModel = koinViewModel()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var baseUrl by rememberSaveable(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var apiKeyDraft by rememberSaveable { mutableStateOf("") }
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_assistant)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues.add(vertical = 8.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.description_assistant_settings),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ---------------------------------------------------------------- servidor
            item {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(Res.string.headline_assistant_base_url)) },
                    supportingText = {
                        Text(stringResource(Res.string.description_assistant_base_url))
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // ---------------------------------------------------------------- clave
            item {
                OutlinedTextField(
                    value = if (hasApiKey && apiKeyDraft.isEmpty()) STORED_KEY_MASK else apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    label = { Text(stringResource(Res.string.headline_assistant_api_key)) },
                    supportingText = {
                        Text(stringResource(Res.string.description_assistant_api_key))
                    },
                    singleLine = true,
                    visualTransformation =
                        if (keyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    trailingIcon = {
                        TextButton(onClick = { keyVisible = !keyVisible }) {
                            Text(
                                stringResource(
                                    if (keyVisible) Res.string.action_hide else Res.string.action_show
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            viewModel.setBaseUrl(baseUrl)
                            if (apiKeyDraft.isNotBlank()) viewModel.setApiKey(apiKeyDraft)
                            apiKeyDraft = ""
                            viewModel.testConnection()
                        }
                    ) {
                        Text(stringResource(Res.string.action_test_connection))
                    }

                    if (hasApiKey) {
                        TextButton(onClick = viewModel::clearApiKey) {
                            Text(stringResource(Res.string.action_clear_api_key))
                        }
                    }

                    if (connection is ConnectionState.Testing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ---------------------------------------------------------------- resultado
            item { ConnectionResult(connection) }

            // ---------------------------------------------------------------- modelos
            val available = (connection as? ConnectionState.Connected)?.models.orEmpty()
            if (available.isNotEmpty()) {
                item {
                    ModelPicker(
                        title = stringResource(Res.string.headline_assistant_model),
                        description = stringResource(Res.string.description_assistant_model),
                        models = available,
                        selected = settings.model,
                        onSelect = viewModel::setModel,
                    )
                }

                item {
                    ModelPicker(
                        title = stringResource(Res.string.headline_assistant_vision_model),
                        description = stringResource(Res.string.description_assistant_vision_model),
                        models = available,
                        selected = settings.visionModel,
                        onSelect = { model ->
                            // Volver a tocar el ya elegido lo desactiva: sin modelo de vision la
                            // camara desaparece del chat, en vez de fallar al usarla.
                            viewModel.setVisionModel(if (model == settings.visionModel) "" else model)
                        },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }

            // ---------------------------------------------------------------- tope del bucle
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        text = stringResource(Res.string.headline_assistant_max_iterations),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(Res.string.description_assistant_max_iterations),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 6, 8, 12, 16).forEach { value ->
                            FilterChip(
                                selected = settings.maxIterations == value,
                                onClick = { viewModel.setMaxIterations(value) },
                                label = { Text(value.toString()) },
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ConnectionResult(state: ConnectionState, modifier: Modifier = Modifier) {
    if (state is ConnectionState.Idle || state is ConnectionState.Testing) return

    val (container, content, message) =
        when (state) {
            is ConnectionState.Connected ->
                Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    stringResource(Res.string.description_assistant_connected, state.models.size),
                )
            is ConnectionState.Failed ->
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    when (state.reason) {
                        FailureReason.Unauthorized ->
                            stringResource(Res.string.error_assistant_unauthorized)
                        FailureReason.RateLimited ->
                            stringResource(Res.string.error_assistant_rate_limited)
                        FailureReason.Network ->
                            stringResource(Res.string.error_assistant_network)
                        FailureReason.Server ->
                            stringResource(Res.string.error_assistant_server, state.detail ?: "")
                        FailureReason.NotConfigured ->
                            stringResource(Res.string.error_assistant_not_configured)
                    },
                )
            else -> return
        }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun ModelPicker(
    title: String,
    description: String,
    models: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            models.forEach { model ->
                FilterChip(
                    selected = model == selected,
                    onClick = { onSelect(model) },
                    label = { Text(model) },
                    shape = RoundedCornerShape(10.dp),
                )
            }
        }
    }
}

/** Stands in for a stored key so it is never rendered, not even behind dots. */
private const val STORED_KEY_MASK = "••••••••••"
