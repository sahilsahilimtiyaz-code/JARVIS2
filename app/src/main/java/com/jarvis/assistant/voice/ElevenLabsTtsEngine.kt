package com.jarvis.assistant.voice

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ElevenLabsTtsEngine(private val client: OkHttpClient) : TtsEngine {
    override suspend fun speak(text: String, config: TtsConfig, player: AudioPlayer): Boolean {
        if (config.apiKey.isBlank() || config.voiceId.isBlank()) return false
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
            call.execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                val chunks: Flow<TtsAudioChunk> = flow {
                    val source = body.source()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        emit(TtsAudioChunk(buffer.copyOf(read), 24000))
                    }
                }.flowOn(Dispatchers.IO)
                player.playPcm(chunks, 24000)
                true
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: IOException) {
            call.cancel()
            false
        } finally {
            call.cancel()
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}