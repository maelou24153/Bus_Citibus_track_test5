package com.cadence.songs.music

/**
 * Doigtés d'accords pour guitare et ukulélé. Aucune dépendance Android.
 *
 * [frets] : une valeur par corde, de la plus grave à la plus aiguë (guitare : Mi la ré sol si mi ;
 * ukulélé : sol do mi la). -1 = corde non jouée, 0 = corde à vide, n = numéro de case absolu.
 * [approx] est vrai quand l'accord a été ramené à une forme voisine (ex. « add9 » joué comme l'accord majeur).
 */
data class Shape(val frets: List<Int>, val approx: Boolean)

object ChordShapes {
    private enum class Q { MAJ, MIN, DOM7, MIN7, MAJ7, SUS4, SUS2, DIM, AUG }

    /** Ramène le suffixe d'un accord à une qualité connue ; le booléen indique une approximation. */
    private fun quality(rest: String): Pair<Q, Boolean>? {
        val r = rest.replace("(", "").replace(")", "").replace(",", "").trim()
        return when {
            r == "" || r == "M" || r == "maj" -> Q.MAJ to false
            r == "m" || r == "min" || r == "mi" || r == "-" -> Q.MIN to false
            r == "7" -> Q.DOM7 to false
            r == "m7" || r == "min7" || r == "mi7" || r == "-7" -> Q.MIN7 to false
            r == "maj7" || r == "M7" || r == "Maj7" -> Q.MAJ7 to false
            r == "sus4" || r == "sus" -> Q.SUS4 to false
            r == "sus2" -> Q.SUS2 to false
            r == "dim" || r == "°" -> Q.DIM to false
            r == "aug" || r == "+" -> Q.AUG to false
            r.startsWith("aug") || r.startsWith("+") -> Q.AUG to true
            r.startsWith("maj") -> Q.MAJ7 to true
            r.startsWith("m7") || r.startsWith("min7") -> Q.MIN7 to true
            r.startsWith("m") || r.startsWith("min") || r.startsWith("-") -> Q.MIN to true
            r.startsWith("sus2") -> Q.SUS2 to true
            r.startsWith("sus") -> Q.SUS4 to true
            r.startsWith("dim") -> Q.DIM to true
            r.startsWith("7") || r.startsWith("9") || r.startsWith("11") || r.startsWith("13") -> Q.DOM7 to true
            else -> Q.MAJ to true // add9, 6, 5, 2, 4…
        }
    }

    // ---------- Guitare ----------

    private val GUITAR_OPEN: Map<String, List<Int>> = mapOf(
        "C" to listOf(-1, 3, 2, 0, 1, 0),
        "D" to listOf(-1, -1, 0, 2, 3, 2),
        "G" to listOf(3, 2, 0, 0, 0, 3),
        "Dm" to listOf(-1, -1, 0, 2, 3, 1),
        "C7" to listOf(-1, 3, 2, 3, 1, 0),
        "G7" to listOf(3, 2, 0, 0, 0, 1),
        "D7" to listOf(-1, -1, 0, 2, 1, 2),
        "Cmaj7" to listOf(-1, 3, 2, 0, 0, 0),
        "Dmaj7" to listOf(-1, -1, 0, 2, 2, 2),
        "Gmaj7" to listOf(3, 2, 0, 0, 0, 2),
        "Fmaj7" to listOf(-1, -1, 3, 2, 1, 0),
        "Dsus4" to listOf(-1, -1, 0, 2, 3, 3),
        "Dsus2" to listOf(-1, -1, 0, 2, 3, 0),
        "Csus2" to listOf(-1, 3, 0, 0, 1, 3),
        "Gsus4" to listOf(3, 3, 0, 0, 1, 3),
        "Bm" to listOf(-1, 2, 4, 4, 3, 2),
        "Cm" to listOf(-1, 3, 5, 5, 4, 3),
        "B7" to listOf(-1, 2, 1, 2, 0, 2),
        "Gm" to listOf(3, 5, 5, 3, 3, 3),
        "Dm7" to listOf(-1, -1, 0, 2, 1, 1),
        "Ddim" to listOf(-1, -1, 0, 1, 0, 1)
    )

