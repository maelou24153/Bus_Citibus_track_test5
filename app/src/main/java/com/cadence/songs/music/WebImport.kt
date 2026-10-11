package com.cadence.songs.music

import com.cadence.songs.server.Json

/**
 * Extrait une chanson (paroles + accords) d'une page web déjà chargée dans le navigateur de l'appli.
 * Fonctionne en trois temps : le texte sélectionné par l'utilisateur, puis les données d'Ultimate Guitar,
 * puis les blocs « préformatés » (<pre>) que la plupart des sites d'accords utilisent.
 * Aucune dépendance Android.
 */
object WebImport {
    private val entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    fun unescapeHtml(s: String): String = entity.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            else -> when (e) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                "eacute" -> "é"; "egrave" -> "è"; "agrave" -> "à"; "ecirc" -> "ê"; "ccedil" -> "ç"
                "ocirc" -> "ô"; "ugrave" -> "ù"; "icirc" -> "î"; "acirc" -> "â"; "rsquo", "lsquo" -> "'"
                "hellip" -> "…"
                else -> m.value
            }
        }
    }

    private fun htmlToText(fragment: String): String {
        var t = fragment
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|li|tr|h[1-6])>", RegexOption.IGNORE_CASE), "\n")
        t = t.replace(Regex("<[^>]+>"), "")
        return unescapeHtml(t).replace("\r\n", "\n").replace('\r', '\n').replace('\u00A0', ' ')
    }

    private fun cleanTitle(pageTitle: String): String {
        var t = pageTitle.substringBefore('|').trim()
        t = t.replace(
            Regex("\\s*[-–(]?\\s*(chords?|tabs?|accords?|paroles?|lyrics?|tablature|ukulele|guitar|guitare)(\\s*(&|et|and)\\s*(tabs?|accords?|chords?|paroles?|lyrics?))?\\s*\\)?\\s*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        return t.ifBlank { "Chanson importée" }
    }

    /** Renvoie la chanson trouvée, ou null si rien d'exploitable (l'utilisateur doit alors sélectionner le texte). */
    fun extract(html: String, selection: String, pageTitle: String): ImportedSong? {
        val fallbackTitle = cleanTitle(pageTitle)

        val sel = selection.replace('\u00A0', ' ').trim('\n', '\r')
        if (sel.trim().length >= 20 && sel.contains('\n')) {
            return ChordPro.importText(sel, fallbackTitle)
        }
        ultimateGuitar(html)?.let { return it }

        // Blocs <pre> : on garde le plus long qui ressemble à une chanson.
        val pres = Regex("<pre[^>]*>(.*?)</pre>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html).map { htmlToText(it.groupValues[1]).trim('\n') }
            .filter { it.lines().size >= 4 }
            .sortedByDescending { it.length }
            .toList()
        for (text in pres) {
            val lines = text.lines()
            val chordLines = lines.count { ChordPro.isChordLine(it) }
            if (chordLines >= 2 || ChordPro.hasInlineChords(text)) {
                val h1 = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .find(html)?.groupValues?.get(1)?.let { htmlToText(it).trim() }
                return ChordPro.importText(text, cleanTitle(h1?.takeIf { it.isNotBlank() } ?: pageTitle))
            }
        }
        return null
    }

    /** Ultimate Guitar place les données de la page dans un attribut data-content (JSON). */
    private fun ultimateGuitar(html: String): ImportedSong? {
        val attr = Regex("class=\"js-store\"[^>]*data-content=\"([^\"]*)\"").find(html)
            ?: Regex("data-content=\"([^\"]*)\"[^>]*class=\"js-store\"").find(html)
            ?: return null
        val json = unescapeHtml(attr.groupValues[1])
        val tabAt = json.indexOf("\"wiki_tab\"")
        val from = if (tabAt >= 0) tabAt else 0
        val content = Regex("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json, from) ?: return null
        val raw = (runCatching { Json.parse("\"" + content.groupValues[1] + "\"") }.getOrNull() as? String) ?: return null
        fun field(name: String) = Regex("\"$name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)
            ?.let { runCatching { Json.parse("\"$it\"") as? String }.getOrNull() }.orEmpty()

        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
        text = text.replace(Regex("\\[/?ch]"), "")
        text = text.replace(Regex("\\[tab]\\s*\n?"), "{start_of_tab}\n")
            .replace(Regex("\\s*\\[/tab]"), "\n{end_of_tab}")
        val song = ChordPro.importText(text, field("song_name").ifBlank { "Chanson importée" })
        val artist = field("artist_name")
        val title = field("song_name")
        return song.copy(
            title = title.ifBlank { song.title },
            artist = artist.ifBlank { song.artist }
        )
    }
}
