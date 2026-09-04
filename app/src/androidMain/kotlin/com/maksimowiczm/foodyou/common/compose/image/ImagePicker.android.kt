package com.maksimowiczm.foodyou.common.compose.image

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_EDGE = 1024
private const val JPEG_QUALITY = 82

@Composable
actual fun rememberImagePicker(onPicked: (PickedImage) -> Unit): ImagePickerController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // La foto recien hecha se escribe aqui antes de leerla: TakePicture no devuelve la imagen,
    // solo escribe en la URI que se le da.
    val pendingPhoto = remember { mutableStateOf<Uri?>(null) }

    fun encode(uri: Uri) {
        scope.launch {
            val encoded = withContext(Dispatchers.IO) { context.encodeDownscaled(uri) }
            if (encoded != null) onPicked(PickedImage(encoded, "image/jpeg"))
        }
    }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) encode(uri)
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = pendingPhoto.value
            pendingPhoto.value = null
            if (saved && uri != null) encode(uri)
        }

    val hasCamera =
        remember(context) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        }

    return remember(hasCamera) {
        object : ImagePickerController {
            override val isAvailable = true

            override val canTakePhoto = hasCamera

            override fun pick() {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }

            override fun takePhoto() {
                if (!hasCamera) return pick()
                val uri = context.newPhotoUri() ?: return pick()
                pendingPhoto.value = uri
                cameraLauncher.launch(uri)
            }
        }
    }
}

/**
 * A writable URI for the camera to fill.
 *
 * It has to be a `content://` URI from our own FileProvider: handing another app a `file://` URI
 * throws FileUriExposedException on anything modern.
 */
private fun Context.newPhotoUri(): Uri? =
    runCatching {
        val dir = File(cacheDir, "assistant-photos").apply { mkdirs() }
        val file = File.createTempFile("photo-", ".jpg", dir)
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }
        .getOrNull()

private fun Context.encodeDownscaled(uri: Uri): String? =
    runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return@use null
                val scaled = bitmap.downscaled()
                val bytes = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)
                Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            }
        }
        .getOrNull()

actual fun decodeBase64Image(base64: String): ImageBitmap? =
    runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        .getOrNull()

/** Longest edge capped, aspect kept. A 4000 px photo becomes ~90 KB of base64 instead of ~7 MB. */
private fun Bitmap.downscaled(): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= MAX_EDGE) return this
    val ratio = MAX_EDGE.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
}
