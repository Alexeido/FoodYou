package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Slim, non-expanding search bar. Unlike the Material3 [androidx.compose.material3.SearchBar] it
 * does not morph to a full-screen surface when focused — tapping just focuses the field inline and
 * the content stays visible below.
 */
@Composable
internal fun FoodSearchBar(
    textFieldState: TextFieldState,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onSearch: (String?) -> Unit,
    onBarcodeScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val empty = textFieldState.text.isEmpty()

    Surface(
        modifier = modifier.height(48.dp),
        shape = CircleShape,
        color =
            if (focused) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        border =
            if (focused) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    state = textFieldState,
                    modifier =
                        Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = {
                        onSearch(textFieldState.text.toString().ifBlank { null })
                    },
                )
                if (empty) {
                    Text(
                        text = stringResource(Res.string.action_search),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                if (empty) {
                    IconButton(onBarcodeScanner) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_barcode_scanner),
                            contentDescription = stringResource(Res.string.action_scan_barcode),
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            textFieldState.setTextAndPlaceCursorAtEnd("")
                            onSearch(null)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = stringResource(Res.string.action_clear),
                        )
                    }
                }
            }
        }
    }
}
