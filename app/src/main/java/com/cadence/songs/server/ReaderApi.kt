package com.cadence.songs.server

/**
 * Serveur n°2 « Cadence lecteur » : une page de lecture seule pour un autre ordinateur ou téléphone du même Wi-Fi.
 * Rien ne peut être modifié ni supprimé : seules les requêtes GET sont acceptées.
 * Un code à 4 chiffres est demandé seulement si [pin] renvoie une valeur non nulle.
 */
class ReaderApi(
    store: SongStore,
    private val html: () -> String,
    private val pin: () -> String?
) {
    private val reads = ReadRoutes(store)

    fun handle(req: HttpRequest): HttpResponse {
        val path = req.path.trimEnd('/').ifEmpty { "/" }
        if (req.method != "GET") return httpError(405, "Lecture seule : ce serveur ne permet aucune modification")
        if (path == "/" || path == "/index.html") return HttpResponse.html(html())
        if (!path.startsWith("/api/")) return HttpResponse.text(404, "Introuvable")

        val required = pin()
        if (required != null) {
            val given = req.headers["x-pin"] ?: req.query["pin"] ?: ""
            if (given != required) {
                Thread.sleep(400)
                return httpError(401, "Code incorrect")
            }
        }
        return try {
            reads.handle(req, path.removePrefix("/api/").split('/')) ?: httpError(404, "Route inconnue")
        } catch (e: Exception) {
            httpError(500, e.message ?: "Erreur")
        }
    }
}
