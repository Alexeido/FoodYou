package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.SettingsListItem
import com.maksimowiczm.foodyou.app.ui.sync.relativeTime
import com.maksimowiczm.foodyou.sync.domain.SyncConfigRepository
import com.maksimowiczm.foodyou.sync.infrastructure.SyncEngine
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun SyncSettingsListItem(
    onClick: () -> Unit,
    shape: Shape,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val configRepository: SyncConfigRepository = koinInject()
    val engine: SyncEngine = koinInject()
    val config by configRepository.observe().collectAsStateWithLifecycle(null)
    val status by engine.status.collectAsStateWithLifecycle()
    val lastSuccess = status.lastSuccess

    SettingsListItem(
        icon = { Icon(Icons.Outlined.CloudSync, null) },
        label = { Text(stringResource(Res.string.headline_sync)) },
        supportingContent = {
            Text(
                when {
                    config?.enabled != true -> stringResource(Res.string.description_sync_setting)
                    lastSuccess != null ->
                        stringResource(Res.string.description_sync_last, relativeTime(lastSuccess))
                    else -> stringResource(Res.string.description_sync_never)
                }
            )
        },
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
    )
}
