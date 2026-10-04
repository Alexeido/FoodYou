package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.BuildConfig
import com.maksimowiczm.foodyou.app.ui.common.component.SettingsListItem
import com.maksimowiczm.foodyou.update.domain.UpdateController
import com.maksimowiczm.foodyou.update.domain.UpdateState
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** "Check for updates": asks the update server now; the answer appears as a dialog. */
@Composable
internal fun UpdateSettingsListItem(
    shape: Shape,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    controller: UpdateController = koinInject(),
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val checking = state is UpdateState.Checking

    SettingsListItem(
        icon = { Icon(Icons.Outlined.SystemUpdate, null) },
        label = { Text(stringResource(Res.string.headline_check_updates)) },
        supportingContent = {
            Text(
                when {
                    checking -> stringResource(Res.string.description_checking_updates)
                    BuildConfig.BUILD_NUMBER == 0 ->
                        stringResource(Res.string.description_development_build)
                    else ->
                        stringResource(
                            Res.string.description_installed_version,
                            BuildConfig.BUILD_NUMBER.toString(),
                        )
                }
            )
        },
        onClick = { if (!checking) controller.checkNow() },
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
    )
}
