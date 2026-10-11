package com.cadence.songs.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Métronome précis : le tempo est cadencé par l'écriture audio (AudioTrack en flux continu),
 * pas par un timer. [onBeat] est appelé sur le thread principal avec l'index du temps (0 = temps fort).
 */
class Metronome(private val onBeat: (Int) -> Unit) {
    @Volatile var bpm: Int = 100
    @Volatile var beats: Int = 4
    @Volatile private var running = false
    @Volatile private var generation = 0
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        if (running) return
        running = true
        val myGeneration = ++generation
        Thread {
            val sampleRate = 44100
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer.coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            var beat = 0
            try {
                while (running && myGeneration == generation) {
                    val tempo = bpm.coerceIn(20, 300)
                    val samples = (sampleRate * 60.0 / tempo).toInt()
                    val buffer = ShortArray(samples)
                    val frequency = if (beat == 0) 1700.0 else 1050.0
                    val clickLength = min(samples, sampleRate / 40)
                    for (i in 0 until clickLength) {
                        val envelope = 1.0 - i.toDouble() / clickLength
                        val value = sin(2.0 * PI * frequency * i / sampleRate) * envelope * 0.8
                        buffer[i] = (value * Short.MAX_VALUE).toInt().toShort()
                    }
                    val current = beat
                    handler.post { onBeat(current) }
                    track.write(buffer, 0, buffer.size)
                    beat = (beat + 1) % beats.coerceAtLeast(1)
                }
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        generation++
    }
}
