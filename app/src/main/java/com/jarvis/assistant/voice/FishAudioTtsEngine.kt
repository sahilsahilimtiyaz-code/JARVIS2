package com.jarvis.assistant.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class FishAudioTtsEngine(private val client: OkHttpClient) : TtsEngine {
    override var lastError: String? = null

    override suspend fun synthesize(text: String, config: TtsConfig): SpeechPayload? {
        lastError = null
        if (config.apiKey.isBlank() || config.voiceId.isBlank()) {
            lastError = "Fish Audio needs an API key and voice reference id."
            return null
        }
        val payload = JSONObject().apply {
            put("text", text)
            put("reference_id", config.voiceId)
            put("format", "pcm")
            put("sample_rate", PCM_SAMPLE_RATE)
            put("latency", "balanced")
            put("normalize", true)
            put(
                "prosody",
                JSONObject().put("speed", config.speed.toDouble()),
            )
        }
        val request = Request.Builder()
            .url("https://api.fish.audio/v1/tts")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("model", MODEL)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val call = client.newCall(request)
        return try {
            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "Fish Audio returned HTTP ${response.code}: " +
                            response.body?.string()?.take(200).orEmpty()
                        return@withContext null
                    }
                    val bytes = response.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        lastError = "Fish Audio returned an empty response."
                        return@withContext null
                    }
                    SpeechPayload.Pcm(bytes, PCM_SAMPLE_RATE)
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: Exception) {
            call.cancel()
            lastError = "${e::class.simpleName}: ${e.message ?: "unknown error"}"
            null
        }
    }

    private companion object {
        const val PCM_SAMPLE_RATE = 24000
        const val MODEL = "s2.1-pro-free"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
