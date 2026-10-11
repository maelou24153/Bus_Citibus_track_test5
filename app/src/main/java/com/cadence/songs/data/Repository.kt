package com.cadence.songs.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cadence.songs.music.ChordPro
import com.cadence.songs.music.MuseScore
import com.cadence.songs.music.Solfege
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.UUID

class Repository(private val context: Context, db: AppDatabase) {
    private val songDao = db.songDao()
    private val setDao = db.setListDao()

    private val prefs = context.getSharedPreferences("cadence", Context.MODE_PRIVATE)

    private suspend fun insertSong(song: Song): Long =
        songDao.insert(if (song.uid.isBlank()) song.copy(uid = UUID.randomUUID().toString()) else song)

    /** Donne un identifiant aux morceaux créés avant la synchronisation Wi-Fi. */
    suspend fun ensureUids() {
        for (s in songDao.withoutUid()) songDao.update(s.copy(uid = UUID.randomUUID().toString()))
    }

    // ---------- Morceaux supprimés (pour propager les suppressions entre appareils) ----------

    fun tombstones(): Map<String, Long> {
        val raw = prefs.getString("tombstones", "").orEmpty()
        val out = HashMap<String, Long>()
        for (part in raw.split(';')) {
            val i = part.indexOf('=')
            if (i > 0) part.substring(i + 1).toLongOrNull()?.let { out[part.substring(0, i)] = it }
        }
        return out
    }

    private fun saveTombstones(map: Map<String, Long>) {
        // On garde les 500 plus récents, largement assez pour quelques appareils.
        val kept = map.entries.sortedByDescending { it.value }.take(500)
        prefs.edit().putString("tombstones", kept.joinToString(";") { it.key + "=" + it.value }).apply()
    }

    private fun recordTombstone(uid: String) {
        if (uid.isBlank()) return
        saveTombstones(tombstones() + (uid to System.currentTimeMillis()))
    }

    private fun forgetTombstone(uid: String) {
        val t = tombstones()
        if (uid in t) saveTombstones(t - uid)
    }

    // ---------- Morceaux ----------

    val songs: Flow<List<Song>> = songDao.observeAll()

    fun observeSong(id: Long): Flow<Song?> = songDao.observe(id)

    suspend fun getSong(id: Long): Song? = songDao.get(id)

    suspend fun saveSong(song: Song): Long {
        return if (song.id == 0L) {
            insertSong(song)
        } else {
            songDao.update(song.copy(updatedAt = System.currentTimeMillis()))
            song.id
        }
    }

    /** Mise à jour des réglages de lecture (transposition, capo, vitesse…) sans toucher à la date de modif. */
    suspend fun updateSong(song: Song) = songDao.update(song)

    suspend fun deleteSong(song: Song) {
        songDao.delete(song)
        recordTombstone(song.uid)
        song.pdfPath?.let { path -> runCatching { File(path).delete() } }
    }

    suspend fun seedIfEmpty(sample: Song) {
        if (songDao.count() == 0) insertSong(sample)
    }

    // ---------- Sets ----------

    val setLists: Flow<List<SetList>> = setDao.observeAll()
    val setCounts: Flow<List<SetCount>> = setDao.observeCounts()

    fun observeSetList(id: Long): Flow<SetList?> = setDao.observe(id)
    fun observeEntries(id: Long): Flow<List<EntryWithSong>> = setDao.observeEntries(id)

    suspend fun createSetList(name: String): Long = setDao.insert(SetList(name = name))

    suspend fun renameSetList(setList: SetList, name: String) = setDao.update(setList.copy(name = name))

    suspend fun deleteSetList(setList: SetList) = setDao.delete(setList)

    suspend fun addToSetList(setId: Long, songIds: List<Long>) {
        var position = setDao.maxPosition(setId) + 1
        val entries = songIds.map { SetEntry(setListId = setId, songId = it, position = position++) }
        setDao.insertEntries(entries)
    }

    suspend fun removeEntry(setId: Long, entryId: Long) {
        setDao.deleteEntry(entryId)
        setDao.entries(setId).forEachIndexed { index, e ->
            if (e.position != index) setDao.setPosition(e.id, index)
        }
    }

