package com.cadence.songs.music

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/** Un événement musical : une note, un accord (plusieurs hauteurs) ou un silence (aucune hauteur). */
class ScoreEvent(
    /** Numéro de mesure (0 = première). */
    val measure: Int,
    /** Position depuis le début, en noires. */
    val start: Double,
    /** Durée en noires. */
    val dur: Double,
    /** Hauteurs MIDI (60 = Do central). Vide pour un silence. */
    val pitches: IntArray,
    /** Hauteurs « tonales » MuseScore (14 = Do, 15 = Sol…), même taille que [pitches] ; sert à écrire Do♯ plutôt que Ré♭. */
    val tpcs: IntArray,
    val lyric: String?,
    /** Voix MuseScore (1 à 4). */
    val voice: Int
) {
    val isRest: Boolean get() = pitches.isEmpty()
}

class ScoreTrack(val name: String, val events: List<ScoreEvent>)

class Score(
    val title: String,
    val composer: String,
    /** Armure : −7 (sept bémols) à +7 (sept dièses). */
    val fifths: Int,
    val beats: Int,
    val beatType: Int,
    val tracks: List<ScoreTrack>,
    /** Début de chaque mesure, en noires ; le dernier élément est la fin de la partition. */
    val measureStarts: List<Double>,
    /** Changements de tempo : (position en noires, noires par minute). Toujours au moins un élément à 0. */
    val tempos: List<Pair<Double, Double>>,
    /** Symboles d'accords : (position en noires, symbole). */
    val harmonies: List<Pair<Double, String>>
) {
    val measureCount: Int get() = (measureStarts.size - 1).coerceAtLeast(0)
    val totalBeats: Double get() = measureStarts.lastOrNull() ?: 0.0
    val firstTempo: Int get() = tempos.firstOrNull()?.second?.toInt() ?: 100

    /** Durée en secondes pour aller du début jusqu'à [beat], avec un facteur de vitesse [scale] (1 = tempo écrit). */
    fun beatToSeconds(beat: Double, scale: Double = 1.0): Double {
        var seconds = 0.0
        var cursor = 0.0
        var bpm = tempos.first().second
        for (i in 1 until tempos.size) {
            val (pos, newBpm) = tempos[i]
            if (pos >= beat) break
            seconds += (pos - cursor) * 60.0 / (bpm * scale)
            cursor = pos
            bpm = newBpm
        }
        return seconds + (beat - cursor) * 60.0 / (bpm * scale)
    }

    fun secondsToBeat(sec: Double, scale: Double = 1.0): Double {
        var seconds = 0.0
        var cursor = 0.0
        var bpm = tempos.first().second
        for (i in 1 until tempos.size) {
            val (pos, newBpm) = tempos[i]
            val segment = (pos - cursor) * 60.0 / (bpm * scale)
            if (seconds + segment >= sec) break
            seconds += segment
            cursor = pos
            bpm = newBpm
        }
        return cursor + (sec - seconds) * bpm * scale / 60.0
    }

    fun measureAtBeat(beat: Double): Int {
        var m = 0
        for (i in 0 until measureCount) if (measureStarts[i] <= beat + 1e-6) m = i
        return m
    }

    // ---------- Sauvegarde dans la base (JSON compact) ----------

    fun toJson(): String {
        val o = JSONObject()
        o.put("title", title).put("composer", composer).put("fifths", fifths)
        o.put("beats", beats).put("beatType", beatType)
        o.put("measures", JSONArray(measureStarts))
        o.put("tempos", JSONArray().also { a -> tempos.forEach { a.put(JSONArray(listOf(it.first, it.second))) } })
        o.put("harmonies", JSONArray().also { a -> harmonies.forEach { a.put(JSONArray(listOf(it.first, it.second))) } })
        val tr = JSONArray()
        for (t in tracks) {
            val ev = JSONArray()
            for (e in t.events) {
                ev.put(
                    JSONArray()
                        .put(e.measure).put(e.start).put(e.dur)
                        .put(JSONArray(e.pitches.toList())).put(JSONArray(e.tpcs.toList()))
                        .put(e.lyric ?: "").put(e.voice)
                )
            }
            tr.put(JSONObject().put("name", t.name).put("events", ev))
        }
        o.put("tracks", tr)
        return o.toString()
    }

    companion object {
        fun fromJson(text: String): Score? = runCatching {
            if (text.isBlank()) return null
            val o = JSONObject(text)
            val ms = o.getJSONArray("measures")
            val tempoArr = o.getJSONArray("tempos")
            val harmArr = o.optJSONArray("harmonies") ?: JSONArray()
            val tracksArr = o.getJSONArray("tracks")
            val tracks = (0 until tracksArr.length()).map { ti ->
                val to = tracksArr.getJSONObject(ti)
                val ea = to.getJSONArray("events")
                ScoreTrack(
                    to.optString("name", "Portée ${ti + 1}"),
                    (0 until ea.length()).map { i ->
                        val a = ea.getJSONArray(i)
                        val p = a.getJSONArray(3)
                        val t = a.getJSONArray(4)
                        ScoreEvent(
                            measure = a.getInt(0),
                            start = a.getDouble(1),
                            dur = a.getDouble(2),
                            pitches = IntArray(p.length()) { p.getInt(it) },
                            tpcs = IntArray(t.length()) { t.getInt(it) },
                            lyric = a.getString(5).ifEmpty { null },
                            voice = a.getInt(6)
                        )
                    }
                )
            }
            Score(
                title = o.optString("title"),
                composer = o.optString("composer"),
                fifths = o.optInt("fifths"),
                beats = o.optInt("beats", 4),
                beatType = o.optInt("beatType", 4),
                tracks = tracks,
                measureStarts = (0 until ms.length()).map { ms.getDouble(it) },
                tempos = (0 until tempoArr.length()).map {
                    val a = tempoArr.getJSONArray(it)
                    a.getDouble(0) to a.getDouble(1)
                },
                harmonies = (0 until harmArr.length()).map {
                    val a = harmArr.getJSONArray(it)
                    a.getDouble(0) to a.getString(1)
                }
            )
        }.getOrNull()
    }
}

