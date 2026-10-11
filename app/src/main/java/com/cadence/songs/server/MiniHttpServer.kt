package com.cadence.songs.server

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    /** Noms d'en-têtes en minuscules. */
    val headers: Map<String, String>,
    val body: ByteArray
) {
    fun bodyText(): String = String(body, Charsets.UTF_8)
}

class HttpResponse(
    val status: Int,
    val contentType: String,
    val body: ByteArray,
    val headers: Map<String, String> = emptyMap()
) {
    companion object {
        fun json(status: Int, json: String) = HttpResponse(status, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))
        fun html(html: String) = HttpResponse(200, "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
        fun text(status: Int, text: String) = HttpResponse(status, "text/plain; charset=utf-8", text.toByteArray(Charsets.UTF_8))
    }
}

/**
 * Serveur HTTP minimal (une requête par connexion), sans dépendance : suffisant pour une page web
 * servie sur le réseau local. Écoute sur toutes les interfaces ; le [handler] est appelé sur un thread
 * de travail et doit gérer lui-même l'authentification.
 */
class MiniHttpServer(
    private val preferredPort: Int = 8080,
    private val maxBodyBytes: Int = 80 * 1024 * 1024,
    private val handler: (HttpRequest) -> HttpResponse
) {
    @Volatile private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true; name = "wifi-http" } }

    val port: Int get() = server?.localPort ?: -1
    val isRunning: Boolean get() = server?.isClosed == false

    /** Démarre le serveur ; essaie quelques ports voisins si le premier est occupé. Renvoie le port utilisé. */
    @Synchronized
    fun start(): Int {
        if (isRunning) return port
        var last: Exception? = null
        for (p in preferredPort..preferredPort + 10) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(p))
                server = s
                Thread {
                    while (!s.isClosed) {
                        try {
                            val c = s.accept()
                            pool.execute { serve(c) }
                        } catch (e: Exception) {
                            if (s.isClosed) break
                        }
                    }
                }.apply { isDaemon = true; name = "wifi-accept" }.start()
                return p
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Aucun port disponible")
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun serve(socket: Socket) {
        socket.use { sock ->
            try {
                sock.soTimeout = 30_000
                val input = BufferedInputStream(sock.getInputStream())
                val req = readRequest(input)
                val resp = if (req == null) {
                    HttpResponse.text(400, "Requête invalide")
                } else if (req == TOO_BIG) {
                    HttpResponse.text(413, "Fichier trop volumineux")
                } else {
                    try {
                        handler(req)
                    } catch (e: Exception) {
                        HttpResponse.text(500, "Erreur interne : ${e.message}")
                    }
                }
                writeResponse(sock, resp)
            } catch (_: Exception) {
                // connexion coupée par le navigateur : rien à faire
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.size() == 0) null else String(sb.toByteArray(), Charsets.ISO_8859_1)
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.write(b)
            if (sb.size() > 16 * 1024) return null
        }
        return String(sb.toByteArray(), Charsets.ISO_8859_1)
    }

    private val TOO_BIG = HttpRequest("", "", emptyMap(), emptyMap(), ByteArray(0))

    private fun readRequest(input: InputStream): HttpRequest? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        if (length > maxBodyBytes) return TOO_BIG
        val body = ByteArray(length.toInt())
        var read = 0
        while (read < body.size) {
            val n = input.read(body, read, body.size - read)
            if (n < 0) return null
            read += n
        }
        val q = target.indexOf('?')
        val rawPath = if (q >= 0) target.substring(0, q) else target
        val rawQuery = if (q >= 0) target.substring(q + 1) else ""
        val query = HashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val k = if (eq >= 0) pair.substring(0, eq) else pair
            val v = if (eq >= 0) pair.substring(eq + 1) else ""
            query[decodeQuery(k)] = decodeQuery(v)
        }
        return HttpRequest(method, decodePath(rawPath), query, headers, body)
    }

    private fun decodePath(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }

    private fun decodeQuery(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: Exception) {
        s
    }

    private fun writeResponse(sock: Socket, r: HttpResponse) {
        val reason = when (r.status) {
            200 -> "OK"; 201 -> "Created"; 204 -> "No Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"
            404 -> "Not Found"; 405 -> "Method Not Allowed"; 413 -> "Payload Too Large"; else -> "Error"
        }
        val head = StringBuilder()
        head.append("HTTP/1.1 ${r.status} $reason\r\n")
        head.append("Content-Type: ${r.contentType}\r\n")
        head.append("Content-Length: ${r.body.size}\r\n")
        head.append("Cache-Control: no-store\r\n")
        head.append("Connection: close\r\n")
        for ((k, v) in r.headers) head.append("$k: $v\r\n")
        head.append("\r\n")
        val out = sock.getOutputStream()
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(r.body)
        out.flush()
    }
}
