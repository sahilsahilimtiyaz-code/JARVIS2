package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

sealed interface SttEvent {
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
    data class Error(val message: String, val code: Int? = null) : SttEvent
    data object End : SttEvent
}

interface SttEngine {
    fun listen(locale: Locale): Flow<SttEvent>
}

/**
 * Android speech recognition.
 *
 * On Android 13+ the on-device recogniser is preferred when the language is
 * available: it works offline, is faster, and keeps audio on the phone. The
 * network recogniser is used otherwise. Each listen creates and destroys its own
 * [SpeechRecognizer], which is the documented lifecycle and avoids the
 * `ERROR_RECOGNIZER_BUSY` dead ends of reusing one instance across loops.
 */
class AndroidSttEngine(private val context: Context) : SttEngine {

    override fun listen(locale: Locale): Flow<SttEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SttEvent.Error("Speech recognition is not available on this phone."))
            close()
            return@callbackFlow
        }

        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
                .getOrDefault(false)

        val recognizer = runCatching {
            if (onDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.recoverCatching {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull()

        if (recognizer == null) {
            trySend(SttEvent.Error("Speech recognition could not be started."))
            close()
            return@callbackFlow
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SttEvent.Partial(""))
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                trySend(SttEvent.End)
            }

            override fun onError(error: Int) {
                trySend(SttEvent.Error(sttErrorMessage(error), error))
                close()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) {
                    trySend(SttEvent.Final(text))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) {
                    trySend(SttEvent.Partial(text))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val started = runCatching { recognizer.startListening(intent) }.isSuccess
        if (!started) {
            trySend(SttEvent.Error("Speech recognition could not start listening."))
            close()
        }

        awaitClose {
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }.flowOn(Dispatchers.Main)
}

/** True when the error means the next listen attempt is worth trying. */
fun isRecoverableSttError(code: Int?): Boolean = when (code) {
    SpeechRecognizer.ERROR_AUDIO,
    SpeechRecognizer.ERROR_CLIENT,
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> true

    else -> false
}

private fun sttErrorMessage(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "I could not hear you clearly."
    SpeechRecognizer.ERROR_CLIENT -> "Speech recognition stopped unexpectedly."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
    SpeechRecognizer.ERROR_NETWORK -> "Speech recognition needs a network connection."
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
    SpeechRecognizer.ERROR_NO_MATCH -> "I did not catch that."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy."
    SpeechRecognizer.ERROR_SERVER -> "Speech recognition failed on the server."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear anything."
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "That language is not supported by the recogniser."
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "That language is not installed for speech recognition."
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Speech recognition is rate limiting. One moment."
    else -> "Speech recognition failed."
}
