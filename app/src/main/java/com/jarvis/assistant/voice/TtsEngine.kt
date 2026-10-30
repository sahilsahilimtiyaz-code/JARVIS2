package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import com.jarvis.assistant.settings.VoiceProvider

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
    var lastError: String?
    suspend fun speak(text: String, config: TtsConfig, player: AudioPlayer): Boolean
}

class AudioPlayer(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private var isStopped = false

    suspend fun playPcm(chunks: Flow<TtsAudioChunk>, sampleRate: Int): Boolean =
        withContext(Dispatchers.IO) {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(sampleRate)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            synchronized(this) {
                if (isStopped) {
                    track.release()
                    return@withContext false
                }
                audioTrack = track
                isStopped = false
            }
            track.play()
            var completed = true
            try {
                chunks.collect { chunk ->
                    synchronized(this) {
                        if (isStopped || audioTrack !== track) {
                            completed = false
                            return@collect
                        }
                    }
                    var offset = 0
                    while (offset < chunk.pcm.size) {
                        synchronized(this) {
                            if (isStopped || audioTrack !== track) {
                                completed = false
                                return@collect
                            }
                        }
                        val written = track.write(chunk.pcm, offset, chunk.pcm.size - offset)
                        if (written <= 0) break
                        offset += written
                    }
                }
                synchronized(this) {
                    if (isStopped || audioTrack !== track) {
                        completed = false
                    }
                }
                if (completed) track.stop()
                completed
            } catch (e: CancellationException) {
                synchronized(this) {
                    if (!isStopped && audioTrack === track) {
                        runCatching { track.stop() }
                    }
                }
                throw e
            } catch (e: IllegalStateException) {
                false
            } finally {
                synchronized(this) {
                    if (audioTrack === track) {
                        runCatching { track.release() }
                        audioTrack = null
                        isStopped = true
                    }
                }
            }
        }

    fun stop() {
        synchronized(this) {
            isStopped = true
            audioTrack?.runCatching { stop() }
            audioTrack?.runCatching { release() }
            audioTrack = null
        }
    }
}