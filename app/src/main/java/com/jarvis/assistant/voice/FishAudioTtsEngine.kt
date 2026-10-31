package com.jarvis.assistant.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Fish Audio text to speech over `POST /v1/tts`.
 *
 * The response is raw 16-bit mono PCM and is read in chunks as it arrives, so
 * the first sentence starts speaking while the rest is still being generated.
 * The `model` header defaults to `s2.1-pro-free`, the only model Fish Audio
 * allows without credit; a paid model can be selected in Settings.
 */
class FishAudioTtsEngine(private val client: OkHttpClient) : TtsEngine {

    override var lastError: String? = null

    override fun synthesize(text: String, config: TtsConfig): Flow<AudioChunk> = flow {
        lastError = null
        if (config.apiKey.isBlank()) {
            lastError = "Fish Audio needs an API key. Add one in Settings, then Voice."
            return@flow
        }
        if (config.voiceId.isBlank()) {
            lastError = "Fish Audio needs a voice reference id. Copy it from your Fish Audio voice."
            return@flow
        }
        val clean = text.trim()
        if (clean.isEmpty()) return@flow

        val payload = JSONObject().apply {
            put("text", clean)
            put("reference_id", config.voiceId.trim())
            put("format", "pcm")
            put("sample_rate", PCM_SAMPLE_RATE)
            // Balanced is Fish Audio's documented streaming default.
            put("latency", "balanced")
            put("normalize", true)
            put("chunk_length", 200)
            put("prosody", JSONObject().put("speed", config.speed.coerceIn(0.5f, 2.0f).toDouble()))
            config.language.trim().takeIf { it.isNotBlank() && it.length <= 35 }?.let {
                put("language", it)
            }
        }
        val body = payload.toString().toRequestBody(JSON_MEDIA)

        var attempts = 0
        while (true) {
            attempts++
            // A retry that succeeds must not leave the previous failure looking
            // like a failure of the audio that was just produced.
            lastError = null
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer ${config.apiKey.trim()}")
                .header("Content-Type", "application/json")
                .header("Accept", "audio/pcm")
                .header("model", modelName(config.model))
                .post(body)
                .build()

            val call = client.newCall(request)
            var retryable = false
            try {
                withContext(Dispatchers.IO) {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            val detail = readErrorBody(response)
                            retryable = response.code == 429 || response.code >= 500
                            lastError = friendlyError(response.code, detail)
                            return@withContext
                        }
                        val stream = response.body.byteStream()
                        val buffer = ByteArray(CHUNK_BYTES)
                        var sawAudio = false
                        while (true) {
                            ensureActive()
                            val read = stream.read(buffer)
                            if (read <= 0) break
                            sawAudio = true
                            emit(AudioChunk(buffer.copyOf(read), PCM_SAMPLE_RATE))
                        }
                        if (!sawAudio) {
                            lastError = "Fish Audio returned no audio. Check the voice id and try again."
                        }
                    }
                }
            } catch (e: CancellationException) {
                call.cancel()
                throw e
            } catch (e: Exception) {
                call.cancel()
                lastError = "Fish Audio could not be reached: ${e.message ?: e::class.simpleName}"
                retryable = true
            }

            if (lastError == null || !retryable || attempts >= MAX_ATTEMPTS) return@flow
            delay(RETRY_DELAY_MS * attempts)
        }
    }.flowOn(Dispatchers.IO)

    private fun modelName(configured: String): String {
        val model = configured.trim()
        return model.ifBlank { DEFAULT_MODEL }
    }

    private fun readErrorBody(response: okhttp3.Response): String = runCatching {
        // A non-2xx body is a small JSON error or plain text; cap it hard so it
        // can be shown on the settings screen without flooding the notification.
        response.body.string().take(600).replace(Regex("\\s+"), " ").trim()
    }.getOrDefault("")

    private fun friendlyError(code: Int, detail: String): String {
        val suffix = detail.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return when (code) {
            400 -> "Fish Audio rejected the request. Check the voice reference id$suffix."
            401 -> "Fish Audio rejected the API key. Copy it again from fish.audio/app/api-keys."
            402 -> "Fish Audio has no credit for this model. Choose s2.1-pro-free in Voice settings."
            403 -> "This Fish Audio key is not allowed to use that voice$suffix."
            404 -> "Fish Audio could not find that voice reference id."
            413 -> "That sentence was too long for Fish Audio."
            429 -> "Fish Audio is busy (rate limit). I will use the fallback voice."
            in 500..599 -> "Fish Audio is having server trouble (HTTP $code)."
            else -> "Fish Audio returned HTTP $code$suffix."
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.fish.audio/v1/tts"
        const val DEFAULT_MODEL = "s2.1-pro-free"
        const val PCM_SAMPLE_RATE = 24_000
        const val CHUNK_BYTES = 8 * 1024
        const val MAX_ATTEMPTS = 2
        const val RETRY_DELAY_MS = 600L
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
