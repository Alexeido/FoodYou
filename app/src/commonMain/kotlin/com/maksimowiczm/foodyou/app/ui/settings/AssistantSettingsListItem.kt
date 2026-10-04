package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.maksimowiczm.foodyou.app.ui.common.component.SettingsListItem
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Always present, even with nothing configured.
 *
 * The assistant itself disappears from the rest of the app when there is no key - but if this entry
 * disappeared too, nobody would ever find out the feature exists.
 */
@Composable
internal fun AssistantSettingsListItem(
    onClick: () -> Unit,
    shape: Shape,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsListItem(
        icon = { Icon(Icons.Outlined.AutoAwesome, null) },
        label = { Text(stringResource(Res.string.headline_assistant)) },
        supportingContent = {
            Text(stringResource(Res.string.description_assistant_settings_entry))
        },
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
    )
}
