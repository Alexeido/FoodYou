package com.maksimowiczm.foodyou.app.ui.update

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.common.compose.image.decodeImageBytes
import com.maksimowiczm.foodyou.update.domain.StableRelease
import com.maksimowiczm.foodyou.update.domain.UpdateController
import com.maksimowiczm.foodyou.update.domain.UpdateFailure
import com.maksimowiczm.foodyou.update.domain.UpdateState
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Lives at the root of the app: checks once on launch and shows whatever the update flow needs,
 * whichever screen is open - including when the check was started from Settings.
 */
@Composable
fun UpdateHost(controller: UpdateController = koinInject()) {
    LaunchedEffect(controller) { controller.checkOnLaunch() }
    val state by controller.state.collectAsStateWithLifecycle()

    when (val s = state) {
        UpdateState.Idle,
        UpdateState.Checking -> Unit

        is UpdateState.Available ->
            ReleaseDialog(
                release = s.release,
                image = s.image,
                onDismiss = controller::close,
            ) {
                TextButton(onClick = controller::later) { Text(stringResource(Res.string.action_later)) }
                Button(onClick = controller::update) { Text(stringResource(Res.string.action_update)) }
            }

        is UpdateState.Downloading ->
            ReleaseDialog(
                release = s.release,
                image = s.image,
                onDismiss = {},
                dismissible = false,
                body = {
                    val progress = s.progress
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text =
                                stringResource(Res.string.description_downloading_update) +
                                    (progress?.let { " ${(it * 100).roundToInt()} %" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            ) {
                TextButton(onClick = controller::close) { Text(stringResource(Res.string.action_cancel)) }
            }

        is UpdateState.NeedsPermission ->
            SimpleDialog(
                icon = Icons.Outlined.NewReleases,
                title = stringResource(Res.string.headline_allow_install),
                text = stringResource(Res.string.description_allow_install),
                onDismiss = controller::close,
                dismissLabel = stringResource(Res.string.action_open_permission),
                onDismissButton = controller::openPermissionSettings,
                confirmLabel = stringResource(Res.string.action_install),
                onConfirm = controller::installAgain,
            )

        is UpdateState.Installing ->
            SimpleDialog(
                icon = Icons.Outlined.NewReleases,
                title = stringResource(Res.string.headline_installing_update),
                text = stringResource(Res.string.description_installing_update),
                onDismiss = controller::close,
                dismissLabel = stringResource(Res.string.action_close),
                onDismissButton = controller::close,
                confirmLabel = stringResource(Res.string.action_install),
                onConfirm = controller::installAgain,
            )

        is UpdateState.UpToDate ->
            SimpleDialog(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(Res.string.headline_up_to_date),
                text = stringResource(Res.string.description_up_to_date, s.build.toString()),
                onDismiss = controller::close,
                confirmLabel = stringResource(Res.string.action_close),
                onConfirm = controller::close,
            )

        is UpdateState.Failed ->
            SimpleDialog(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(Res.string.headline_update_failed),
                text =
                    stringResource(
                        when (s.reason) {
                            UpdateFailure.Check -> Res.string.description_update_check_failed
                            UpdateFailure.Download -> Res.string.description_update_download_failed
                        }
                    ),
                onDismiss = controller::close,
                dismissLabel = stringResource(Res.string.action_close),
                onDismissButton = controller::close,
                confirmLabel = stringResource(Res.string.action_try_again),
                onConfirm = { if (s.release != null) controller.update() else controller.checkNow() },
            )
    }
}

/**
 * The "new version" card: the release picture across the top when there is one, otherwise a
 * badge, then what changed.
 */
@Composable
private fun ReleaseDialog(
    release: StableRelease,
    image: ByteArray?,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    body: (@Composable () -> Unit)? = null,
    buttons: @Composable () -> Unit,
) {
    val bitmap: ImageBitmap? = remember(image) { image?.let(::decodeImageBytes) }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier.fillMaxWidth()
                                .aspectRatio(
                                    (bitmap.width.toFloat() / bitmap.height).coerceIn(1f, 2.2f)
                                ),
                    )
                }
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (bitmap == null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(Res.string.headline_new_version),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text =
                                buildList {
                                        add(
                                            stringResource(
                                                Res.string.description_new_version_build,
                                                release.version ?: release.build.toString(),
                                            )
                                        )
                                        release.sizeBytes?.let {
                                            add(
                                                stringResource(
                                                    Res.string.description_update_size,
                                                    (it / 1_000_000).coerceAtLeast(1).toString(),
                                                )
                                            )
                                        }
                                    }
                                    .joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    release.title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    release.notes?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                    body?.invoke()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        buttons()
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleDialog(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismissButton: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton =
            if (dismissLabel != null && onDismissButton != null) {
                { TextButton(onClick = onDismissButton) { Text(dismissLabel) } }
            } else {
                null
            },
    )
}