/** Une case d'une mesure en solfège. */
data class SolfegeCell(
    val text: String,
    val duration: String,
    val lyric: String,
    val rest: Boolean,
    /** Symbole d'accord brut (non transposé) posé sur cette case, ou null. */
    val chord: String?,
    val startBeat: Double
)

data class SolfegeMeasure(val number: Int, val startBeat: Double, val cells: List<SolfegeCell>)

object Solfege {
    private val FIXED_SHARP = arrayOf("Do", "Do♯", "Ré", "Ré♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")
    private val FIXED_FLAT = arrayOf("Do", "Ré♭", "Ré", "Mi♭", "Mi", "Fa", "Sol♭", "Sol", "La♭", "La", "Si♭", "Si")
    private val LETTER_NAME = mapOf('C' to "Do", 'D' to "Ré", 'E' to "Mi", 'F' to "Fa", 'G' to "Sol", 'A' to "La", 'B' to "Si")
    private val MOBILE = arrayOf("Do", "Do♯", "Ré", "Mi♭", "Mi", "Fa", "Fa♯", "Sol", "La♭", "La", "Si♭", "Si")
    private val FLAT_MAJOR_TONICS = setOf(5, 10, 3, 8, 1, 6)

    /** Lettre (C..B) et altération (−2..2) d'une hauteur tonale MuseScore. */
    fun tpcLetter(tpc: Int): Char = "FCGDAEB"[Math.floorMod(tpc - 14 + 1, 7)]
    fun tpcAccidental(tpc: Int): Int = Math.floorDiv(tpc + 1, 7) - 2

    /** Nom anglais d'une hauteur tonale : 14 → "C", 21 → "F#", 12 → "Bb". */
    fun tpcName(tpc: Int): String {
        val acc = tpcAccidental(tpc)
        return tpcLetter(tpc).toString() + (if (acc > 0) "#".repeat(acc) else "b".repeat(-acc))
    }

    private fun marks(octave: Int): String = when {
        octave > 0 -> "'".repeat(octave.coerceAtMost(4))
        octave < 0 -> ",".repeat((-octave).coerceAtMost(4))
        else -> ""
    }

    private fun tonicPitchClass(fifths: Int): Int = ((fifths * 7) % 12 + 12) % 12

