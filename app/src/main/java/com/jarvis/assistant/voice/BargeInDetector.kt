package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BargeInDetector(private val context: Context) {
    private var job: Job? = null

    fun start(onSpeech: () -> Unit): Job {
        stop()
        val newJob = CoroutineScope(Dispatchers.IO).launch {
            val sampleRate = 16000
            val channel = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
            if (minBuffer <= 0) return@launch

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channel,
                encoding,
                minBuffer * 2,
            )
            val aec = AcousticEchoCanceler.create(recorder.audioSessionId)
            aec?.enabled = true

            val buffer = ShortArray(minBuffer)
            var consecutive = 0
            try {
                recorder.startRecording()
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    var sum = 0.0
                    for (i in 0 until read) {
                        sum += buffer[i].toDouble() * buffer[i].toDouble()
                    }
                    val rms = kotlin.math.sqrt(sum / read.toDouble())
                    if (rms > 1200.0) {
                        consecutive++
                        if (consecutive >= 2) {
                            onSpeech()
                            break
                        }
                    } else {
                        consecutive = 0
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
                aec?.release()
            }
        }
        job = newJob
        return newJob
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}