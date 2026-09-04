package com.maksimowiczm.foodyou.common.compose.image

import androidx.compose.runtime.*

/** A photo, already downsampled and encoded the way the API wants it. */
data class PickedImage(val base64: String, val mimeType: String) {
    /** The inline data URI form verified against the API. */
    val dataUri: String
        get() = "data:$mimeType;base64,$base64"
}

@Stable
interface ImagePickerController {
    val isAvailable: Boolean

    /** Whether this device can take a photo right now, as opposed to only picking an existing one. */
    val canTakePhoto: Boolean
        get() = false

    fun pick()

    /** Opens the camera. Falls back to [pick] where taking a photo is not supported. */
    fun takePhoto() = pick()
}

/**
 * Decodes what [rememberImagePicker] produced back into something drawable.
 *
 * Returns null when the platform cannot decode it, so callers fall back to a caption instead of
 * crashing on a corrupt or unsupported payload.
 */
expect fun decodeBase64Image(base64: String): androidx.compose.ui.graphics.ImageBitmap?

/**
 * Picks a photo and hands back base64.
 *
 * Downsampling before encoding is not optional: an untouched phone photo is several megabytes of
 * base64, which makes the request slow and expensive for no gain - the model reads a 1024 px image
 * just as well.
 */
@Composable expect fun rememberImagePicker(onPicked: (PickedImage) -> Unit): ImagePickerController
