package com.cadence.songs.server

import com.cadence.songs.data.Repository
import com.cadence.songs.data.Song
import kotlinx.coroutines.runBlocking

data class SongDto(
    val id: Long = 0,
    val uid: String = "",
    val title: String = "",
    val artist: String = "",
    val key: String = "",
    val tempo: Int = 0,
    val timeSig: String = "4/4",
    val capo: Int = 0,
    val tags: String = "",
    val notes: String = "",
    val content: String = "",
    val favorite: Boolean = false,
    val hasPdf: Boolean = false,
    val hasScore: Boolean = false,
    val updatedAt: Long = 0
)

data class SetDto(val id: Long, val name: String, val count: Int)

data class SetEntryDto(
    val entryId: Long,
    val position: Int,
    val songId: Long,
    val title: String,
    val artist: String,
    val key: String
)

/** Accès aux morceaux et aux sets pour les serveurs (appels bloquants : ils tournent sur leurs propres threads). */
interface SongStore {
    fun list(): List<SongDto>
    fun get(id: Long): SongDto?
    /** id = 0 : création. Renvoie l'id du morceau enregistré. */
    fun save(song: SongDto): Long
    fun delete(id: Long)
    fun savePdf(id: Long, fileName: String, bytes: ByteArray): Boolean
    fun readPdf(id: Long): ByteArray?
    fun removePdf(id: Long)

    fun toggleFavorite(id: Long): Boolean?
    fun duplicate(id: Long): Long?
    fun exportText(id: Long): String?

    /** Importe une partition MuseScore ; lève MuseScore.ParseException si elle est illisible. */
    fun importScore(bytes: ByteArray, fileName: String, attachTo: Long?): Long
    fun removeScore(id: Long)
    fun scoreJson(id: Long): String?

    fun sets(): List<SetDto>
    fun setEntries(setId: Long): List<SetEntryDto>?
    fun createSet(name: String): Long
    fun renameSet(id: Long, name: String): Boolean
    fun deleteSet(id: Long)
    fun addToSet(setId: Long, songIds: List<Long>)
    fun removeFromSet(setId: Long, entryId: Long)
    fun moveInSet(setId: Long, entryId: Long, delta: Int)

    /** Chaîne qui change dès qu'un morceau ou un set change : permet à une page ouverte de se rafraîchir seule. */
    fun version(): String
    fun backupJson(): String
}

class AndroidSongStore(private val repo: Repository) : SongStore {
    private fun Song.toDto() = SongDto(
        id = id, uid = uid, title = title, artist = artist, key = songKey, tempo = tempo, timeSig = timeSig,
        capo = capo, tags = tags, notes = notes, content = content, favorite = favorite,
        hasPdf = pdfPath != null, hasScore = score.isNotBlank(), updatedAt = updatedAt
    )

    override fun list(): List<SongDto> = runBlocking { repo.allSongs().map { it.toDto() } }

    override fun get(id: Long): SongDto? = runBlocking { repo.getSong(id)?.toDto() }

    override fun save(song: SongDto): Long = runBlocking {
        if (song.id == 0L) {
            repo.saveSong(
                Song(
                    title = song.title, artist = song.artist, songKey = song.key, tempo = song.tempo,
                    timeSig = song.timeSig, capo = song.capo, tags = song.tags, notes = song.notes,
                    content = song.content
                )
            )
        } else {
            val old = repo.getSong(song.id) ?: return@runBlocking 0L
            repo.saveSong(
                old.copy(
                    title = song.title, artist = song.artist, songKey = song.key, tempo = song.tempo,
                    timeSig = song.timeSig, capo = song.capo, tags = song.tags, notes = song.notes,
                    content = song.content
                )
            )
        }
    }

    override fun delete(id: Long) = runBlocking {
        repo.getSong(id)?.let { repo.deleteSong(it) }
        Unit
    }

    override fun savePdf(id: Long, fileName: String, bytes: ByteArray): Boolean =
        runBlocking { repo.savePdfBytes(id, fileName, bytes) }

    override fun readPdf(id: Long): ByteArray? = runBlocking { repo.readPdfBytes(id) }

    override fun removePdf(id: Long) = runBlocking { repo.removePdf(id) }

    override fun toggleFavorite(id: Long): Boolean? = runBlocking { repo.toggleFavorite(id) }

    override fun duplicate(id: Long): Long? = runBlocking { repo.duplicateSong(id) }

    override fun exportText(id: Long): String? = runBlocking { repo.getSong(id)?.let { repo.exportText(it) } }

    override fun importScore(bytes: ByteArray, fileName: String, attachTo: Long?): Long =
        runBlocking { repo.importScoreBytes(bytes, fileName, attachTo) }

    override fun removeScore(id: Long) = runBlocking { repo.removeScore(id) }

    override fun scoreJson(id: Long): String? = runBlocking { repo.getSong(id)?.score?.takeIf { it.isNotBlank() } }

    override fun sets(): List<SetDto> = runBlocking {
        repo.setsWithCounts().map { (s, n) -> SetDto(s.id, s.name, n) }
    }

    override fun setEntries(setId: Long): List<SetEntryDto>? = runBlocking {
        if (repo.getSetList(setId) == null) {
            null
        } else {
            repo.setEntriesWithSong(setId).map {
                SetEntryDto(it.entryId, it.position, it.song.id, it.song.title, it.song.artist, it.song.songKey)
            }
        }
    }

    override fun createSet(name: String): Long = runBlocking { repo.createSetList(name.trim().ifBlank { "Nouveau set" }) }

    override fun renameSet(id: Long, name: String): Boolean = runBlocking {
        val s = repo.getSetList(id)
        if (s == null || name.isBlank()) {
            false
        } else {
            repo.renameSetList(s, name.trim())
            true
        }
    }

    override fun deleteSet(id: Long) = runBlocking {
        repo.getSetList(id)?.let { repo.deleteSetList(it) }
        Unit
    }

    override fun addToSet(setId: Long, songIds: List<Long>) = runBlocking { repo.addToSetList(setId, songIds) }

    override fun removeFromSet(setId: Long, entryId: Long) = runBlocking { repo.removeEntry(setId, entryId) }

    override fun moveInSet(setId: Long, entryId: Long, delta: Int) = runBlocking { repo.moveEntry(setId, entryId, delta) }

    override fun version(): String = runBlocking {
        val stamps = repo.stamps()
        val sets = repo.setsWithCounts()
        "${stamps.size}-${stamps.maxOfOrNull { it.updatedAt } ?: 0}-${sets.size}-${sets.sumOf { it.second }}"
    }

    override fun backupJson(): String = runBlocking { repo.backupJson() }
}
