package com.maksimowiczm.foodyou.app.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.sync.infrastructure.SyncFailure
import foodyou.app.generated.resources.*
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SyncSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SyncSettingsViewModel = koinViewModel()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()

    // Los campos se rellenan con lo guardado la primera vez que llega.
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(config) {
        val c = config
        if (!loaded && c != null) {
            url = c.serverUrl
            username = c.username
            password = c.password
            loaded = true
        }
    }
    val enabled = config?.enabled == true
    val edited = config != null && (url != config?.serverUrl || username != config?.username || password != config?.password)
    val complete = url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    var confirmDisable by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_sync)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = padding.add(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.description_sync_intro),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(Res.string.description_sync_first_time),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (enabled) {
                item {
                    SectionCard(stringResource(Res.string.headline_sync_status)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(Res.string.label_sync_enabled),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = true, onCheckedChange = { confirmDisable = true })
                        }
                        Text(
                            text =
                                when {
                                    status.running -> stringResource(Res.string.description_sync_running)
                                    status.lastFailure != null -> failureText(status.lastFailure!!)
                                    status.lastSuccess != null ->
                                        stringResource(
                                            Res.string.description_sync_last,
                                            relativeTime(status.lastSuccess!!),
                                        )
                                    else -> stringResource(Res.string.description_sync_never)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (status.lastFailure != null && !status.running) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        if (pending > 0) {
                            Text(
                                stringResource(Res.string.description_sync_pending, pending.toString()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::syncNow, enabled = !status.running) {
                                Text(stringResource(Res.string.action_sync_now))
                            }
                            OutlinedButton(onClick = viewModel::pairWatch) {
                                Text(stringResource(Res.string.action_pair_watch))
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(stringResource(Res.string.headline_sync_server)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(Res.string.label_sync_url)) },
                        placeholder = { Text("https://sync.example.com") },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(Res.string.label_sync_username)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(Res.string.label_sync_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    when (val t = test) {
                        ConnectionTest.Idle -> Unit
                        ConnectionTest.Testing ->
                            Text(
                                stringResource(Res.string.description_sync_testing),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        is ConnectionTest.Ok ->
                            Text(
                                stringResource(
                                    Res.string.description_sync_connected,
                                    t.account,
                                    t.documents.toString(),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        is ConnectionTest.Failed ->
                            Text(
                                failureText(t.reason),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.testConnection(url, username, password) },
                            enabled = complete && test != ConnectionTest.Testing,
                        ) {
                            Text(stringResource(Res.string.action_test_connection))
                        }
                        if (!enabled) {
                            Button(
                                onClick = { viewModel.enable(url, username, password) },
                                enabled = complete,
                            ) {
                                Text(stringResource(Res.string.action_enable_sync))
                            }
                        } else if (edited) {
                            Button(
                                onClick = { viewModel.save(url, username, password) },
                                enabled = complete,
                            ) {
                                Text(stringResource(Res.string.action_save))
                            }
                        }
                    }
                }
            }

            if (config != null) {
                item {
                    TextButton(
                        onClick = {
                            viewModel.forget()
                            url = ""
                            username = ""
                            password = ""
                        }
                    ) {
                        Text(stringResource(Res.string.action_forget_sync_account))
                    }
                }
            }
        }
    }

    when (val p = pairing) {
        PairingState.Hidden -> Unit
        PairingState.Loading,
        is PairingState.Code,
        is PairingState.Failed ->
            AlertDialog(
                onDismissRequest = viewModel::closePairing,
                title = { Text(stringResource(Res.string.headline_pair_watch)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (p) {
                            is PairingState.Code -> {
                                Text(
                                    text = p.code.chunked(3).joinToString(" "),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Text(
                                    stringResource(
                                        Res.string.description_pair_watch,
                                        p.minutes.toString(),
                                    )
                                )
                            }
                            is PairingState.Failed ->
                                Text(failureText(p.reason), color = MaterialTheme.colorScheme.error)
                            else -> Text(stringResource(Res.string.description_sync_testing))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::closePairing) {
                        Text(stringResource(Res.string.action_close))
                    }
                },
            )
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text(stringResource(Res.string.headline_disable_sync)) },
            text = { Text(stringResource(Res.string.description_disable_sync)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisable = false
                        viewModel.disable()
                    }
                ) {
                    Text(stringResource(Res.string.action_turn_off))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
internal fun failureText(reason: SyncFailure): String =
    stringResource(
        when (reason) {
            SyncFailure.Network -> Res.string.description_sync_error_network
            SyncFailure.Unauthorized -> Res.string.description_sync_error_unauthorized
            SyncFailure.Forbidden -> Res.string.description_sync_error_forbidden
            SyncFailure.Server -> Res.string.description_sync_error_server
        }
    )

/** "hace 3 min", refreshed every time the screen recomposes (the status changes often). */
@Composable
internal fun relativeTime(epochMillis: Long): String {
    val minutes = (Clock.System.now().toEpochMilliseconds() - epochMillis) / 60_000
    return when {
        minutes < 1 -> stringResource(Res.string.time_just_now)
        minutes < 60 -> stringResource(Res.string.time_minutes_ago, minutes.toString())
        minutes < 60 * 24 -> stringResource(Res.string.time_hours_ago, (minutes / 60).toString())
        else -> stringResource(Res.string.time_days_ago, (minutes / (60 * 24)).toString())
    }
}
