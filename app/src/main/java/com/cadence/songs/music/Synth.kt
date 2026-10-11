package com.cadence.songs.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Petit synthétiseur sans fichier de sons : chaque instrument est calculé (synthèse additive ou corde pincée).
 * La partition entière est rendue en mémoire puis jouée par [ScorePlayer].
 */
object Synth {
    const val SR = 22050
    private const val MAX_SECONDS = 420

    val INSTRUMENTS = listOf("Piano", "Guitare", "Orgue", "Flûte", "Violon", "Marimba", "Basse", "Trompette")

    private class Voice(
        val harmonics: FloatArray,
        val attack: Float,
        /** Constante de temps de la décroissance (s) ; 0 = son tenu. */
        val tau: Float,
        val release: Float,
        val vibratoHz: Float,
        val vibratoDepth: Float,
        val breath: Float,
        val gain: Float
    )

    private val VOICES = arrayOf(
        // Piano
        Voice(floatArrayOf(1f, 0.55f, 0.28f, 0.16f, 0.09f, 0.05f), 0.004f, 1.1f, 0.25f, 0f, 0f, 0f, 1.0f),
        // Guitare : cordes pincées (voir pluck), paramètres non utilisés
        Voice(floatArrayOf(1f), 0f, 0f, 0.2f, 0f, 0f, 0f, 1.0f),
        // Orgue
        Voice(floatArrayOf(1f, 0.7f, 0.25f, 0.4f, 0f, 0.2f, 0f, 0.12f), 0.015f, 0f, 0.05f, 0f, 0f, 0f, 0.55f),
        // Flûte
        Voice(floatArrayOf(1f, 0.18f, 0.06f), 0.06f, 0f, 0.1f, 5.2f, 0.006f, 0.035f, 0.9f),
        // Violon
        Voice(floatArrayOf(1f, 0.5f, 0.33f, 0.25f, 0.2f, 0.16f, 0.14f, 0.12f, 0.1f), 0.09f, 0f, 0.12f, 5.6f, 0.01f, 0.01f, 0.5f),
        // Marimba
        Voice(floatArrayOf(1f, 0f, 0f, 0.12f, 0f, 0f, 0.04f), 0.003f, 0.22f, 0.04f, 0f, 0f, 0f, 1.0f),
        // Basse
        Voice(floatArrayOf(1f, 0.6f, 0.3f, 0.12f), 0.01f, 0.7f, 0.12f, 0f, 0f, 0f, 1.0f),
        // Trompette
        Voice(floatArrayOf(1f, 0.85f, 0.7f, 0.55f, 0.4f, 0.3f, 0.2f, 0.12f), 0.035f, 0f, 0.06f, 5f, 0.004f, 0f, 0.45f)
    )

    private val TABLE = FloatArray(4096) { sin(2.0 * PI * it / 4096.0).toFloat() }

    private fun sine(phase: Double): Float {
        val x = phase - floor(phase)
        return TABLE[(x * 4096.0).toInt() and 4095]
    }

