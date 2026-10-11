package com.cadence.songs.server

import com.cadence.songs.music.ChordPro
import com.cadence.songs.music.Chords
import com.cadence.songs.music.Line
import com.cadence.songs.music.Score
import com.cadence.songs.music.Solfege

internal fun httpError(status: Int, message: String) =
    HttpResponse.json(status, Json.stringify(mapOf("error" to message)))

internal fun songMap(s: SongDto, withContent: Boolean): Map<String, Any?> {
    val m = linkedMapOf<String, Any?>(
        "id" to s.id, "title" to s.title, "artist" to s.artist, "key" to s.key, "tempo" to s.tempo,
        "timeSig" to s.timeSig, "capo" to s.capo, "tags" to s.tags, "notes" to s.notes,
        "favorite" to s.favorite, "hasPdf" to s.hasPdf, "hasScore" to s.hasScore, "updatedAt" to s.updatedAt
    )
    if (withContent) m["content"] = s.content
    return m
}

/** Transforme un morceau ou une partition en structure simple que la page web n'a plus qu'à afficher. */
object SongRender {
    /** notation : 0 = lettres, 1 = Nashville, 2 = solfège. */
    fun chordMapper(key: String, content: String, semitones: Int, notation: Int): (String) -> String {
        val keyForCalc = key.ifBlank { ChordPro.firstChord(content).orEmpty() }
        val flats = if (keyForCalc.isNotBlank()) Chords.useFlats(keyForCalc, semitones) else ChordPro.contentPrefersFlats(content)
        val keyIndex = Chords.keyIndex(keyForCalc) ?: 0
        return { c ->
            when (notation) {
                1 -> Chords.nashville(c, keyIndex)
                2 -> Chords.solfege(c, semitones, flats)
                else -> Chords.transpose(c, semitones, flats)
            }
        }
    }

    fun render(song: SongDto, transpose: Int, capo: Int, notation: Int): Map<String, Any?> {
        val semis = transpose - capo
        val map = chordMapper(song.key, song.content, semis, notation)
        val lines = ChordPro.parse(song.content).map { line -> lineMap(line, map) }
        val keyForCalc = song.key.ifBlank { ChordPro.firstChord(song.content).orEmpty() }
        val flatsSound = if (keyForCalc.isNotBlank()) Chords.useFlats(keyForCalc, transpose) else false
        val shownKey = if (keyForCalc.isBlank()) "" else Chords.transposeKey(keyForCalc, transpose, flatsSound)
        val m = linkedMapOf<String, Any?>()
        m.putAll(songMap(song, withContent = false))
        m["key"] = shownKey
        m["transpose"] = transpose
        m["capo"] = capo
        m["lines"] = lines
        return m
    }

    private fun lineMap(line: Line, map: (String) -> String): Map<String, Any?> = when (line) {
        is Line.Blank -> mapOf("k" to "blank")
        is Line.Section -> mapOf("k" to "section", "t" to line.label)
        is Line.Comment -> mapOf("k" to "comment", "t" to line.text)
        is Line.Tab -> mapOf("k" to "tab", "t" to line.text)
        is Line.Lyric -> mapOf(
            "k" to "lyric",
            "chorus" to line.chorus,
            "segs" to line.segs.map { seg ->
                mapOf("c" to seg.chord?.let { map(it) }, "t" to seg.text)
            }
        )
    }

    /** Partition en solfège : mesures et cases, avec accords déjà convertis selon la notation choisie. */
    fun solfege(score: Score, track: Int, transpose: Int, fixed: Boolean, notation: Int): Map<String, Any?> {
        val t = track.coerceIn(0, (score.tracks.size - 1).coerceAtLeast(0))
        val key = Solfege.keyName(score.fifths)
        val map = chordMapper(key, "", transpose, notation)
        val measures = Solfege.measures(score, t, transpose, fixed).map { m ->
            mapOf(
                "n" to m.number,
                "cells" to m.cells.map { c ->
                    mapOf("t" to c.text, "d" to c.duration, "l" to c.lyric, "r" to c.rest, "c" to c.chord?.let { map(it) })
                }
            )
        }
        val flats = Chords.useFlats(key, transpose)
        return mapOf(
            "title" to score.title,
            "composer" to score.composer,
            "key" to Chords.transposeKey(key, transpose, flats),
            "timeSig" to "${score.beats}/${score.beatType}",
            "tempo" to score.firstTempo,
            "tracks" to score.tracks.map { it.name },
            "track" to t,
            "measures" to measures
        )
    }
}

