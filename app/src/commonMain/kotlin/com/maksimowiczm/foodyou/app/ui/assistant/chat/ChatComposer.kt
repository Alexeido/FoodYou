package com.maksimowiczm.foodyou.app.ui.assistant.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.common.compose.image.PickedImage
import com.maksimowiczm.foodyou.common.compose.image.decodeBase64Image
import com.maksimowiczm.foodyou.common.compose.image.rememberImagePicker
import com.maksimowiczm.foodyou.common.compose.speech.rememberDictationController
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The input row.
 *
 * The camera and the microphone sit *inside* the field because both are ways of filling it; sending
 * stays outside because it is a different kind of act. The camera is absent entirely when no vision
 * model is configured - the app never offers something that would fail.
 */
@Composable
internal fun ChatComposer(
    showCamera: Boolean,
    onSend: (text: String, image: PickedImage?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberTextFieldState()
    val hasText = state.text.isNotBlank()

    var attached by remember { mutableStateOf<PickedImage?>(null) }
    val picker = rememberImagePicker { attached = it }
    val dictation = rememberDictationController()

    // Lo dictado rellena el campo; el envio sigue siendo tuyo, para poder corregir antes.
    LaunchedEffect(dictation.transcript) {
        if (dictation.transcript.isNotBlank()) {
            state.setTextAndPlaceCursorAtEnd(dictation.transcript)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        attached?.let { photo ->
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Se ve la foto antes de enviarla: "Foto adjunta" a secas no permite comprobar
                // que la elegida es la que se queria.
                val preview = remember(photo.base64) { decodeBase64Image(photo.base64) }
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription =
                            stringResource(Res.string.description_assistant_photo_attached),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                    )
                }
                Text(
                    text = stringResource(Res.string.description_assistant_photo_attached),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.action_remove),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { attached = null },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier =
                    Modifier.weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(24.dp))
                        .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (!hasText) {
                        Text(
                            text = stringResource(Res.string.description_assistant_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    BasicTextField(
                        state = state,
                        textStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }

                // La camara solo existe si hay modelo con vision Y la plataforma sabe elegir foto.
                if (showCamera && picker.isAvailable) {
                    Box {
                        var sourceMenuOpen by remember { mutableStateOf(false) }
                        ComposerAction(
                            // Con camara disponible se pregunta primero: el caso normal del
                            // asistente es fotografiar lo que tienes delante, no rebuscar en la
                            // galeria una foto que habria que haber hecho antes desde otra app.
                            onClick = {
                                if (picker.canTakePhoto) sourceMenuOpen = true else picker.pick()
                            },
                            icon = {
                                Icon(
                                    Icons.Outlined.PhotoCamera,
                                    stringResource(Res.string.action_attach_photo),
                                )
                            },
                        )
                        DropdownMenu(
                            expanded = sourceMenuOpen,
                            onDismissRequest = { sourceMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_take_photo)) },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                                onClick = {
                                    sourceMenuOpen = false
                                    picker.takePhoto()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(Res.string.action_choose_from_gallery))
                                },
                                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                                onClick = {
                                    sourceMenuOpen = false
                                    picker.pick()
                                },
                            )
                        }
                    }
                }

                if (dictation.isAvailable) {
                    ComposerAction(
                        onClick = { if (dictation.isListening) dictation.stop() else dictation.start() },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = stringResource(Res.string.action_dictate),
                                tint =
                                    if (dictation.isListening) MaterialTheme.colorScheme.primary
                                    else LocalContentColor.current,
                            )
                        },
                    )
                }
            }

            Box(
                modifier =
                    Modifier.size(44.dp)
                        .background(
                            if (hasText) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape,
                        )
                        .clickable(enabled = hasText) {
                            onSend(state.text.toString(), attached)
                            state.clearText()
                            attached = null
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(Res.string.action_send),
                    tint =
                        if (hasText) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ComposerAction(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.outline) {
            icon()
        }
    }
}
