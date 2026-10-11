package com.cadence.songs.music

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lecture des partitions MuseScore (MuseScore 3 et 4) : fichiers compressés .mscz ou XML .mscx.
 * Conserve les notes, silences, liaisons, triolets, paroles, tempo, armure, chiffrage et symboles d'accords.
 * Les reprises, nuances et ornements ne sont pas interprétés.
 */
object MuseScore {
    class ParseException(message: String) : Exception(message)

    private const val MAX_XML_BYTES = 30 * 1024 * 1024

    fun isScoreFile(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".mscz") || n.endsWith(".mscx")
    }

    /** Lit un fichier .mscz / .mscx. Lève [ParseException] avec un message lisible en cas de problème. */
    fun parse(bytes: ByteArray, fileName: String): Score {
        val xml = if (fileName.lowercase().endsWith(".mscz") || (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())) {
            unzipScore(bytes)
        } else {
            bytes
        }
        return parseXml(xml, fileName)
    }

    private fun unzipScore(bytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.lowercase()
                if (!entry.isDirectory && name.endsWith(".mscx") && !name.contains("/")) {
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_XML_BYTES) throw ParseException("Partition trop volumineuse")
                    }
                    return out.toByteArray()
                }
            }
        }
        throw ParseException("Aucune partition trouvée dans ce fichier .mscz")
    }

    // ---------- Aides DOM ----------

    private fun Element.elems(): List<Element> {
        val out = ArrayList<Element>()
        var n: Node? = firstChild
        while (n != null) {
            if (n.nodeType == Node.ELEMENT_NODE) out.add(n as Element)
            n = n.nextSibling
        }
        return out
    }

    private fun Element.kids(name: String): List<Element> = elems().filter { it.tagName == name }
    private fun Element.kid(name: String): Element? = elems().firstOrNull { it.tagName == name }
    private fun Element.text(name: String): String? = kid(name)?.textContent?.trim()

    private fun durationOf(type: String?): Double = when (type) {
        "long" -> 16.0
        "breve" -> 8.0
        "whole" -> 4.0
        "half" -> 2.0
        "quarter" -> 1.0
        "eighth" -> 0.5
        "16th" -> 0.25
        "32nd" -> 0.125
        "64th" -> 0.0625
        "128th" -> 0.03125
        else -> 1.0
    }

    /** « 3/4 » → 3 noires ; « 6/8 » → 3 noires. */
    private fun fractionToBeats(text: String?): Double? {
        if (text == null) return null
        val parts = text.trim().split('/')
        if (parts.size != 2) return null
        val a = parts[0].trim().toDoubleOrNull() ?: return null
        val b = parts[1].trim().toDoubleOrNull() ?: return null
        if (b == 0.0) return null
        return a / b * 4.0
    }

    private fun isGrace(chord: Element): Boolean = chord.elems().any {
        val t = it.tagName
        t == "acciaccatura" || t == "appoggiatura" || t.startsWith("grace")
    }

    private class MutableEvent(
        val measure: Int,
        val start: Double,
        var dur: Double,
        val pitches: IntArray,
        val tpcs: IntArray,
        var lyric: String?,
        val voice: Int
    )

    private fun normalizeChordName(raw: String): String =
        raw.trim().replace('♭', 'b').replace('♯', '#').replace("Δ", "maj7").replace(" ", "")

    // ---------- Lecture ----------

    private fun parseXml(xml: ByteArray, fileName: String): Score {
        val factory = DocumentBuilderFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val doc = try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        } catch (e: Exception) {
            throw ParseException("Fichier MuseScore illisible")
        }
        val root = doc.documentElement
        val scoreEl = if (root.tagName == "Score") root else root.kid("Score")
            ?: throw ParseException("Ce fichier n'est pas une partition MuseScore")

        var title = ""
        var composer = ""
        for (m in scoreEl.kids("metaTag")) {
            when (m.getAttribute("name")) {
                "workTitle" -> title = m.textContent.trim()
                "composer" -> composer = m.textContent.trim()
            }
        }
        if (title.isBlank()) {
            title = scoreEl.kids("Staff").firstOrNull()?.kids("VBox")?.firstOrNull()
                ?.kids("Text")?.firstOrNull { it.text("style")?.lowercase() == "title" }?.text("text").orEmpty()
        }
        if (title.isBlank()) title = fileName.substringBeforeLast('.')

        // Noms des portées d'après les <Part>.
        val staffNames = HashMap<String, String>()
        for (part in scoreEl.kids("Part")) {
            val name = part.text("trackName")?.takeIf { it.isNotBlank() }
                ?: part.kid("Instrument")?.text("longName")?.takeIf { it.isNotBlank() }
                ?: part.kid("Instrument")?.text("trackName")?.takeIf { it.isNotBlank() }
                ?: "Instrument"
            val staves = part.kids("Staff")
            staves.forEachIndexed { i, st ->
                val id = st.getAttribute("id")
                staffNames[id] = if (staves.size > 1) "$name ${i + 1}" else name
            }
        }

        var fifths = 0
        var beats = 4
        var beatType = 4
        var firstSig = true
        var firstKey = true
        val tempos = ArrayList<Pair<Double, Double>>()
        val harmonies = ArrayList<Pair<Double, String>>()
        var measureStarts: List<Double> = emptyList()
        val tracks = ArrayList<ScoreTrack>()

        val staffEls = scoreEl.kids("Staff").filter { it.kids("Measure").isNotEmpty() }
        if (staffEls.isEmpty()) throw ParseException("Cette partition ne contient aucune mesure")

        staffEls.forEachIndexed { staffIndex, staffEl ->
            val isFirst = staffIndex == 0
            val events = ArrayList<MutableEvent>()
            val starts = ArrayList<Double>()
            var curBeats = beats
            var curType = beatType
            var measureStart = 0.0
            val measures = staffEl.kids("Measure")

            for ((mi, meas) in measures.withIndex()) {
                if (isFirst) starts.add(measureStart)
                else if (mi < measureStarts.size) measureStart = measureStarts[mi]

                // Armure et chiffrage : dans la mesure ou dans ses voix.
                val scanTargets = listOf(meas) + meas.kids("voice")
                for (target in scanTargets) {
                    for (ks in target.kids("KeySig")) {
                        val f = (ks.text("concertKey") ?: ks.text("accidental"))?.toIntOrNull()
                        if (f != null && isFirst) {
                            if (firstKey) { fifths = f; firstKey = false }
                        }
                    }
                    for (ts in target.kids("TimeSig")) {
                        val n = ts.text("sigN")?.toIntOrNull()
                        val d = ts.text("sigD")?.toIntOrNull()
                        if (n != null && d != null && d > 0) {
                            curBeats = n; curType = d
                            if (firstSig) { beats = n; beatType = d; firstSig = false }
                        }
                    }
                }
                val nominal = curBeats * 4.0 / curType
                val measureLen = fractionToBeats(meas.getAttribute("len").takeIf { it.isNotBlank() }) ?: nominal

                var longestVoice = 0.0
                for ((vi, voice) in meas.kids("voice").withIndex()) {
                    var cursor = 0.0
                    val tuplets = ArrayList<Double>() // rapports normal/réel, empilés
                    fun ratio(): Double = tuplets.fold(1.0) { a, b -> a * b }
                    var lastEvent: MutableEvent? = null
                    for (el in voice.elems()) {
                        when (el.tagName) {
                            "Tuplet" -> {
                                val normal = el.text("normalNotes")?.toDoubleOrNull() ?: 2.0
                                val actual = el.text("actualNotes")?.toDoubleOrNull() ?: 3.0
                                tuplets.add(if (actual > 0) normal / actual else 1.0)
                            }
                            "endTuplet" -> if (tuplets.isNotEmpty()) tuplets.removeAt(tuplets.size - 1)
                            "location" -> {
                                val frac = fractionToBeats(el.text("fractions"))
                                if (frac != null) cursor = (cursor + frac).coerceAtLeast(0.0)
                            }
                            "Tempo" -> if (isFirst && vi == 0) {
                                val t = el.text("tempo")?.toDoubleOrNull()
                                if (t != null && t > 0) tempos.add((measureStart + cursor) to (t * 60.0))
                            }
                            "Harmony" -> if (isFirst && vi == 0) {
                                val name = harmonyName(el)
                                if (name.isNotBlank()) harmonies.add((measureStart + cursor) to name)
                            }
                            "Rest" -> {
                                val type = el.text("durationType")
                                var d = if (type == "measure") {
                                    fractionToBeats(el.text("duration")) ?: measureLen
                                } else {
                                    durationOf(type)
                                }
                                if (type != "measure") {
                                    val dots = el.text("dots")?.toIntOrNull() ?: 0
                                    d *= 2.0 - Math.pow(0.5, dots.toDouble())
                                    d *= ratio()
                                }
                                val lyric = lyricOf(el)
                                val ev = MutableEvent(mi, measureStart + cursor, d, IntArray(0), IntArray(0), lyric, vi + 1)
                                events.add(ev)
                                lastEvent = ev
                                cursor += d
                            }
                            "Chord" -> {
                                if (isGrace(el)) continue
                                val type = el.text("durationType")
                                var d = durationOf(type)
                                val dots = el.text("dots")?.toIntOrNull() ?: 0
                                d *= 2.0 - Math.pow(0.5, dots.toDouble())
                                d *= ratio()
                                val pitches = ArrayList<Int>()
                                val tpcs = ArrayList<Int>()
                                var allTiedFromPrev = true
                                for (note in el.kids("Note")) {
                                    val p = note.text("pitch")?.toIntOrNull() ?: continue
                                    pitches.add(p)
                                    tpcs.add(note.text("tpc")?.toIntOrNull() ?: -99)
                                    var prev = false
                                    for (sp in note.kids("Spanner")) {
                                        if (sp.getAttribute("type") == "Tie" && sp.kid("prev") != null) prev = true
                                    }
                                    if (!prev) allTiedFromPrev = false
                                }
                                if (pitches.isEmpty()) { cursor += d; continue }
                                val last = lastEvent
                                if (allTiedFromPrev && last != null && !last.pitches.isEmpty() &&
                                    last.pitches.sorted() == pitches.sorted()
                                ) {
                                    last.dur += d // note liée : on prolonge la précédente
                                } else {
                                    val ev = MutableEvent(
                                        mi, measureStart + cursor, d, pitches.toIntArray(), tpcs.toIntArray(),
                                        lyricOf(el), vi + 1
                                    )
                                    events.add(ev)
                                    lastEvent = ev
                                }
                                cursor += d
                            }
                        }
                    }
                    if (cursor > longestVoice) longestVoice = cursor
                }
                if (isFirst) {
                    // Mesure incomplète (anacrouse) : l'attribut len fait foi ; si les voix débordent, on suit les voix.
                    val actual = if (longestVoice > measureLen + 1e-6) longestVoice else measureLen
                    measureStart += actual
                } else if (mi + 1 < measureStarts.size) {
                    measureStart = measureStarts[mi + 1]
                }
            }
            if (isFirst) {
                starts.add(measureStart)
                measureStarts = starts
            }
            if (events.any { !it.isRestEvent() }) {
                val id = staffEl.getAttribute("id")
                tracks.add(
                    ScoreTrack(
                        staffNames[id] ?: "Portée ${tracks.size + 1}",
                        events.map { ScoreEvent(it.measure, it.start, it.dur, it.pitches, it.tpcs, it.lyric, it.voice) }
                    )
                )
            }
        }

        if (tracks.isEmpty()) throw ParseException("Cette partition ne contient aucune note")
        if (tempos.isEmpty() || tempos[0].first > 0.0) tempos.add(0, 0.0 to 100.0)
        val sortedTempos = tempos.sortedBy { it.first }.fold(ArrayList<Pair<Double, Double>>()) { acc, t ->
            if (acc.isNotEmpty() && acc.last().first == t.first) acc[acc.size - 1] = t else acc.add(t)
            acc
        }
        return Score(
            title = title,
            composer = composer,
            fifths = fifths.coerceIn(-7, 7),
            beats = beats,
            beatType = beatType,
            tracks = tracks,
            measureStarts = measureStarts,
            tempos = sortedTempos,
            harmonies = harmonies
        )
    }

    private fun MutableEvent.isRestEvent(): Boolean = pitches.isEmpty()

    private fun lyricOf(el: Element): String? {
        val lyrics = el.kids("Lyrics")
        val first = lyrics.firstOrNull { (it.text("no")?.toIntOrNull() ?: 0) == 0 } ?: lyrics.firstOrNull() ?: return null
        val text = first.text("text")?.takeIf { it.isNotBlank() } ?: return null
        val syl = first.text("syllabic")
        return if (syl == "begin" || syl == "middle") "$text-" else text
    }

    /** Symbole d'accord : MuseScore 4 écrit <name>Am7</name> ; MuseScore 3 sépare <root> (hauteur tonale) et <name> (qualité). */
    private fun harmonyName(h: Element): String {
        val root = h.text("root")?.toIntOrNull()
        val name = h.text("name").orEmpty()
        if (root == null) return normalizeChordName(name)
        val bass = h.text("base")?.toIntOrNull()
        return Solfege.tpcName(root) + normalizeChordName(name) + (if (bass != null) "/" + Solfege.tpcName(bass) else "")
    }
}
