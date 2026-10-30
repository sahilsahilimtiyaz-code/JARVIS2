package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

sealed interface SttEvent {
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
    data class Error(val message: String) : SttEvent
    data object End : SttEvent
}

interface SttEngine {
    fun listen(locale: Locale): Flow<SttEvent>
}

class AndroidSttEngine(private val context: Context) : SttEngine {

    override fun listen(locale: Locale): Flow<SttEvent> = callbackFlow {
        val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }

        if (recognizer == null) {
            trySend(SttEvent.Error("Speech recognition is not available on this phone."))
            close()
            return@callbackFlow
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
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
                trySend(SttEvent.Error(sttErrorMessage(error)))
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

        recognizer.startListening(intent)

        awaitClose {
            runCatching { recognizer.stopListening() }
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main)
}

private fun sttErrorMessage(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "I could not hear you clearly."
    SpeechRecognizer.ERROR_CLIENT -> "Speech recognition stopped."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
    SpeechRecognizer.ERROR_NETWORK -> "Speech recognition needs a network connection."
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
    SpeechRecognizer.ERROR_NO_MATCH -> "I did not catch that."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy."
    SpeechRecognizer.ERROR_SERVER -> "Speech recognition failed on the server."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear anything."
    else -> "Speech recognition failed."
}