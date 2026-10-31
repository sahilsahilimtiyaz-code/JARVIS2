package com.jarvis.assistant.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ElevenLabsTtsEngine(private val client: OkHttpClient) : TtsEngine {
    override var lastError: String? = null

    override suspend fun synthesize(text: String, config: TtsConfig): SpeechPayload? {
        lastError = null
        if (config.apiKey.isBlank() || config.voiceId.isBlank()) {
            lastError = "ElevenLabs needs an API key and voice id."
            return null
        }
        val voiceSettings = JSONObject().apply {
            put("stability", 0.5)
            put("similarity_boost", 0.75)
            put("style", 0.0)
            put("use_speaker_boost", true)
            put("speed", config.speed.toDouble())
        }
        val payload = JSONObject().apply {
            put("text", text)
            put("model_id", MODEL)
            put("voice_settings", voiceSettings)
        }
        val url = "https://api.elevenlabs.io/v1/text-to-speech/${config.voiceId}/stream" +
            "?output_format=pcm_$PCM_SAMPLE_RATE"
        val request = Request.Builder()
            .url(url)
            .header("xi-api-key", config.apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "audio/pcm")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val call = client.newCall(request)
        return try {
            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "ElevenLabs returned HTTP ${response.code}: " +
                            response.body?.string()?.take(200).orEmpty()
                        return@withContext null
                    }
                    val bytes = response.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        lastError = "ElevenLabs returned an empty response."
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
        const val MODEL = "eleven_flash_v2_5"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
