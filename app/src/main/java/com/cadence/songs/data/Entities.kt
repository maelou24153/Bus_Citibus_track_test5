package com.cadence.songs.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val artist: String = "",
    val songKey: String = "",
    val tempo: Int = 0,
    val timeSig: String = "4/4",
    val capo: Int = 0,
    val transpose: Int = 0,
    val scrollSpeed: Int = 6,
    val content: String = "",
    val tags: String = "",
    val favorite: Boolean = false,
    val pdfPath: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Identifiant stable, identique sur tous les appareils (sert à la synchronisation Wi-Fi). */
    val uid: String = "",
    /** Partition MuseScore convertie (JSON compact, voir music/Score.kt). Vide = pas de partition. */
    val score: String = ""
)

/** Version légère d'un morceau, pour comparer deux appareils sans charger tout le contenu. */
data class SongStamp(val uid: String, val updatedAt: Long, val hasPdf: Boolean)

@Entity(tableName = "setlists")
data class SetList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "set_entries",
    indices = [Index("setListId"), Index("songId")],
    foreignKeys = [
        ForeignKey(
            entity = SetList::class,
            parentColumns = ["id"],
            childColumns = ["setListId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setListId: Long,
    val songId: Long,
    val position: Int
)

data class EntryWithSong(
    @Embedded val song: Song,
    val entryId: Long,
    val position: Int
)

data class SetCount(val setListId: Long, val count: Int)