    // Formes mobiles : racine sur la 6e corde (type Mi) ou sur la 5e corde (type La).
    private val E_SHAPE = mapOf(
        Q.MAJ to listOf(0, 2, 2, 1, 0, 0),
        Q.MIN to listOf(0, 2, 2, 0, 0, 0),
        Q.DOM7 to listOf(0, 2, 0, 1, 0, 0),
        Q.MIN7 to listOf(0, 2, 0, 0, 0, 0),
        Q.MAJ7 to listOf(0, 2, 1, 1, 0, 0),
        Q.SUS4 to listOf(0, 2, 2, 2, 0, 0),
        Q.SUS2 to listOf(0, 2, 4, 4, 0, 0)
    )
    private val A_SHAPE = mapOf(
        Q.MAJ to listOf(-1, 0, 2, 2, 2, 0),
        Q.MIN to listOf(-1, 0, 2, 2, 1, 0),
        Q.DOM7 to listOf(-1, 0, 2, 0, 2, 0),
        Q.MIN7 to listOf(-1, 0, 2, 0, 1, 0),
        Q.MAJ7 to listOf(-1, 0, 2, 1, 2, 0),
        Q.SUS4 to listOf(-1, 0, 2, 2, 3, 0),
        Q.SUS2 to listOf(-1, 0, 2, 2, 0, 0),
        Q.DIM to listOf(-1, 0, 1, 2, 1, -1)
    )

    private fun openKey(root: String, q: Q): String = root + when (q) {
        Q.MAJ -> ""
        Q.MIN -> "m"
        Q.DOM7 -> "7"
        Q.MIN7 -> "m7"
        Q.MAJ7 -> "maj7"
        Q.SUS4 -> "sus4"
        Q.SUS2 -> "sus2"
        Q.DIM -> "dim"
        Q.AUG -> "aug"
    }

    private fun shift(shape: List<Int>, by: Int): List<Int> =
        shape.map { if (it < 0) -1 else it + by }

    fun guitar(chord: String): Shape? {
        val p = Chords.parse(chord) ?: return null
        val (q, approx) = quality(p.rest) ?: return null
        val rootIdx = Chords.index(p.root)
        val canonical = Chords.noteName(rootIdx, flats = false)
        val flatName = Chords.noteName(rootIdx, flats = true)

        for (name in listOf(canonical, flatName)) {
            GUITAR_OPEN[openKey(name, q)]?.let { return Shape(it, approx) }
        }
        val fe = ((rootIdx - 4) % 12 + 12) % 12 // case de la racine sur la 6e corde
        val fa = ((rootIdx - 9) % 12 + 12) % 12 // case de la racine sur la 5e corde
        val eShape = E_SHAPE[q]
        val aShape = A_SHAPE[q]
        val useE = eShape != null && (aShape == null || fe <= fa)
        return if (useE) Shape(shift(eShape!!, fe), approx)
        else if (aShape != null) Shape(shift(aShape, fa), approx)
        else null
    }

    // ---------- Ukulélé (sol do mi la, accord standard) ----------

    private val UKE_OPEN: Map<Q, List<List<Int>>> = mapOf(
        // Chaque forme est donnée avec sa note racine (index 0 = do) : (racine, doigté)
        Q.MAJ to listOf(listOf(0, 0, 0, 0, 3), listOf(5, 2, 0, 1, 0), listOf(7, 0, 2, 3, 2)),
        Q.MIN to listOf(listOf(2, 2, 2, 1, 0), listOf(0, 0, 3, 3, 3), listOf(9, 2, 0, 0, 0)),
        Q.DOM7 to listOf(listOf(0, 0, 0, 0, 1), listOf(5, 2, 3, 1, 3), listOf(7, 0, 2, 1, 2)),
        Q.MIN7 to listOf(listOf(9, 0, 0, 0, 0), listOf(2, 2, 2, 1, 3)),
        Q.MAJ7 to listOf(listOf(0, 0, 0, 0, 2), listOf(5, 2, 4, 1, 3)),
        Q.SUS4 to listOf(listOf(0, 0, 0, 1, 3)),
        Q.SUS2 to listOf(listOf(0, 0, 2, 3, 3))
    )

