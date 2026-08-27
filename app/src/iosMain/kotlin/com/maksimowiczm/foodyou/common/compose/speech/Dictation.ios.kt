package com.maksimowiczm.foodyou.common.compose.speech

import androidx.compose.runtime.*

/**
 * Not implemented on iOS yet.
 *
 * Reporting unavailable rather than throwing is the whole point of the interface: the microphone
 * simply does not appear, exactly as the camera does not appear without a vision model.
 */
@Composable
actual fun rememberDictationController(): DictationController = remember {
    object : DictationController {
        override val isAvailable = false
        override val isListening = false
        override val transcript = ""

        override fun start() = Unit

        override fun stop() = Unit
    }
}
