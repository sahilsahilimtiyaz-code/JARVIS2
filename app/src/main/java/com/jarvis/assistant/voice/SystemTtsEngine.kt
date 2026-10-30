package com.jarvis.assistant.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class SystemTtsEngine(context: Context) : TtsEngine {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val ready = CompletableDeferred<Boolean>()

    init {
        tts = TextToSpeech(appContext) { status ->
            ready.complete(status == TextToSpeech.SUCCESS)
        }
    }

    override suspend fun speak(text: String, config: TtsConfig, player: AudioPlayer): Boolean =
        withContext(Dispatchers.Main) {
            if (!ready.await()) return@withContext false
            val engine = tts ?: return@withContext false
            engine.setSpeechRate(config.speed.coerceIn(0.5f, 2.0f))
            val utteranceId = "jarvis_${UUID.randomUUID()}"
            suspendCancellableCoroutine { continuation ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (!continuation.isCompleted) continuation.resume(true)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (!continuation.isCompleted) continuation.resume(false)
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (!continuation.isCompleted) continuation.resume(false)
                    }
                })
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                continuation.invokeOnCancellation {
                    engine.stop()
                }
            }
        }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}