package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Fallback voice built on the Android system text-to-speech engine.
 *
 * [synthesize] never speaks; it hands the text back to the caller so the shared
 * [AudioPlayer] pipeline decides when to play it. [speak] does the actual
 * playback and suspends until the utterance finishes or is cancelled.
 */
class SystemTtsEngine(context: Context) : TtsEngine {
    override var lastError: String? = null
    private val appContext = context.applicationContext

    private var engine: TextToSpeech? = null
    private var ready = CompletableDeferred<Boolean>()

    init {
        createEngine()
    }

    private fun createEngine() {
        ready = CompletableDeferred()
        engine = TextToSpeech(appContext) { status ->
            ready.complete(status == TextToSpeech.SUCCESS)
        }
    }

    override suspend fun synthesize(text: String, config: TtsConfig): SpeechPayload? {
        lastError = null
        if (text.isBlank()) return null
        if (!awaitReady()) {
            lastError = "The system voice engine is not ready."
            return null
        }
        return SpeechPayload.SystemText(text)
    }

    /** Speaks [text] through the system engine and suspends until it finishes. */
    suspend fun speak(text: String, language: String = "en"): Boolean {
        lastError = null
        if (text.isBlank()) return true
        if (!awaitReady()) {
            lastError = "The system voice engine is not ready."
            return false
        }
        val tts = engine ?: return false
        val utteranceId = UUID.randomUUID().toString()

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                // The listener is installed before speak() so a fast utterance
                // cannot complete before anyone is listening for it.
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onError(utteranceId: String?) {
                        if (continuation.isActive) continuation.resume(false)
                    }

                    @Deprecated("Superseded by the 21+ overload", ReplaceWith("onError(id, code)"))
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        lastError = "The system voice failed (code $errorCode)."
                        if (continuation.isActive) continuation.resume(false)
                    }
                })

                val locale = runCatching { Locale.forLanguageTag(language) }
                    .getOrDefault(Locale.getDefault())
                runCatching {
                    if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                        tts.language = locale
                    }
                }

                val started = runCatching {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                }.getOrDefault(TextToSpeech.ERROR)
                if (started == TextToSpeech.ERROR) {
                    lastError = "The system voice refused to speak."
                    if (continuation.isActive) continuation.resume(false)
                }

                continuation.invokeOnCancellation {
                    runCatching { tts.stop() }
                }
            }
        }
    }

    /** Cuts off the current utterance. The engine stays usable afterwards. */
    fun stop() {
        runCatching { engine?.stop() }
    }

    /** Frees the engine. Only for permanent teardown. */
    fun shutdown() {
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }

    private suspend fun awaitReady(): Boolean {
        val tts = engine ?: return false
        val ok = ready.await()
        if (!ok && engine === tts) {
            lastError = "The system voice engine is not available."
        }
        return ok && engine != null
    }
}