/** Routes en lecture seule : partagées par le serveur d'administration et le serveur « lecteur ». */
class ReadRoutes(private val store: SongStore) {

    /** Renvoie null si la route n'est pas une route de lecture. */
    fun handle(req: HttpRequest, seg: List<String>): HttpResponse? {
        if (req.method != "GET") return null
        return when (seg[0]) {
            "ping" -> HttpResponse.json(200, """{"ok":true}""")
            "version" -> HttpResponse.json(200, Json.stringify(mapOf("v" to store.version())))
            "songs" -> songs(req, seg)
            "sets" -> sets(seg)
            else -> null
        }
    }

    private fun intParam(req: HttpRequest, name: String, default: Int): Int = req.query[name]?.toIntOrNull() ?: default

    private fun songs(req: HttpRequest, seg: List<String>): HttpResponse? {
        if (seg.size == 1) {
            return HttpResponse.json(200, Json.stringify(store.list().map { songMap(it, withContent = false) }))
        }
        val id = seg[1].toLongOrNull() ?: return httpError(400, "Identifiant invalide")
        val song = store.get(id) ?: return httpError(404, "Morceau introuvable")
        if (seg.size == 2) return HttpResponse.json(200, Json.stringify(songMap(song, withContent = true)))
        return when (seg[2]) {
            "pdf" -> {
                val bytes = store.readPdf(id) ?: return httpError(404, "Pas de PDF")
                HttpResponse(200, "application/pdf", bytes, mapOf("Content-Disposition" to "inline; filename=\"partition.pdf\""))
            }
            "export" -> {
                val text = store.exportText(id) ?: return httpError(404, "Morceau introuvable")
                val safe = song.title.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "morceau" }
                HttpResponse(
                    200, "text/plain; charset=utf-8", text.toByteArray(Charsets.UTF_8),
                    mapOf("Content-Disposition" to "attachment; filename=\"$safe.cho\"")
                )
            }
            "render" -> {
                val t = intParam(req, "t", 0).coerceIn(-11, 11)
                val capo = intParam(req, "capo", song.capo).coerceIn(0, 11)
                val notation = intParam(req, "notation", 0).coerceIn(0, 2)
                HttpResponse.json(200, Json.stringify(SongRender.render(song, t, capo, notation)))
            }
            "score" -> {
                val json = store.scoreJson(id) ?: return httpError(404, "Pas de partition")
                HttpResponse.json(200, json)
            }
            "solfege" -> {
                val json = store.scoreJson(id) ?: return httpError(404, "Pas de partition")
                val score = Score.fromJson(json) ?: return httpError(500, "Partition illisible")
                val body = SongRender.solfege(
                    score,
                    track = intParam(req, "track", 0),
                    transpose = intParam(req, "t", 0).coerceIn(-11, 11),
                    fixed = intParam(req, "fixed", 1) == 1,
                    notation = intParam(req, "notation", 0).coerceIn(0, 2)
                )
                HttpResponse.json(200, Json.stringify(body))
            }
            else -> null
        }
    }

    private fun sets(seg: List<String>): HttpResponse? {
        if (seg.size == 1) {
            return HttpResponse.json(
                200,
                Json.stringify(store.sets().map { mapOf("id" to it.id, "name" to it.name, "count" to it.count) })
            )
        }
        val id = seg[1].toLongOrNull() ?: return httpError(400, "Identifiant invalide")
        if (seg.size != 2) return null
        val entries = store.setEntries(id) ?: return httpError(404, "Set introuvable")
        val name = store.sets().firstOrNull { it.id == id }?.name.orEmpty()
        return HttpResponse.json(
            200,
            Json.stringify(
                mapOf(
                    "id" to id,
                    "name" to name,
                    "entries" to entries.map {
                        mapOf(
                            "entryId" to it.entryId, "position" to it.position, "songId" to it.songId,
                            "title" to it.title, "artist" to it.artist, "key" to it.key
                        )
                    }
                )
            )
        )
    }
}
