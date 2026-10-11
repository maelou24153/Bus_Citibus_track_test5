package com.cadence.songs.ui.viewer

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast

enum class ListenService { SPOTIFY, YOUTUBE }

/**
 * Ouvre la chanson dans Spotify ou YouTube. Spotify : lecture directe de la recherche si l'appli est installée,
 * sinon la recherche s'ouvre ; YouTube : résultats de recherche (appli YouTube si présente, sinon le navigateur).
 */
fun listenOn(context: Context, title: String, artist: String, service: ListenService) {
    val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
    if (query.isBlank()) {
        Toast.makeText(context, "Ce morceau n'a pas de titre.", Toast.LENGTH_SHORT).show()
        return
    }
    val encoded = Uri.encode(query)
    val attempts: List<Intent> = when (service) {
        ListenService.SPOTIFY -> listOf(
            Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage("com.spotify.music")
                putExtra(SearchManager.QUERY, query)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(MediaStore.EXTRA_MEDIA_TITLE, title)
                if (artist.isNotBlank()) putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist)
            },
            Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encoded"))
        )
        ListenService.YOUTUBE -> listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded"))
                .setPackage("com.google.android.youtube"),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded"))
        )
    }
    for (intent in attempts) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // on essaie l'option suivante
        } catch (e: SecurityException) {
            // idem
        }
    }
    Toast.makeText(context, "Impossible d'ouvrir ${if (service == ListenService.SPOTIFY) "Spotify" else "YouTube"}.", Toast.LENGTH_SHORT).show()
}
