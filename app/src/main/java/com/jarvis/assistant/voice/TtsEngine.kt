package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jarvis.assistant.settings.VoiceProvider
import kotlin.math.roundToInt

data class TtsConfig(
    val provider: VoiceProvider,
    val apiKey: String,
    val voiceId: String,
    val speed: Float = 1.0f,
    val fallbackToSystem: Boolean = true,
    val language: String = "en",
)

sealed interface SpeechPayload {
    data class Pcm(val bytes: ByteArray, val sampleRate: Int) : SpeechPayload
    data class SystemText(val text: String) : SpeechPayload
    data object EndOfReply : SpeechPayload
}

interface TtsEngine {
    var lastError: String?

    /**
     * Produces audio for [text]. Returns null when synthesis failed; callers
     * should then fall back to the system engine or surface [lastError].
     *
     * Implementations must not start playback themselves. Playback is owned by
     * [AudioPlayer] so a single AudioTrack session can span several sentences
     * without a gap or a click between them.
     */
    suspend fun synthesize(text: String, config: TtsConfig): SpeechPayload?
}

/** Fade length in mono 16-bit frames. 20 ms is enough to stop a click. */
private const val FADE_FRAMES = 480

/**
 * Ramps the last [FADE_FRAMES] samples of a little-endian 16-bit buffer down
 * to silence. Fading per sample (not per byte) is what keeps the tail from
 * turning into noise.
 */
private fun applyFadeOut(pcm: ByteArray) {
    val frames = pcm.size / 2
    if (frames <= 0) return
    val fade = minOf(FADE_FRAMES, frames)
    val startFrame = frames - fade
    for (i in 0 until fade) {
        // 1.0 at the head of the fade, 0.0 at the very last sample.
        val factor = 1f - (i.toFloat() / fade)
        val index = (startFrame + i) * 2
        val raw = (pcm[index].toInt() and 0xFF) or (pcm[index + 1].toInt() shl 8)
        val scaled = (raw.toShort().toInt() * factor).roundToInt().coerceIn(-32768, 32767)
        pcm[index] = (scaled and 0xFF).toByte()
        pcm[index + 1] = ((scaled shr 8) and 0xFF).toByte()
    }
}

/**
 * Owns a single [AudioTrack] for the whole of one spoken reply.
 *
 * Sentences of a reply share one track, so there is no click or gap between
 * them. [stop] is safe to call from any state and never throws: a session that
 * is already stopped, released or uninitialised is simply ignored.
 */
class AudioPlayer(private val context: Context) {
    private class Session(
        val track: AudioTrack,
        val sampleRate: Int,
    ) {
        var framesWritten: Long = 0
        var stopped: Boolean = false
        var released: Boolean = false
    }

    private val lock = Any()
    private var session: Session? = null

    /** Bumped by [stop] so in-flight writes abort instead of touching a dead track. */
    private var generation: Long = 0

    var lastError: String? = null
        private set

    val isSpeaking: Boolean get() = synchronized(lock) {
        val current = session ?: return@synchronized false
        !current.stopped && current.track.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    /**
     * Opens a session if needed and appends [bytes]. The session stays open
     * after this returns so the next sentence can continue without a gap.
     */
    suspend fun writePcm(bytes: ByteArray, sampleRate: Int): Boolean =
        withContext(Dispatchers.IO) {
            val target = ensureSession(sampleRate) ?: return@withContext false
            val gen = synchronized(lock) { generation }
            write(target, bytes, gen)
        }

    /** Waits for the buffered audio to drain, then closes the session. */
    suspend fun finish() = withContext(Dispatchers.IO) {
        val target = synchronized(lock) { session } ?: return@withContext
        val gen = synchronized(lock) { generation }
        try {
            drain(target)
        } finally {
            synchronized(lock) { releaseSession(target) }
        }
    }

    /** Silences playback immediately and frees the track. Idempotent. */
    fun stop() {
        val target = synchronized(lock) {
            generation++
            val current = session
            session = null
            current
        }
        if (target != null) releaseSession(target)
    }

    private fun ensureSession(sampleRate: Int): Session? {
        val existing = synchronized(lock) {
            val current = session
            if (current != null && !current.stopped && !current.released) {
                if (current.sampleRate != sampleRate) {
                    // Re-opening at a new rate: discard the old track first.
                    releaseSession(current)
                    null
                } else {
                    current
                }
            } else {
                null
            }
        }
        return existing ?: createSession(sampleRate)
    }

    private fun createSession(sampleRate: Int): Session? {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).takeIf { it > 0 } ?: (sampleRate * 4)
        val bufferBytes = maxOf(minBuffer * 4, sampleRate * 8)

        val track = try {
            AudioTrack.Builder()
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
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            lastError = "Could not open audio output: ${e.message}"
            return null
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            lastError = "Audio output could not be initialized."
            return null
        }

        val newSession = Session(track, sampleRate)
        // play() before the first write, otherwise a MODE_STREAM track stays
        // paused and the reply is never heard.
        if (!runCatching { track.play() }.getOrDefault(false)) {
            lastError = "Audio output could not be started."
            runCatching { track.release() }
            return null
        }
        synchronized(lock) { session = newSession }
        return newSession
    }

    private fun write(target: Session, data: ByteArray, gen: Long): Boolean {
        if (data.isEmpty()) return true

        applyFadeOut(data)

        var offset = 0
        while (offset < data.size) {
            synchronized(lock) {
                if (generation != gen || target.stopped || target.released) return false
            }
            val written = try {
                target.track.write(data, offset, data.size - offset)
            } catch (e: IllegalStateException) {
                // A released track lands here. Treat it as "stopped", not a crash.
                lastError = "Audio output was closed: ${e.message}"
                return false
            }
            if (written < 0) {
                lastError = "Audio output error code $written"
                return false
            }
            if (written == 0) {
                Thread.sleep(5)
                continue
            }
            offset += written
            synchronized(lock) { target.framesWritten += written / 2 }
        }
        return true
    }

    private fun drain(target: Session) {
        while (true) {
            val written = synchronized(lock) {
                if (target.stopped || target.released) return@synchronized -1L
                target.framesWritten
            }
            if (written < 0L) return
            if (written == 0L) return
            val head = target.track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (head >= written) return
            Thread.sleep(20)
        }
    }

    private fun releaseSession(target: Session) {
        if (target.released) return
        target.released = true
        target.stopped = true
        val track = target.track
        runCatching {
            if (track.state == AudioTrack.STATE_INITIALIZED &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING
            ) {
                track.stop()
            }
        }
        runCatching {
            if (track.state == AudioTrack.STATE_INITIALIZED) track.release()
        }
    }
}
