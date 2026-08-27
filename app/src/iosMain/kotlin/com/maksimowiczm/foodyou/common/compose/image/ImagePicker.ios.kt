package com.maksimowiczm.foodyou.common.compose.image

import androidx.compose.runtime.*

/** Not implemented on iOS yet: the camera button stays hidden rather than failing when tapped. */
@Composable
actual fun rememberImagePicker(onPicked: (PickedImage) -> Unit): ImagePickerController = remember {
    object : ImagePickerController {
        override val isAvailable = false

        override fun pick() = Unit
    }
}
