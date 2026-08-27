package com.maksimowiczm.foodyou.common.compose.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_EDGE = 1024
private const val JPEG_QUALITY = 82

@Composable
actual fun rememberImagePicker(onPicked: (PickedImage) -> Unit): ImagePickerController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val encoded =
                    withContext(Dispatchers.IO) {
                        runCatching {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    val bitmap = BitmapFactory.decodeStream(stream) ?: return@use null
                                    val scaled = bitmap.downscaled()
                                    val bytes = ByteArrayOutputStream()
                                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)
                                    Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                                }
                            }
                            .getOrNull()
                    }

                if (encoded != null) onPicked(PickedImage(encoded, "image/jpeg"))
            }
        }

    return remember {
        object : ImagePickerController {
            override val isAvailable = true

            override fun pick() {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }
}

/** Longest edge capped, aspect kept. A 4000 px photo becomes ~90 KB of base64 instead of ~7 MB. */
private fun Bitmap.downscaled(): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= MAX_EDGE) return this
    val ratio = MAX_EDGE.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
}
