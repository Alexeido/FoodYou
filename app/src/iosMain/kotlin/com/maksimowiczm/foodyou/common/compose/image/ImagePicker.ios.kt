package com.maksimowiczm.foodyou.common.compose.image

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap

/** Not implemented on iOS yet: the camera button stays hidden rather than failing when tapped. */
@Composable
actual fun rememberImagePicker(onPicked: (PickedImage) -> Unit): ImagePickerController = remember {
    object : ImagePickerController {
        override val isAvailable = false

        override fun pick() = Unit
    }
}

/** Nothing to decode while [rememberImagePicker] cannot produce anything on this platform. */
actual fun decodeBase64Image(base64: String): ImageBitmap? = null

actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap? = null
