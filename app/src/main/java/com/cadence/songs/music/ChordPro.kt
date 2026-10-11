package com.cadence.songs.music

/** Un segment de ligne : un accord optionnel posé au-dessus d'un morceau de texte. */
data class Seg(val chord: String?, val text: String)

sealed interface Line {
    data object Blank : Line
    data class Section(val label: String) : Line
    data class Comment(val text: String) : Line
    data class Tab(val text: String) : Line
    data class Lyric(val segs: List<Seg>, val chorus: Boolean) : Line
}

data class Meta(
    val title: String? = null,
    val artist: String? = null,
    val key: String? = null,
    val capo: Int? = null,
    val tempo: Int? = null,
    val time: String? = null
)

data class ImportedSong(
    val title: String,
    val artist: String,
    val key: String,
    val capo: Int,
    val tempo: Int,
    val timeSig: String,
    val content: String
)

object ChordPro {
    private val directiveRe = Regex("^\\{\\s*([a-zA-Z_]+)\\s*(?::\\s*(.*?))?\\s*\\}$")
    private val bracketRe = Regex("\\[([^\\]]*)\\]")
    private val loneBracketRe = Regex("^\\s*\\[([^\\]]+)\\]\\s*$")
    private val sectionWordRe = Regex(
        "^\\s*[\\(\\[]?\\s*(intro|verse|chorus|refrain|bridge|pont|couplet|outro|solo|pre-?chorus|interlude|instrumental|coda|tag)(\\s*\\d+)?\\s*[\\)\\]]?\\s*:?\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val chordLineExtras = setOf("|", "/", "-", "N.C.", "NC", "x2", "x3", "x4", "||", "|:", ":|")

    // ---------- Analyse ----------

    fun parse(content: String): List<Line> {
        val out = mutableListOf<Line>()
        var chorus = false
        var tab = false
        for (raw in content.lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) continue

            val d = directiveRe.matchEntire(trimmed)
            if (d != null) {
                val name = d.groupValues[1].lowercase()
                val arg = d.groupValues[2].trim()
                when (name) {
                    "soc", "start_of_chorus" -> {
                        chorus = true
                        out.add(Line.Section(arg.ifBlank { "Refrain" }))
                    }
                    "eoc", "end_of_chorus" -> chorus = false
                    "sov", "start_of_verse" -> out.add(Line.Section(arg.ifBlank { "Couplet" }))
                    "sob", "start_of_bridge" -> out.add(Line.Section(arg.ifBlank { "Pont" }))
                    "sot", "start_of_tab" -> tab = true
                    "eot", "end_of_tab" -> tab = false
                    "chorus" -> out.add(Line.Section(arg.ifBlank { "Refrain" }))
                    "c", "comment", "ci", "comment_italic", "cb", "comment_box" ->
                        if (arg.isNotBlank()) out.add(Line.Comment(arg))
                    else -> {}
                }
                continue
            }
            if (tab) {
                out.add(Line.Tab(line))
                continue
            }
            if (trimmed.isEmpty()) {
                out.add(Line.Blank)
                continue
            }
            val lone = loneBracketRe.matchEntire(line)
            if (lone != null && !Chords.isChord(lone.groupValues[1].trim())) {
                out.add(Line.Section(lone.groupValues[1].trim()))
                continue
            }
            out.add(Line.Lyric(splitSegments(line), chorus))
        }
        return out
    }

    fun splitSegments(line: String): List<Seg> {
        val segs = mutableListOf<Seg>()
        var last = 0
        var pending: String? = null
        for (m in bracketRe.findAll(line)) {
            val before = line.substring(last, m.range.first)
            if (pending != null || before.isNotEmpty()) segs.add(Seg(pending, before))
            pending = m.groupValues[1].trim()
            last = m.range.last + 1
        }
        val tail = line.substring(last)
        if (pending != null || tail.isNotEmpty() || segs.isEmpty()) segs.add(Seg(pending, tail))
        return segs
    }

    /** Premier accord rencontré dans le texte (sert de tonalité par défaut). */
    fun firstChord(content: String): String? {
        for (m in bracketRe.findAll(content)) {
            val c = m.groupValues[1].trim()
            if (Chords.isChord(c)) return c
        }
        return null
    }

    fun hasInlineChords(content: String): Boolean =
        bracketRe.findAll(content).any { Chords.isChord(it.groupValues[1].trim()) }

