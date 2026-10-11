package com.cadence.songs.server

import com.cadence.songs.music.ChordPro
import com.cadence.songs.music.Chords
import com.cadence.songs.music.MuseScore
import java.net.URLDecoder

/**
 * Serveur n°1 « Cadence sur PC » (administration) : modifier les morceaux, gérer les sets, joindre des PDF et
 * des partitions MuseScore, importer, exporter, sauvegarder. Protégé par un code à 4 chiffres affiché sur l'appareil.
 */
class WebApi(
    private val store: SongStore,
    private val html: () -> String,
    private val pin: () -> String
) {
    private val reads = ReadRoutes(store)

    fun handle(req: HttpRequest): HttpResponse {
        val path = req.path.trimEnd('/').ifEmpty { "/" }
        if (req.method == "GET" && (path == "/" || path == "/index.html")) return HttpResponse.html(html())
        if (!path.startsWith("/api/")) return HttpResponse.text(404, "Introuvable")

        val given = req.headers["x-pin"] ?: req.query["pin"] ?: ""
        if (given != pin()) {
            Thread.sleep(400) // ralentit les essais au hasard
            return httpError(401, "Code incorrect")
        }
        return try {
            val seg = path.removePrefix("/api/").split('/')
            reads.handle(req, seg) ?: route(req, seg)
        } catch (e: Json.JsonException) {
            httpError(400, "JSON invalide : ${e.message}")
        } catch (e: MuseScore.ParseException) {
            httpError(400, e.message ?: "Partition illisible")
        } catch (e: Exception) {
            httpError(500, e.message ?: "Erreur")
        }
    }

    private fun ok() = HttpResponse.json(200, """{"ok":true}""")

    private fun route(req: HttpRequest, seg: List<String>): HttpResponse {
        when (seg[0]) {
            "convert" -> if (req.method == "POST") {
                val text = Json.parseObject(req.bodyText())["text"] as? String ?: ""
                val imp = ChordPro.importText(text, "")
                return HttpResponse.json(200, Json.stringify(mapOf("content" to imp.content, "key" to imp.key)))
            }
            "transpose" -> if (req.method == "POST") return transposeText(req)
            "import" -> if (req.method == "POST") {
                val o = Json.parseObject(req.bodyText())
                val text = o["text"] as? String ?: ""
                val fallback = (o["title"] as? String).orEmpty().ifBlank { "Sans titre" }
                val imp = ChordPro.importText(text, fallback)
                val id = store.save(
                    SongDto(
                        title = imp.title, artist = imp.artist, key = imp.key, capo = imp.capo,
                        tempo = imp.tempo, timeSig = imp.timeSig, content = imp.content
                    )
                )
                return HttpResponse.json(201, Json.stringify(songMap(store.get(id)!!, withContent = false)))
            }
            "importscore" -> if (req.method == "POST") {
                val name = decodedHeader(req, "x-filename") ?: "partition.mscz"
                if (!MuseScore.isScoreFile(name)) return httpError(400, "Il faut un fichier MuseScore (.mscz ou .mscx)")
                val attach = req.query["attach"]?.toLongOrNull()
                val id = store.importScore(req.body, name, attach)
                return HttpResponse.json(201, Json.stringify(songMap(store.get(id)!!, withContent = false)))
            }
            "backup" -> if (req.method == "GET") {
                return HttpResponse(
                    200, "application/json; charset=utf-8", store.backupJson().toByteArray(Charsets.UTF_8),
                    mapOf("Content-Disposition" to "attachment; filename=\"cadence-sauvegarde.json\"")
                )
            }
            "songs" -> return songs(req, seg)
            "sets" -> return sets(req, seg)
        }
        return httpError(404, "Route inconnue")
    }

    private fun decodedHeader(req: HttpRequest, name: String): String? =
        req.headers[name]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }

    /** Transpose tous les accords [entre crochets] d'un texte ChordPro et la tonalité. */
    private fun transposeText(req: HttpRequest): HttpResponse {
        val o = Json.parseObject(req.bodyText())
        val content = o["content"] as? String ?: ""
        val key = (o["key"] as? String).orEmpty().trim()
        val semis = ((o["semitones"] as? Number)?.toInt() ?: 0).coerceIn(-11, 11)
        val ref = key.ifBlank { ChordPro.firstChord(content).orEmpty() }
        val flats = if (ref.isNotBlank()) Chords.useFlats(ref, semis) else ChordPro.contentPrefersFlats(content)
        val out = Regex("\\[([^\\]]*)\\]").replace(content) { m ->
            "[" + Chords.transpose(m.groupValues[1], semis, flats) + "]"
        }
        val newKey = if (key.isBlank()) "" else Chords.transposeKey(key, semis, flats)
        return HttpResponse.json(200, Json.stringify(mapOf("content" to out, "key" to newKey)))
    }

    private fun songs(req: HttpRequest, seg: List<String>): HttpResponse {
        if (seg.size == 1) {
            return when (req.method) {
                "POST" -> {
                    val dto = fromJson(Json.parseObject(req.bodyText()), 0)
                    val id = store.save(dto)
                    HttpResponse.json(201, Json.stringify(songMap(store.get(id)!!, withContent = true)))
                }
                else -> httpError(405, "Méthode non permise")
            }
        }
        val id = seg[1].toLongOrNull() ?: return httpError(400, "Identifiant invalide")
        if (seg.size == 2) {
            return when (req.method) {
                "PUT" -> {
                    val old = store.get(id) ?: return httpError(404, "Morceau introuvable")
                    val dto = fromJson(Json.parseObject(req.bodyText()), id).copy(favorite = old.favorite)
                    store.save(dto)
                    HttpResponse.json(200, Json.stringify(songMap(store.get(id)!!, withContent = true)))
                }
                "DELETE" -> { store.delete(id); ok() }
                else -> httpError(405, "Méthode non permise")
            }
        }
        if (store.get(id) == null) return httpError(404, "Morceau introuvable")
        when (seg[2]) {
            "pdf" -> return when (req.method) {
                "POST" -> {
                    val name = decodedHeader(req, "x-filename") ?: "partition.pdf"
                    if (req.body.size < 5 || String(req.body, 0, 5, Charsets.ISO_8859_1) != "%PDF-") {
                        return httpError(400, "Ce fichier n'est pas un PDF")
                    }
                    if (!store.savePdf(id, name, req.body)) httpError(500, "Enregistrement impossible")
                    else HttpResponse.json(200, Json.stringify(songMap(store.get(id)!!, withContent = false)))
                }
                "DELETE" -> { store.removePdf(id); ok() }
                else -> httpError(405, "Méthode non permise")
            }
            "favorite" -> if (req.method == "POST") {
                val now = store.toggleFavorite(id) ?: return httpError(404, "Morceau introuvable")
                return HttpResponse.json(200, Json.stringify(mapOf("favorite" to now)))
            }
            "duplicate" -> if (req.method == "POST") {
                val copy = store.duplicate(id) ?: return httpError(404, "Morceau introuvable")
                return HttpResponse.json(201, Json.stringify(songMap(store.get(copy)!!, withContent = true)))
            }
            "score" -> if (req.method == "DELETE") {
                store.removeScore(id)
                return ok()
            }
        }
        return httpError(404, "Route inconnue")
    }

    private fun sets(req: HttpRequest, seg: List<String>): HttpResponse {
        if (seg.size == 1) {
            if (req.method != "POST") return httpError(405, "Méthode non permise")
            val name = Json.parseObject(req.bodyText())["name"] as? String ?: ""
            val id = store.createSet(name)
            return HttpResponse.json(201, Json.stringify(mapOf("id" to id)))
        }
        val id = seg[1].toLongOrNull() ?: return httpError(400, "Identifiant invalide")
        if (store.setEntries(id) == null) return httpError(404, "Set introuvable")
        if (seg.size == 2) {
            return when (req.method) {
                "PUT" -> {
                    val name = Json.parseObject(req.bodyText())["name"] as? String ?: ""
                    if (store.renameSet(id, name)) ok() else httpError(400, "Nom invalide")
                }
                "DELETE" -> { store.deleteSet(id); ok() }
                else -> httpError(405, "Méthode non permise")
            }
        }
        when (seg[2]) {
            "songs" -> if (req.method == "POST") {
                val ids = (Json.parseObject(req.bodyText())["songIds"] as? List<*>).orEmpty()
                    .mapNotNull { (it as? Number)?.toLong() }
                    .filter { store.get(it) != null }
                if (ids.isNotEmpty()) store.addToSet(id, ids)
                return HttpResponse.json(200, Json.stringify(mapOf("added" to ids.size)))
            }
            "move" -> if (req.method == "POST") {
                val o = Json.parseObject(req.bodyText())
                val entry = (o["entryId"] as? Number)?.toLong() ?: return httpError(400, "entryId manquant")
                val delta = ((o["delta"] as? Number)?.toInt() ?: 0).coerceIn(-1, 1)
                store.moveInSet(id, entry, delta)
                return ok()
            }
            "entries" -> if (req.method == "DELETE" && seg.size == 4) {
                val entry = seg[3].toLongOrNull() ?: return httpError(400, "Identifiant invalide")
                store.removeFromSet(id, entry)
                return ok()
            }
        }
        return httpError(404, "Route inconnue")
    }

    private fun fromJson(o: Map<String, Any?>, id: Long): SongDto {
        fun str(k: String) = (o[k] as? String).orEmpty()
        fun int(k: String) = (o[k] as? Number)?.toInt() ?: (o[k] as? String)?.toIntOrNull() ?: 0
        return SongDto(
            id = id,
            title = str("title").trim(),
            artist = str("artist").trim(),
            key = str("key").trim(),
            tempo = int("tempo").coerceIn(0, 400),
            timeSig = str("timeSig").trim().ifBlank { "4/4" },
            capo = int("capo").coerceIn(0, 11),
            tags = str("tags").trim(),
            notes = str("notes"),
            content = str("content").replace("\r\n", "\n")
        )
    }
}