    /** Écrit une hauteur en solfège. [transpose] en demi-tons ; [fixed] = Do fixe, sinon Do mobile. */
    fun noteName(midi: Int, tpc: Int, transpose: Int, fixed: Boolean, fifths: Int): String {
        val tonic = tonicPitchClass(fifths)
        if (!fixed) {
            val rel = midi - tonic // la transposition ne change rien en Do mobile
            val name = MOBILE[Math.floorMod(rel, 12)]
            return name + marks(Math.floorDiv(rel, 12) - 5)
        }
        val shifted = midi + transpose
        val octave = Math.floorDiv(shifted, 12) - 1 - 4
        if (transpose == 0 && tpc > -90) {
            val acc = tpcAccidental(tpc)
            val base = LETTER_NAME[tpcLetter(tpc)] ?: "Do"
            val sign = if (acc > 0) "♯".repeat(acc) else "♭".repeat(-acc)
            // L'octave suit la note écrite (Do♭4 est dans l'octave 4 même si son MIDI est 59).
            val letterPc = when (tpcLetter(tpc)) { 'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; else -> 11 }
            val written = letterPc + acc
            return base + sign + marks(Math.floorDiv(midi - written, 12) - 1 - 4)
        }
        val newTonic = (tonic + transpose + 1200) % 12
        val flats = newTonic in FLAT_MAJOR_TONICS || (fifths < 0 && transpose == 0)
        val names = if (flats) FIXED_FLAT else FIXED_SHARP
        return names[Math.floorMod(shifted, 12)] + marks(octave)
    }

    fun durationLabel(d: Double): String {
        val labels = mapOf(0.0625 to "1/16", 0.125 to "⅛", 0.25 to "¼", 0.375 to "⅜", 0.5 to "½", 0.75 to "¾", 1.0 to "1", 1.5 to "1½", 2.0 to "2", 3.0 to "3", 4.0 to "4", 6.0 to "6", 8.0 to "8")
        for ((k, v) in labels) if (abs(k - d) < 1e-3) return v
        if (abs(d - Math.rint(d)) < 1e-3) return Math.rint(d).toLong().toString()
        return String.format(Locale.ROOT, "%.2f", d)
    }

    /** Nom de la tonalité majeure correspondant à l'armure (Do, Sol, Ré, …). */
    fun keyName(fifths: Int): String {
        val sharp = arrayOf("C", "G", "D", "A", "E", "B", "F#", "C#")
        val flat = arrayOf("C", "F", "Bb", "Eb", "Ab", "Db", "Gb", "Cb")
        return if (fifths >= 0) sharp[fifths.coerceAtMost(7)] else flat[(-fifths).coerceAtMost(7)]
    }

    /** Mesures d'une portée converties en solfège (voix principale uniquement ; les accords de piano sont joints par « + »). */
    fun measures(score: Score, trackIndex: Int, transpose: Int, fixed: Boolean): List<SolfegeMeasure> {
        val track = score.tracks.getOrNull(trackIndex) ?: return emptyList()
        val mainVoice = track.events.minOfOrNull { it.voice } ?: 1
        val events = track.events.filter { it.voice == mainVoice }.sortedBy { it.start }
        val harmonies = score.harmonies.sortedBy { it.first }
        val usedHarmony = BooleanArray(harmonies.size)
        val out = mutableListOf<SolfegeMeasure>()
        for (mi in 0 until score.measureCount) {
            val cells = mutableListOf<SolfegeCell>()
            for (e in events) {
                if (e.measure != mi) continue
                val text = if (e.isRest) {
                    "–"
                } else {
                    e.pitches.indices.sortedByDescending { e.pitches[it] }.take(4).joinToString("+") { i ->
                        noteName(e.pitches[i], e.tpcs.getOrElse(i) { -99 }, transpose, fixed, score.fifths)
                    }
                }
                var chord: String? = null
                for (h in harmonies.indices) {
                    if (usedHarmony[h]) continue
                    val pos = harmonies[h].first
                    if (pos < e.start + e.dur - 1e-6 && pos >= e.start - 1e-6) {
                        chord = harmonies[h].second
                        usedHarmony[h] = true
                        break
                    }
                }
                cells.add(SolfegeCell(text, durationLabel(e.dur), e.lyric.orEmpty(), e.isRest, chord, e.start))
            }
            out.add(SolfegeMeasure(mi + 1, score.measureStarts[mi], cells))
        }
        return out
    }

    /** Tonalité (anglais) de la partition pour remplir la fiche du morceau. */
    fun songKey(score: Score): String = keyName(score.fifths)

    /**
     * Texte ChordPro (paroles + accords) tiré de la partition, ou chaîne vide si elle n'a ni paroles ni accords.
     * La première portée qui porte des paroles sert de mélodie.
     */
    fun toChordPro(score: Score): String {
        val src = score.tracks.firstOrNull { t -> t.events.any { !it.lyric.isNullOrBlank() } }
            ?: score.tracks.firstOrNull() ?: return ""
        val harmonies = score.harmonies.sortedBy { it.first }
        val hasLyrics = src.events.any { !it.lyric.isNullOrBlank() }
        if (harmonies.isEmpty() && !hasLyrics) return ""
        val mainVoice = src.events.minOfOrNull { it.voice } ?: 1
        val events = src.events.filter { it.voice == mainVoice && (!it.isRest || !it.lyric.isNullOrBlank()) }.sortedBy { it.start }
        val sb = StringBuilder()
        val line = StringBuilder()
        var hi = 0
        var group = -1
        fun flush() {
            if (line.isNotBlank()) sb.append(line.toString().trimEnd()).append('\n')
            line.setLength(0)
        }
        for (e in events) {
            val g = e.measure / 4
            if (g != group) { flush(); group = g }
            while (hi < harmonies.size && harmonies[hi].first <= e.start + 1e-6) {
                line.append('[').append(harmonies[hi].second).append(']')
                hi++
            }
            val ly = e.lyric
            if (ly != null) {
                val joined = ly.endsWith("-")
                line.append(ly.removeSuffix("-"))
                if (!joined) line.append(' ')
            } else if (line.endsWith("]")) {
                line.append(' ')
            }
        }
        flush()
        return sb.toString().trimEnd()
    }
}
