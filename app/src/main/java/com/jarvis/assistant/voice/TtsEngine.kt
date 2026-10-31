package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.jarvis.assistant.settings.VoiceProvider
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class TtsConfig(
    val provider: VoiceProvider,
    val apiKey: String,
    val voiceId: String,
    val model: String = "",
    val speed: Float = 1.0f,
    val fallbackToSystem: Boolean = true,
    val language: String = "en",
)

/** One piece of raw 16-bit mono PCM, 24 kHz unless the provider says otherwise. */
data class AudioChunk(
    val bytes: ByteArray,
    val sampleRate: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is AudioChunk && sampleRate == other.sampleRate && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + sampleRate
}

/**
 * A cloud voice.
 *
 * [synthesize] emits raw PCM as it arrives so playback can start on the first
 * chunk of the first sentence. Implementations own only the network request;
 * playback belongs to [AudioPlayer]. On failure the flow completes without
 * chunks and [lastError] explains why, in words fit to be shown and spoken.
 */
interface TtsEngine {
    var lastError: String?

    fun synthesize(text: String, config: TtsConfig): Flow<AudioChunk>
}

/** Fade length in mono 16-bit frames. 20 ms is enough to stop a click. */
private const val FADE_FRAMES = 480

/**
 * Ramps the last [FADE_FRAMES] samples of a little-endian 16-bit buffer down to
 * silence. Fading per sample (not per byte) is what keeps the tail from turning
 * into noise, and it is applied only to the final chunk of an utterance so
 * speech inside a sentence is never dipped.
 */
fun fadeOutPcm(pcm: ByteArray) {
    val frames = pcm.size / 2
    if (frames <= 0) return
    val fade = minOf(FADE_FRAMES, frames)
    val startFrame = frames - fade
    for (i in 0 until fade) {
        val factor = 1f - (i.toFloat() / fade)
        val index = (startFrame + i) * 2
        val raw = (pcm[index].toInt() and 0xFF) or (pcm[index + 1].toInt() shl 8)
        val scaled = (raw.toShort().toInt() * factor).roundToInt().coerceIn(-32768, 32767)
        pcm[index] = (scaled and 0xFF).toByte()
        pcm[index + 1] = ((scaled shr 8) and 0xFF).toByte()
    }
}

/**
 * Owns a single [AudioTrack] for one spoken reply, so sentence N+1 continues in
 * the same buffer with no click or gap after sentence N.
 *
 * Every entry point is safe from any state: a released, stopped or uninitialised
 * session is ignored rather than thrown, because Stop and barge-in race the
 * audio thread by design.
 */
class AudioPlayer(private val context: Context) {

    private class Session(val track: AudioTrack, val sampleRate: Int) {
        var framesWritten: Long = 0L

        @Volatile
        var released: Boolean = false
    }

    private val lock = Any()
    private var session: Session? = null

    /** Bumped by [stop] so an in-flight write aborts instead of touching a dead track. */
    private var generation: Long = 0L

    @Volatile
    var lastError: String? = null
        private set

    /** True while the speaker is actually producing sound. */
    val isSpeaking: Boolean
        get() = synchronized(lock) {
            val current = session ?: return@synchronized false
            !current.released && current.track.playState == AudioTrack.PLAYSTATE_PLAYING
        }

    /**
     * Appends PCM to the current reply, opening a track at [sampleRate] if
     * needed. Blocking writes pace the caller to real time, which is exactly the
     * backpressure the synthesis loop needs.
     */
    suspend fun write(bytes: ByteArray, sampleRate: Int, fadeOut: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext true
            val target = ensureSession(sampleRate) ?: return@withContext false
            val gen = synchronized(lock) { generation }
            val data = if (fadeOut) bytes.copyOf().also { fadeOutPcm(it) } else bytes
            writeFrames(target, data, gen)
        }

    /** Waits (bounded) for the buffered audio to play, then frees the track. */
    suspend fun finish() = withContext(Dispatchers.IO) {
        val target = synchronized(lock) { session } ?: return@withContext
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
            if (current != null && !current.released && current.sampleRate == sampleRate) {
                current
            } else {
                if (current != null) {
                    session = null
                    releaseSession(current)
                }
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

        val started = runCatching { track.play() }.isSuccess &&
            track.playState == AudioTrack.PLAYSTATE_PLAYING
        if (!started) {
            lastError = "Audio output could not be started."
            runCatching { track.release() }
            return null
        }

        val newSession = Session(track, sampleRate)
        synchronized(lock) { session = newSession }
        return newSession
    }

    private fun writeFrames(target: Session, data: ByteArray, gen: Long): Boolean {
        var offset = 0
        while (offset < data.size) {
            synchronized(lock) {
                if (generation != gen || target.released) return false
            }
            val written = try {
                target.track.write(data, offset, data.size - offset)
            } catch (e: IllegalStateException) {
                lastError = "Audio output was closed: ${e.message}"
                return false
            }
            when {
                written < 0 -> {
                    lastError = "Audio output error code $written"
                    return false
                }
                written == 0 -> Thread.sleep(5)
                else -> {
                    offset += written
                    synchronized(lock) {
                        if (!target.released) target.framesWritten += written / 2
                    }
                }
            }
        }
        return true
    }

    private fun drain(target: Session) {
        val deadline = System.currentTimeMillis() + MAX_DRAIN_MS
        while (System.currentTimeMillis() < deadline) {
            val written = synchronized(lock) {
                if (target.released) return
                target.framesWritten
            }
            if (written <= 0L) return
            val head = target.track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (head >= written) return
            Thread.sleep(20)
        }
    }

    private fun releaseSession(target: Session) {
        if (target.released) return
        target.released = true
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

    private companion object {
        /** Never hang a reply because a device reports a stalled playback head. */
        const val MAX_DRAIN_MS = 30_000L
    }
}