    fun contentPrefersFlats(content: String): Boolean {
        for (m in bracketRe.findAll(content)) {
            val p = Chords.parse(m.groupValues[1].trim()) ?: continue
            if (p.root.length > 1 && p.root[1] == 'b') return true
            if (p.root.length > 1 && p.root[1] == '#') return false
        }
        return false
    }

    // ---------- Métadonnées ----------

    fun extractMeta(content: String): Pair<Meta, String> {
        var title: String? = null
        var artist: String? = null
        var key: String? = null
        var capo: Int? = null
        var tempo: Int? = null
        var time: String? = null
        var subtitle: String? = null
        val kept = mutableListOf<String>()
        for (raw in content.lines()) {
            val d = directiveRe.matchEntire(raw.trim())
            if (d == null) {
                kept.add(raw)
                continue
            }
            val arg = d.groupValues[2].trim()
            when (d.groupValues[1].lowercase()) {
                "title", "t" -> title = arg
                "subtitle", "st" -> subtitle = arg
                "artist" -> artist = arg
                "key" -> key = arg
                "capo" -> capo = arg.toIntOrNull()
                "tempo" -> tempo = arg.toIntOrNull()
                "time" -> time = arg
                else -> kept.add(raw)
            }
        }
        val body = kept.joinToString("\n").trim('\n', '\r')
        return Meta(title, artist ?: subtitle, key, capo, tempo, time) to body
    }

    fun importText(text: String, fallbackTitle: String): ImportedSong {
        val clean = text.replace("\r\n", "\n").replace('\r', '\n').removePrefix("﻿")
        val (meta, body) = extractMeta(clean)
        val content = if (hasInlineChords(body)) body else convertChordsOverLyrics(body)
        val key = meta.key ?: firstChord(content).orEmpty()
        return ImportedSong(
            title = meta.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            artist = meta.artist.orEmpty(),
            key = key,
            capo = meta.capo ?: 0,
            tempo = meta.tempo ?: 0,
            timeSig = meta.time ?: "4/4",
            content = content
        )
    }

    fun export(
        title: String, artist: String, key: String, capo: Int, tempo: Int, timeSig: String, content: String
    ): String {
        val sb = StringBuilder()
        sb.append("{title: ").append(title).append("}\n")
        if (artist.isNotBlank()) sb.append("{artist: ").append(artist).append("}\n")
        if (key.isNotBlank()) sb.append("{key: ").append(key).append("}\n")
        if (capo > 0) sb.append("{capo: ").append(capo).append("}\n")
        if (tempo > 0) sb.append("{tempo: ").append(tempo).append("}\n")
        if (timeSig.isNotBlank()) sb.append("{time: ").append(timeSig).append("}\n")
        sb.append('\n').append(content)
        return sb.toString()
    }

    // ---------- Conversion « accords au-dessus des paroles » -> ChordPro ----------

    private fun isChordToken(t: String) = Chords.isChord(t)

    fun isChordLine(line: String): Boolean {
        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        var chords = 0
        for (t in tokens) {
            if (isChordToken(t)) chords++ else if (t !in chordLineExtras) return false
        }
        return chords > 0
    }

    private fun merge(chordLine: String, lyric: String): String {
        val tokens = Regex("\\S+").findAll(chordLine).toList()
        val sb = StringBuilder(lyric)
        for (t in tokens.asReversed()) {
            if (!isChordToken(t.value)) continue
            val pos = t.range.first
            while (sb.length < pos) sb.append(' ')
            sb.insert(pos, "[${t.value}]")
        }
        return sb.toString()
    }

    fun convertChordsOverLyrics(text: String): String {
        val lines = text.lines()
        val out = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (isChordLine(line)) {
                val next = lines.getOrNull(i + 1)
                if (next != null && next.isNotBlank() && !isChordLine(next) &&
                    !sectionWordRe.matches(next) && !next.contains('[')
                ) {
                    out.add(merge(line, next))
                    i += 2
                } else {
                    val chords = Regex("\\S+").findAll(line).map { it.value }.filter { isChordToken(it) }
                    out.add(chords.joinToString(" ") { "[$it]" })
                    i++
                }
            } else if (sectionWordRe.matches(line)) {
                val label = line.trim().trim('[', ']', '(', ')', ':', ' ')
                out.add("[$label]")
                i++
            } else {
                out.add(line)
                i++
            }
        }
        return out.joinToString("\n")
    }
}