    /**
     * Rend la partition en PCM 16 bits mono. Renvoie null si la partition dépasse 7 minutes.
     * [programs] : numéro d'instrument par portée ; [muted] : portées à ne pas jouer.
     */
    fun render(
        score: Score,
        programs: Map<Int, Int>,
        muted: Set<Int>,
        tempoScale: Double,
        transpose: Int
    ): ShortArray? {
        val total = score.beatToSeconds(score.totalBeats, tempoScale) + 2.5
        if (total > MAX_SECONDS) return null
        val n = (total * SR).toInt()
        val mix = FloatArray(n)
        for ((ti, track) in score.tracks.withIndex()) {
            if (ti in muted) continue
            val inst = (programs[ti] ?: 0).coerceIn(0, VOICES.size - 1)
            for (e in track.events) {
                if (e.isRest) continue
                val t0 = score.beatToSeconds(e.start, tempoScale)
                val t1 = score.beatToSeconds(e.start + e.dur, tempoScale)
                val amp = 0.5f / e.pitches.size.coerceAtLeast(1).toFloat().pow(0.6f)
                for (p in e.pitches) addNote(mix, inst, p + transpose, t0, (t1 - t0).coerceAtLeast(0.05), amp)
            }
        }
        var peak = 0f
        for (v in mix) { val a = if (v < 0) -v else v; if (a > peak) peak = a }
        val scale = if (peak > 0f) 0.85f / peak else 1f
        return ShortArray(n) { (mix[it] * scale * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun addNote(mix: FloatArray, instrument: Int, midi: Int, t0: Double, dur: Double, amp: Float) {
        val freq = 440.0 * 2.0.pow((midi - 69) / 12.0)
        if (freq < 20.0 || freq > SR / 2.2) return
        val v = VOICES[instrument]
        val start = (t0 * SR).toInt()
        if (start >= mix.size) return
        val held = (dur * SR).toInt().coerceAtLeast(SR / 20)
        val releaseN = (v.release * SR).toInt().coerceAtLeast(1)
        var length = held + releaseN
        if (v.tau > 0f) length = minOf(length, (v.tau * 6f * SR).toInt() + releaseN)
        if (instrument == 1) length = minOf(held + releaseN, (3.5 * SR).toInt())
        length = minOf(length, mix.size - start)
        if (length <= 0) return

        if (instrument == 1) {
            pluck(mix, start, length, held, releaseN, freq, amp)
            return
        }
        val attackN = (v.attack * SR).toInt().coerceAtLeast(1)
        val maxHarm = minOf(v.harmonics.size, (SR / 2.2 / freq).toInt())
        var phase = 0.0
        val noise = if (v.breath > 0f) Random(midi * 7919 + start) else null
        for (i in 0 until length) {
            val tt = i.toDouble() / SR
            var env = if (i < attackN) i.toFloat() / attackN else 1f
            if (v.tau > 0f) env *= exp(-tt / v.tau).toFloat()
            if (i > held) env *= 1f - (i - held).toFloat() / releaseN
            val vib = if (v.vibratoDepth > 0f && tt > 0.12) 1.0 + v.vibratoDepth * sine(v.vibratoHz * tt) else 1.0
            phase += freq * vib / SR
            var s = 0f
            for (k in 0 until maxHarm) {
                val w = v.harmonics[k]
                if (w != 0f) s += w * sine(phase * (k + 1))
            }
            if (noise != null) s += (noise.nextFloat() * 2f - 1f) * v.breath
            mix[start + i] += s * env * amp * v.gain
        }
    }

    /** Corde pincée (Karplus-Strong) pour la guitare. */
    private fun pluck(mix: FloatArray, start: Int, length: Int, held: Int, releaseN: Int, freq: Double, amp: Float) {
        val period = (SR / freq).toInt().coerceAtLeast(2)
        val buf = FloatArray(period)
        val rnd = Random(period * 31 + start)
        var prev = 0f
        for (i in 0 until period) {
            val raw = rnd.nextFloat() * 2f - 1f
            prev = 0.6f * prev + 0.4f * raw // bruit adouci : son plus rond
            buf[i] = prev
        }
        var idx = 0
        for (i in 0 until length) {
            val cur = buf[idx]
            val next = buf[(idx + 1) % period]
            buf[idx] = 0.5f * (cur + next) * 0.9965f
            idx = (idx + 1) % period
            var env = 1f
            if (i > held) env = 1f - (i - held).toFloat() / releaseN
            mix[start + i] += cur * env * amp * 1.6f
        }
    }
}

/** Joue un rendu PCM produit par [Synth.render] (mode statique : toute la partition tient en mémoire). */
class ScorePlayer {
    private var track: AudioTrack? = null
    private var frames = 0

    val loaded: Boolean get() = track != null

    fun load(samples: ShortArray) {
        release()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Synth.SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(samples, 0, samples.size)
        track = t
        frames = samples.size
    }

    fun play() { runCatching { track?.play() } }

    fun pause() { runCatching { track?.pause() } }

    /** Arrête et revient au début. */
    fun stop() {
        runCatching {
            track?.stop()
            track?.reloadStaticData()
        }
    }

    fun isPlaying(): Boolean = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    fun positionSeconds(): Double = (track?.playbackHeadPosition ?: 0).toDouble() / Synth.SR

    fun finished(): Boolean = loaded && (track?.playbackHeadPosition ?: 0) >= frames - 64

    fun seekTo(seconds: Double) {
        val t = track ?: return
        val wasPlaying = isPlaying()
        runCatching {
            t.pause()
            t.playbackHeadPosition = (seconds * Synth.SR).toInt().coerceIn(0, frames - 1)
            if (wasPlaying) t.play()
        }
    }

    fun release() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        frames = 0
    }
}
