package com.maksimowiczm.foodyou.common.compose.speech

import androidx.compose.runtime.*

/** What the composer needs from voice input, kept small so each platform can satisfy it. */
@Stable
interface DictationController {
    /** False where the platform offers nothing: the microphone is then hidden rather than broken. */
    val isAvailable: Boolean

    val isListening: Boolean

    /** Text recognised so far, including the tentative tail. */
    val transcript: String

    fun start()

    fun stop()
}

/**
 * Speech to text only.
 *
 * The result fills the input field and the person sends it themselves, so a misheard word can be
 * fixed before it reaches the model - which matters more here than in a messaging app, because the
 * assistant acts on what it is told.
 */
@Composable expect fun rememberDictationController(): DictationController