    private val UKE_COMMON: Map<String, List<Int>> = mapOf(
        "A" to listOf(2, 1, 0, 0), "G" to listOf(0, 2, 3, 2), "D" to listOf(2, 2, 2, 0),
        "E" to listOf(4, 4, 4, 2), "Am" to listOf(2, 0, 0, 0), "Em" to listOf(0, 4, 3, 2),
        "A7" to listOf(0, 1, 0, 0), "G7" to listOf(0, 2, 1, 2), "D7" to listOf(2, 2, 2, 3),
        "E7" to listOf(1, 2, 0, 2), "Bm" to listOf(4, 2, 2, 2), "B7" to listOf(2, 3, 2, 2),
        "Em7" to listOf(0, 2, 0, 2)
    )

    fun ukulele(chord: String): Shape? {
        val p = Chords.parse(chord) ?: return null
        val (q, approx) = quality(p.rest) ?: return null
        if (q == Q.DIM || q == Q.AUG) return null
        val rootIdx = Chords.index(p.root)
        for (name in listOf(Chords.noteName(rootIdx, false), Chords.noteName(rootIdx, true))) {
            UKE_COMMON[openKey(name, q)]?.let { return Shape(it, approx) }
        }
        val forms = UKE_OPEN[q] ?: return null
        var best: List<Int>? = null
        var bestFret = Int.MAX_VALUE
        for (f in forms) {
            val n = ((rootIdx - f[0]) % 12 + 12) % 12
            if (n < bestFret) {
                bestFret = n
                best = f.drop(1).map { it + n }
            }
        }
        return best?.let { Shape(it, approx) }
    }

    fun shapeFor(chord: String, ukulele: Boolean): Shape? =
        if (ukulele) ukulele(chord) else guitar(chord)

    // ---------- Piano ----------

    private val PIANO_INTERVALS = mapOf(
        Q.MAJ to listOf(0, 4, 7),
        Q.MIN to listOf(0, 3, 7),
        Q.DOM7 to listOf(0, 4, 7, 10),
        Q.MIN7 to listOf(0, 3, 7, 10),
        Q.MAJ7 to listOf(0, 4, 7, 11),
        Q.SUS4 to listOf(0, 5, 7),
        Q.SUS2 to listOf(0, 2, 7),
        Q.DIM to listOf(0, 3, 6),
        Q.AUG to listOf(0, 4, 8)
    )

    /**
     * Touches à jouer au piano : positions en demi-tons depuis le do (0 = do, 12 = do suivant), racine en premier.
     * Sur un accord avec basse (C/E), la basse est ajoutée une octave plus bas si elle n'est pas déjà dans l'accord ;
     * elle est alors renvoyée avec une position négative.
     */
    fun piano(chord: String): Shape? {
        val p = Chords.parse(chord) ?: return null
        val (q, approx) = quality(p.rest) ?: return null
        val root = Chords.index(p.root)
        val keys = PIANO_INTERVALS.getValue(q).map { root + it }.toMutableList()
        p.bass?.let { b ->
            val bi = Chords.index(b)
            if (keys.none { it % 12 == bi }) keys.add(0, bi - 12) else keys.add(0, keys.first { it % 12 == bi } - 12)
        }
        return Shape(keys, approx)
    }


    val guitarStrings = listOf("Mi", "La", "Ré", "Sol", "Si", "Mi")
    val ukuleleStrings = listOf("Sol", "Do", "Mi", "La")
}
