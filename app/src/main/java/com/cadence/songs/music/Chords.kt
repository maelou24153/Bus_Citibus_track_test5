package com.cadence.songs.music

/** Analyse, transposition et notation des accords. Aucune dépendance Android. */
object Chords {
    private val SHARP = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val SOLFEGE_SHARP = arrayOf("Do", "Do#", "Ré", "Ré#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")
    private val SOLFEGE_FLAT = arrayOf("Do", "Réb", "Ré", "Mib", "Mi", "Fa", "Solb", "Sol", "Lab", "La", "Sib", "Si")
    private val NASHVILLE = arrayOf("1", "b2", "2", "b3", "3", "4", "b5", "5", "b6", "6", "b7", "7")

    private val ROOT = Regex("^([A-G][#b]?)(.*)$")
    private val BASS = Regex("^[A-G][#b]?$")
    private val QUALITY =
        Regex("^(?:maj|min|mi|m|M|dim|aug|sus|add|no|[0-9]|#|b|\\+|-|°|ø|\\(|\\)|,)*$")

    data class Parsed(val root: String, val rest: String, val bass: String?)

    fun parse(symbol: String): Parsed? {
        val s = symbol.trim()
        if (s.isEmpty()) return null
        val slash = s.indexOf('/')
        val main = if (slash >= 0) s.substring(0, slash) else s
        val bass = if (slash >= 0) s.substring(slash + 1) else null
        val m = ROOT.matchEntire(main) ?: return null
        val rest = m.groupValues[2]
        if (!QUALITY.matches(rest)) return null
        if (bass != null && !BASS.matches(bass)) return null
        return Parsed(m.groupValues[1], rest, bass)
    }

    fun isChord(symbol: String): Boolean = parse(symbol) != null

    fun index(root: String): Int {
        val base = when (root[0]) {
            'C' -> 0
            'D' -> 2
            'E' -> 4
            'F' -> 5
            'G' -> 7
            'A' -> 9
            else -> 11
        }
        val acc = if (root.length > 1) (if (root[1] == '#') 1 else -1) else 0
        return ((base + acc) % 12 + 12) % 12
    }

    fun noteName(index: Int, flats: Boolean): String {
        val i = ((index % 12) + 12) % 12
        return if (flats) FLAT[i] else SHARP[i]
    }

    fun transpose(chord: String, semitones: Int, flats: Boolean): String {
        if (semitones % 12 == 0) return chord
        val p = parse(chord) ?: return chord
        val root = noteName(index(p.root) + semitones, flats)
        val bass = p.bass?.let { noteName(index(it) + semitones, flats) }
        return root + p.rest + (if (bass != null) "/$bass" else "")
    }

    /** Notation en solfège : C → Do, Am → Lam, F#m7 → Fa#m7, Bb/D → Sib/Ré. Transpose en même temps. */
    fun solfege(chord: String, semitones: Int, flats: Boolean): String {
        val p = parse(chord) ?: return chord
        val names = if (flats) SOLFEGE_FLAT else SOLFEGE_SHARP
        fun nm(root: String) = names[((index(root) + semitones) % 12 + 12) % 12]
        return nm(p.root) + p.rest + (if (p.bass != null) "/" + nm(p.bass) else "")
    }

    /** Notation Nashville : chiffres relatifs à la tonalité (keyIndex = 0..11). */
    fun nashville(chord: String, keyIndex: Int): String {
        val p = parse(chord) ?: return chord
        val degree = NASHVILLE[((index(p.root) - keyIndex) % 12 + 12) % 12]
        val bass = p.bass?.let { NASHVILLE[((index(it) - keyIndex) % 12 + 12) % 12] }
        return degree + p.rest + (if (bass != null) "/$bass" else "")
    }

    private val MAJOR_FLAT_KEYS = setOf(5, 10, 3, 8, 1, 6)
    private val MINOR_FLAT_KEYS = setOf(2, 7, 0, 5, 10, 3)

    fun isMinorKey(key: String): Boolean {
        val p = parse(key) ?: return false
        return p.rest.startsWith("m") && !p.rest.startsWith("maj")
    }

    /** Faut-il écrire les altérations en bémols pour cette tonalité transposée ? */
    fun useFlats(key: String, semitones: Int): Boolean {
        val p = parse(key) ?: return false
        val idx = ((index(p.root) + semitones) % 12 + 12) % 12
        return if (isMinorKey(key)) idx in MINOR_FLAT_KEYS else idx in MAJOR_FLAT_KEYS
    }

    fun transposeKey(key: String, semitones: Int, flats: Boolean): String {
        if (key.isBlank()) return key
        return transpose(key, semitones, flats)
    }

    fun keyIndex(key: String): Int? = parse(key)?.let { index(it.root) }
}
