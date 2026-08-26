package com.maksimowiczm.foodyou.app.ui.database.externaldatabases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foodyou.app.generated.resources.*
import io.ktor.http.Url
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UpdateCustomFoodSourceDialog(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: UpdateCustomFoodSourceDialogViewModel = koinViewModel()
    val initialValues by viewModel.initialValues.collectAsStateWithLifecycle()
    val values = initialValues ?: return

    var baseUrl by rememberSaveable(values) { mutableStateOf(values.baseUrl) }
    var username by rememberSaveable(values) { mutableStateOf(values.username) }
    var password by rememberSaveable(values) { mutableStateOf(values.password) }

    val baseUrlValid = baseUrl.isNotBlank() && runCatching { Url(baseUrl) }.isSuccess
    val usernameValid = username.isNotBlank()
    val passwordValid = password.isNotBlank()
    val formValid = baseUrlValid && usernameValid && passwordValid

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = formValid,
                onClick = {
                    viewModel.save(
                        baseUrl = baseUrl.trim(),
                        username = username,
                        password = password,
                        onSaved = onSave,
                    )
                },
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onDismissRequest) { Text(stringResource(Res.string.action_cancel)) }
        },
        title = { Text(stringResource(Res.string.headline_custom_food_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(Res.string.headline_server_url)) },
                    isError = baseUrl.isNotEmpty() && !baseUrlValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.headline_username)) },
                    isError = username.isEmpty(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.headline_password)) },
                    isError = password.isEmpty(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
    )
}
