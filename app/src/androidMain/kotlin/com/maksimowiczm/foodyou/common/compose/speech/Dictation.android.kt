package com.maksimowiczm.foodyou.common.compose.speech

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberDictationController(): DictationController {
    val context = LocalContext.current
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var wantsToStart by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
            if (!granted) wantsToStart = false
        }

    val recognizer = remember {
        if (available) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }

    // El reconocedor se arranca en un efecto y no dentro de start(): createSpeechRecognizer exige
    // hilo principal y start() puede llamarse desde cualquier sitio.
    LaunchedEffect(wantsToStart, permissionGranted) {
        if (!wantsToStart || !permissionGranted || recognizer == null) return@LaunchedEffect

        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let { transcript = it }
                }

                override fun onResults(results: Bundle?) {
                    results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let { transcript = it }
                    listening = false
                    wantsToStart = false
                }

                override fun onError(error: Int) {
                    listening = false
                    wantsToStart = false
                }

                override fun onEndOfSpeech() {
                    listening = false
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            }
        )

        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
        )
    }

    return remember(available, listening, transcript, permissionGranted) {
        object : DictationController {
            override val isAvailable = available
            override val isListening = listening
            override val transcript = transcript

            override fun start() {
                transcript = ""
                if (!permissionGranted) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                wantsToStart = true
            }

            override fun stop() {
                recognizer?.stopListening()
                listening = false
                wantsToStart = false
            }
        }
    }
}