    suspend fun moveEntry(setId: Long, entryId: Long, delta: Int) {
        val list = setDao.entries(setId).toMutableList()
        val i = list.indexOfFirst { it.id == entryId }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        Collections.swap(list, i, j)
        list.forEachIndexed { index, e -> setDao.setPosition(e.id, index) }
    }

    // ---------- Accès pour le serveur Wi-Fi ----------

    suspend fun allSongs(): List<Song> = songDao.all()

    /** Enregistre un PDF reçu (par Wi-Fi) et le rattache au morceau ; l'ancien PDF est supprimé. */
    suspend fun savePdfBytes(songId: Long, name: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val song = songDao.get(songId) ?: return@runCatching false
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "partition.pdf" }
            val dest = File(pdfDir(), "${System.currentTimeMillis()}_$safe")
            dest.writeBytes(bytes)
            song.pdfPath?.let { old -> runCatching { File(old).delete() } }
            songDao.update(song.copy(pdfPath = dest.absolutePath, updatedAt = System.currentTimeMillis()))
            true
        }.getOrDefault(false)
    }

    suspend fun readPdfBytes(songId: Long): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val path = songDao.get(songId)?.pdfPath ?: return@runCatching null
            File(path).takeIf { it.exists() }?.readBytes()
        }.getOrNull()
    }

    suspend fun removePdf(songId: Long) = withContext(Dispatchers.IO) {
        val song = songDao.get(songId) ?: return@withContext
        song.pdfPath?.let { old -> runCatching { File(old).delete() } }
        songDao.update(song.copy(pdfPath = null, updatedAt = System.currentTimeMillis()))
    }

    // ---------- Sets : accès pour les serveurs et la synchronisation ----------

    suspend fun getSetList(id: Long): SetList? = setDao.get(id)

    suspend fun setsWithCounts(): List<Pair<SetList, Int>> {
        val counts = setDao.counts().associate { it.setListId to it.count }
        return setDao.allSetLists().sortedBy { it.name.lowercase() }.map { it to (counts[it.id] ?: 0) }
    }

    suspend fun setEntriesWithSong(setId: Long): List<EntryWithSong> = setDao.entriesWithSong(setId)

    // ---------- Actions pratiques ----------

    suspend fun toggleFavorite(id: Long): Boolean? {
        val s = songDao.get(id) ?: return null
        songDao.update(s.copy(favorite = !s.favorite))
        return !s.favorite
    }

    /** Duplique un morceau (PDF et partition compris). Renvoie l'id de la copie. */
    suspend fun duplicateSong(id: Long): Long? = withContext(Dispatchers.IO) {
        val s = songDao.get(id) ?: return@withContext null
        var newPdf: String? = null
        s.pdfPath?.let { old ->
            runCatching {
                val src = File(old)
                if (src.exists()) {
                    val dest = File(pdfDir(), "${System.currentTimeMillis()}_copie_${src.name.substringAfter('_')}")
                    src.copyTo(dest, overwrite = true)
                    newPdf = dest.absolutePath
                }
            }
        }
        val now = System.currentTimeMillis()
        insertSong(
            s.copy(
                id = 0, uid = "", title = s.title + " (copie)", pdfPath = newPdf, favorite = false,
                createdAt = now, updatedAt = now
            )
        )
    }

    // ---------- Partitions MuseScore ----------

    /**
     * Importe une partition MuseScore (.mscz ou .mscx). Sans [attachTo], crée un nouveau morceau (titre, tonalité,
     * tempo, paroles et accords repris de la partition). Avec [attachTo], joint la partition à ce morceau.
     * Lève [MuseScore.ParseException] si le fichier est illisible.
     */
    suspend fun importScoreBytes(bytes: ByteArray, fileName: String, attachTo: Long? = null): Long =
        withContext(Dispatchers.Default) {
            val score = MuseScore.parse(bytes, fileName)
            val json = score.toJson()
            if (json.length > 1_500_000) throw MuseScore.ParseException("Partition trop volumineuse")
            val lyricsAndChords = Solfege.toChordPro(score)
            val timeSig = "${score.beats}/${score.beatType}"
            if (attachTo != null) {
                val old = songDao.get(attachTo) ?: throw MuseScore.ParseException("Morceau introuvable")
                songDao.update(
                    old.copy(
                        score = json,
                        content = old.content.ifBlank { lyricsAndChords },
                        songKey = old.songKey.ifBlank { Solfege.songKey(score) },
                        tempo = if (old.tempo > 0) old.tempo else score.firstTempo,
                        timeSig = timeSig,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                attachTo
            } else {
                insertSong(
                    Song(
                        title = score.title.ifBlank { fileName.substringBeforeLast('.') },
                        artist = score.composer,
                        songKey = Solfege.songKey(score),
                        tempo = score.firstTempo,
                        timeSig = timeSig,
                        content = lyricsAndChords,
                        score = json,
                        tags = "partition"
                    )
                )
            }
        }

    suspend fun removeScore(songId: Long) {
        val s = songDao.get(songId) ?: return
        songDao.update(s.copy(score = "", updatedAt = System.currentTimeMillis()))
    }

    // ---------- Synchronisation entre appareils ----------

    suspend fun stamps(): List<SongStamp> = songDao.stamps()

    suspend fun songByUid(uid: String): Song? = songDao.byUid(uid)

    /**
     * Applique un morceau reçu d'un autre appareil. La date de modification d'origine est conservée,
     * sinon les deux appareils se renverraient le morceau indéfiniment. Les réglages de lecture locaux
     * (transposition, vitesse, favori, PDF) ne sont pas écrasés.
     */
    suspend fun applyRemoteSong(remote: Song): Long {
        val local = songDao.byUid(remote.uid)
        forgetTombstone(remote.uid)
        return if (local == null) {
            songDao.insert(remote.copy(id = 0, pdfPath = null))
        } else {
            songDao.update(
                remote.copy(
                    id = local.id, pdfPath = local.pdfPath, transpose = local.transpose,
                    scrollSpeed = local.scrollSpeed, favorite = local.favorite, createdAt = local.createdAt
                )
            )
            local.id
        }
    }

    /** Enregistre le PDF d'un morceau reçu sans toucher à sa date de modification. */
    suspend fun applyRemotePdf(uid: String, name: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val song = songDao.byUid(uid) ?: return@runCatching false
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "partition.pdf" }
            val dest = File(pdfDir(), "${System.currentTimeMillis()}_$safe")
            dest.writeBytes(bytes)
            song.pdfPath?.let { old -> runCatching { File(old).delete() } }
            songDao.update(song.copy(pdfPath = dest.absolutePath))
            true
        }.getOrDefault(false)
    }

    suspend fun deleteByUid(uid: String, ifOlderThan: Long) {
        val s = songDao.byUid(uid) ?: return
        if (s.updatedAt <= ifOlderThan) deleteSong(s)
    }

    /** Sets sous forme (nom, identifiants uid des morceaux dans l'ordre). */
    suspend fun setsForSync(): List<Pair<String, List<String>>> =
        setDao.allSetLists().map { sl ->
            sl.name to setDao.entriesWithSong(sl.id).map { it.song.uid }.filter { it.isNotBlank() }
        }

    /** Fusionne des sets reçus : crée ceux qui manquent, ajoute les morceaux absents (rien n'est retiré). */
    suspend fun mergeSets(remote: List<Pair<String, List<String>>>) {
        for ((name, uids) in remote) {
            if (name.isBlank()) continue
            val set = setDao.byName(name) ?: setDao.get(setDao.insert(SetList(name = name))) ?: continue
            val present = setDao.entriesWithSong(set.id).map { it.song.uid }.toSet()
            val toAdd = uids.filter { it !in present }.mapNotNull { songDao.byUid(it)?.id }
            if (toAdd.isNotEmpty()) addToSetList(set.id, toAdd)
        }
    }

    // ---------- Import de fichiers ----------

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun pdfDir(): File = File(context.filesDir, "pdfs").apply { mkdirs() }

    /** Copie un PDF dans le stockage privé de l'appli et renvoie son chemin. */
    suspend fun copyPdf(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = (queryName(uri) ?: "partition.pdf").replace(Regex("[^A-Za-z0-9._-]"), "_")
            val dest = File(pdfDir(), "${System.currentTimeMillis()}_$name")
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
            input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
            dest.absolutePath
        }.getOrNull()
    }

    /** Importe un fichier ChordPro / texte / PDF. Renvoie l'id du morceau créé ou null en cas d'échec. */
    suspend fun importFile(uri: Uri): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryName(uri) ?: "Morceau"
            val mime = context.contentResolver.getType(uri).orEmpty()
            val baseName = name.substringBeforeLast('.')
            if (MuseScore.isScoreFile(name)) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching null
                importScoreBytes(bytes, name)
            } else if (mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) {
                val path = copyPdf(uri) ?: return@runCatching null
                insertSong(Song(title = baseName, pdfPath = path))
            } else {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: return@runCatching null
                val imp = ChordPro.importText(text, baseName)
                insertSong(
                    Song(
                        title = imp.title, artist = imp.artist, songKey = imp.key, capo = imp.capo,
                        tempo = imp.tempo, timeSig = imp.timeSig, content = imp.content
                    )
                )
            }
        }.getOrNull()
    }

    fun exportText(song: Song): String =
        ChordPro.export(song.title, song.artist, song.songKey, song.capo, song.tempo, song.timeSig, song.content)

    // ---------- Sauvegarde / restauration (JSON) ----------

    /** Contenu complet de la sauvegarde (morceaux, partitions et sets) au format JSON. */
    suspend fun backupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 2)
        val songsJson = JSONArray()
        for (s in songDao.all()) {
            songsJson.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("artist", s.artist)
                    .put("key", s.songKey)
                    .put("tempo", s.tempo)
                    .put("timeSig", s.timeSig)
                    .put("capo", s.capo)
                    .put("transpose", s.transpose)
                    .put("scrollSpeed", s.scrollSpeed)
                    .put("content", s.content)
                    .put("tags", s.tags)
                    .put("favorite", s.favorite)
                    .put("notes", s.notes)
                    .put("uid", s.uid)
                    .put("score", s.score)
            )
        }
        root.put("songs", songsJson)
        val setsJson = JSONArray()
        for (sl in setDao.allSetLists()) {
            val ids = JSONArray()
            setDao.entries(sl.id).forEach { ids.put(it.songId) }
            setsJson.put(JSONObject().put("name", sl.name).put("songs", ids))
        }
        root.put("setlists", setsJson)
        root.toString(2)
    }

    suspend fun exportBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = backupJson()
            val out = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            true
        }.getOrDefault(false)
    }

    /** Restaure une sauvegarde en ajoutant les morceaux et sets (rien n'est écrasé). */
    suspend fun importBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return@runCatching 0
            val root = JSONObject(text)
            val idMap = HashMap<Long, Long>()
            val songsJson = root.optJSONArray("songs") ?: JSONArray()
            for (i in 0 until songsJson.length()) {
                val o = songsJson.getJSONObject(i)
                val savedUid = o.optString("uid")
                val newId = insertSong(
                    Song(
                        uid = if (savedUid.isNotBlank() && songDao.byUid(savedUid) == null) savedUid else "",
                        score = o.optString("score"),
                        title = o.optString("title"),
                        artist = o.optString("artist"),
                        songKey = o.optString("key"),
                        tempo = o.optInt("tempo"),
                        timeSig = o.optString("timeSig", "4/4"),
                        capo = o.optInt("capo"),
                        transpose = o.optInt("transpose"),
                        scrollSpeed = o.optInt("scrollSpeed", 6),
                        content = o.optString("content"),
                        tags = o.optString("tags"),
                        favorite = o.optBoolean("favorite"),
                        notes = o.optString("notes")
                    )
                )
                idMap[o.optLong("id")] = newId
            }
            val setsJson = root.optJSONArray("setlists") ?: JSONArray()
            for (i in 0 until setsJson.length()) {
                val o = setsJson.getJSONObject(i)
                val setId = setDao.insert(SetList(name = o.optString("name", "Set")))
                val ids = o.optJSONArray("songs") ?: JSONArray()
                val mapped = (0 until ids.length()).mapNotNull { idMap[ids.getLong(it)] }
                if (mapped.isNotEmpty()) addToSetList(setId, mapped)
            }
            songsJson.length()
        }.getOrDefault(-1)
    }
}
