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

class FishAudioTtsEngine(private val client: OkHttpClient) : TtsEngine {
    override suspend fun speak(text: String, config: TtsConfig, player: AudioPlayer): Boolean {
        if (config.apiKey.isBlank() || config.voiceId.isBlank()) return false
        val payload = JSONObject().apply {
            put("text", text)
            put("reference_id", config.voiceId)
            put("format", "pcm")
            put("sample_rate", 24000)
            put("latency", "balanced")
            put("normalize", true)
        }
        val request = Request.Builder()
            .url("https://api.fish.audio/v1/tts")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("model", "s2.1-pro")
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