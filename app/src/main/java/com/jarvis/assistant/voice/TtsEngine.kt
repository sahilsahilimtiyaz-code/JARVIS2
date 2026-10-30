package com.jarvis.assistant.voice

import com.jarvis.assistant.settings.VoiceProvider

import kotlinx.coroutines.flow.Flow

data class TtsConfig(
    val provider: VoiceProvider,
    val apiKey: String,
    val voiceId: String,
    val speed: Float = 1.0f,
    val fallbackToSystem: Boolean = true,
    val language: String = "en",
)

data class TtsAudioChunk(
    val pcm: ByteArray,
    val sampleRate: Int,
)

interface TtsEngine {
    suspend fun speak(text: String, config: TtsConfig, player: AudioPlayer): Boolean
}

class AudioPlayer(private val context: android.content.Context) {
    private var audioTrack: android.media.AudioTrack? = null
    private var currentJob: kotlinx.coroutines.Job? = null

    suspend fun playPcm(chunks: Flow<TtsAudioChunk>, sampleRate: Int): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val minBuffer = android.media.AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(sampleRate)
            val track = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()
            return@withContext try {
                chunks.collect { chunk ->
                    var offset = 0
                    while (offset < chunk.pcm.size) {
                        val written = track.write(chunk.pcm, offset, chunk.pcm.size - offset)
                        if (written <= 0) break
                        offset += written
                    }
                }
                track.stop()
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                track.stop()
                throw e
            } finally {
                track.release()
                if (audioTrack === track) audioTrack = null
            }
        }

    fun stop() {
        audioTrack?.runCatching { stop() }
        audioTrack?.release()
        audioTrack = null
    }
}