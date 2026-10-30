package com.jarvis.assistant.voice

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ElevenLabsTtsEngine(private val client: OkHttpClient) : TtsEngine {
    override var lastError: String? = null
        private set

    override suspend fun speak(text: String, config: TtsConfig, player: AudioPlayer): Boolean {
        lastError = null
        if (config.apiKey.isBlank() || config.voiceId.isBlank()) {
            lastError = "ElevenLabs needs an API key and voice id."
            return false
        }
        val voiceSettings = JSONObject().apply {
            put("stability", 0.5)
            put("similarity_boost", 0.75)
            put("style", 0.0)
            put("use_speaker_boost", true)
            if (config.speed != 1.0f) put("speed", config.speed.toDouble())
        }
        val payload = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_flash_v2_5")
            put("voice_settings", voiceSettings)
        }
        val url = "https://api.elevenlabs.io/v1/text-to-speech/${config.voiceId}/stream?output_format=pcm_24000"
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
                        lastError = "ElevenLabs returned HTTP ${response.code}: ${response.body?.string()?.take(200).orEmpty()}"
                        return@withContext false
                    }
                    val body = response.body
                    if (body == null) {
                        lastError = "ElevenLabs returned an empty response."
                        return@withContext false
                    }
                    val chunks: Flow<TtsAudioChunk> = flow {
                        val source = body.source()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) break
                            emit(TtsAudioChunk(buffer.copyOf(read), 24000))
                        }
                    }.flowOn(Dispatchers.IO)
                    val played = player.playPcm(chunks, 24000)
                    if (!played) {
                        lastError = "ElevenLabs audio playback failed."
                    }
                    played
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: Exception) {
            call.cancel()
            lastError = "${e::class.simpleName}: ${e.message ?: "unknown error"}"
            false
        } finally {
            call.cancel()
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}