package com.cadence.songs.server

import android.content.Context
import android.net.wifi.WifiManager
import com.cadence.songs.data.Repository
import com.cadence.songs.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Synchronisation automatique entre plusieurs appareils Cadence connectés au même Wi-Fi.
 *
 * - Chaque appareil annonce sa présence par un message UDP toutes les 4 secondes (port 8765) et sert ses morceaux
 *   sur un petit serveur HTTP (port 8099) protégé par le « code de synchronisation » commun à tous les appareils.
 * - Dès qu'un appareil voit qu'un autre a changé, il récupère ce qui est plus récent (le dernier modifié l'emporte).
 * - Morceaux, partitions MuseScore, PDF, sets et suppressions sont synchronisés. Les réglages de lecture propres
 *   à chaque appareil (transposition, vitesse de défilement, favoris, thème) ne le sont pas.
 *
 * Fonctionne tant que l'appli est ouverte. Les horloges des appareils doivent être à peu près à l'heure.
 */
class SyncManager(context: Context, private val repo: Repository) {
    data class State(
        val running: Boolean = false,
        val code: String = "",
        val peers: List<String> = emptyList(),
        val busy: Boolean = false,
        val lastSync: Long = 0,
        val message: String = "",
        val error: String? = null,
        val manualPeer: String = ""
    )

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cadence", Context.MODE_PRIVATE)
    private val deviceId: String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }

    private val _state = MutableStateFlow(
        State(code = currentCode(), manualPeer = prefs.getString("sync_peer", "").orEmpty())
    )
    val state: StateFlow<State> = _state

    private var server: MiniHttpServer? = null
    private var udpIn: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var active = false
    @Volatile private var httpPort = PORT_HTTP

    private class Peer(val host: String, val port: Int, var lastSeen: Long, var version: String)
    private val peers = ConcurrentHashMap<String, Peer>()
    private val lastSyncedVersion = ConcurrentHashMap<String, String>()
    private val working = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true; name = "cadence-sync" } }

    // ---------- Code de synchronisation ----------

    private fun currentCode(): String {
        val existing = prefs.getString("sync_code", null)
        if (!existing.isNullOrBlank()) return existing
        val code = Random.nextInt(100000, 1000000).toString()
        prefs.edit().putString("sync_code", code).apply()
        return code
    }

    private fun hash(): String {
        val d = MessageDigest.getInstance("SHA-256").digest(("cadence:" + currentCode()).toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }.take(24)
    }

    fun setCode(code: String) {
        val clean = code.trim()
        if (clean.length < 4) return
        prefs.edit().putString("sync_code", clean).apply()
        _state.value = _state.value.copy(code = clean)
        if (active) { stop(); start() }
    }

    fun newCode() = setCode(Random.nextInt(100000, 1000000).toString())

    fun setManualPeer(address: String) {
        prefs.edit().putString("sync_peer", address.trim()).apply()
        _state.value = _state.value.copy(manualPeer = address.trim())
    }

    // ---------- Démarrage / arrêt ----------

    @Synchronized
    fun start() {
        if (active) return
        try {
            runBlocking { repo.ensureUids() }
            val s = MiniHttpServer(PORT_HTTP) { handle(it) }
            httpPort = s.start()
            server = s
            runCatching {
                val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wm?.createMulticastLock("cadence-sync")?.apply { setReferenceCounted(false); acquire() }
            }
            active = true
            _state.value = _state.value.copy(running = true, error = null, message = "Recherche d'autres appareils…")
            Thread { listenLoop() }.apply { isDaemon = true; name = "cadence-udp-in" }.start()
            Thread { announceLoop() }.apply { isDaemon = true; name = "cadence-udp-out" }.start()
            Thread { periodicLoop() }.apply { isDaemon = true; name = "cadence-sync-timer" }.start()
        } catch (e: Exception) {
            active = false
            server?.stop()
            server = null
            _state.value = _state.value.copy(running = false, error = "Impossible de démarrer : ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        active = false
        runCatching { udpIn?.close() }
        udpIn = null
        server?.stop()
        server = null
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
        peers.clear()
        _state.value = _state.value.copy(running = false, peers = emptyList(), busy = false, message = "")
    }

    /** Lance tout de suite une synchronisation avec tous les appareils connus. */
    fun syncNow() {
        if (!active) return
        worker.execute { syncAll(force = true) }
    }

    // ---------- Découverte (UDP) ----------

    private fun localVersion(): String = runBlocking {
        val stamps = repo.stamps()
        "${stamps.size}.${stamps.maxOfOrNull { it.updatedAt } ?: 0}.${repo.tombstones().size}.${repo.setsForSync().sumOf { it.second.size + 1 }}"
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (ia in nif.interfaceAddresses) {
                    val b = ia.broadcast
                    if (b != null) out.add(b)
                }
            }
        }
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        return out.toList()
    }

    private fun announceLoop() {
        val socket = try {
            DatagramSocket().apply { broadcast = true }
        } catch (_: Exception) {
            return
        }
        try {
            while (active) {
                runCatching {
                    val msg = "CADENCE1|$deviceId|$httpPort|${hash().take(8)}|${localVersion()}".toByteArray(Charsets.UTF_8)
                    for (addr in broadcastAddresses()) {
                        runCatching { socket.send(DatagramPacket(msg, msg.size, addr, PORT_UDP)) }
                    }
                }
                Thread.sleep(4000)
            }
        } catch (_: Exception) {
            // arrêt demandé
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun listenLoop() {
        try {
            val socket = DatagramSocket(null as java.net.SocketAddress?).apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(PORT_UDP))
                soTimeout = 2000
            }
            udpIn = socket
            val buf = ByteArray(512)
            while (active) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    socket.receive(p)
                    onAnnounce(String(p.data, 0, p.length, Charsets.UTF_8), p.address.hostAddress.orEmpty())
                } catch (_: java.net.SocketTimeoutException) {
                    // normal : on boucle pour vérifier que la synchronisation est toujours active
                }
                prunePeers()
            }
        } catch (e: Exception) {
            if (active) _state.value = _state.value.copy(error = "Découverte automatique impossible : ${e.message}. Utilisez l'adresse manuelle.")
        }
    }

    private fun onAnnounce(text: String, host: String) {
        val parts = text.split('|')
        if (parts.size < 5 || parts[0] != "CADENCE1") return
        if (parts[1] == deviceId) return
        if (parts[3] != hash().take(8)) return
        val port = parts[2].toIntOrNull() ?: return
        val version = parts[4]
        val key = parts[1]
        val existing = peers[key]
        if (existing == null) {
            peers[key] = Peer(host, port, System.currentTimeMillis(), version)
        } else {
            existing.lastSeen = System.currentTimeMillis()
            existing.version = version
        }
        publishPeers()
        if (lastSyncedVersion[key] != version) {
            worker.execute { syncPeer(key, host, port, version) }
        }
    }

    private fun prunePeers() {
        val now = System.currentTimeMillis()
        val removed = peers.entries.removeIf { now - it.value.lastSeen > 20_000 }
        if (removed) publishPeers()
    }

    private fun publishPeers() {
        _state.value = _state.value.copy(peers = peers.values.map { "${it.host}:${it.port}" }.sorted())
    }

    private fun periodicLoop() {
        var tick = 0
        while (active) {
            try {
                Thread.sleep(10_000)
            } catch (_: InterruptedException) {
                return
            }
            tick++
            if (!active) return
            val manual = _state.value.manualPeer
            if (manual.isNotBlank()) worker.execute { syncManual(manual) }
            if (tick % 6 == 0) worker.execute { syncAll(force = true) }
        }
    }

    // ---------- Client : récupérer ce qui est plus récent chez un autre appareil ----------

    private fun syncAll(force: Boolean) {
        for ((key, p) in peers) syncPeer(key, p.host, p.port, if (force) "" else p.version)
        val manual = _state.value.manualPeer
        if (manual.isNotBlank()) syncManual(manual)
    }

    private fun syncManual(address: String) {
        val clean = address.removePrefix("http://").trim()
        val host = clean.substringBefore(':')
        val port = clean.substringAfter(':', "").toIntOrNull() ?: PORT_HTTP
        if (host.isBlank()) return
        syncPeer("manual:$host", host, port, "")
    }

    private fun syncPeer(key: String, host: String, port: Int, seenVersion: String) {
        if (!working.compareAndSet(false, true)) return
        _state.value = _state.value.copy(busy = true)
        try {
            val received = pullFrom("http://$host:$port")
            if (seenVersion.isNotEmpty()) lastSyncedVersion[key] = seenVersion
            val msg = if (received > 0) "$received élément(s) reçu(s) de $host" else "À jour avec $host"
            _state.value = _state.value.copy(lastSync = System.currentTimeMillis(), message = msg, error = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "Synchronisation avec $host impossible : ${e.message ?: "erreur réseau"}")
        } finally {
            working.set(false)
            _state.value = _state.value.copy(busy = false)
        }
    }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 25000
        c.setRequestProperty("X-Sync", hash())
        try {
            val code = c.responseCode
            if (code == 401) throw IOException("code de synchronisation différent")
            if (code != 200) throw IOException("réponse HTTP $code")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    /** Renvoie le nombre d'éléments reçus (morceaux, PDF, suppressions). */
    private fun pullFrom(base: String): Int = runBlocking {
        var received = 0
        val manifest = Json.parseObject(String(get("$base/sync/manifest"), Charsets.UTF_8))
        val localStamps = repo.stamps().associateBy { it.uid }
        val localTombs = repo.tombstones()

        // 1. Suppressions faites ailleurs.
        for (d in (manifest["deleted"] as? List<*>).orEmpty()) {
            val m = d as? Map<*, *> ?: continue
            val uid = m["uid"] as? String ?: continue
            val ts = (m["ts"] as? Number)?.toLong() ?: continue
            if (localStamps.containsKey(uid)) {
                repo.deleteByUid(uid, ifOlderThan = ts)
                received++
            }
        }

        // 2. Morceaux nouveaux ou plus récents.
        for (s in (manifest["songs"] as? List<*>).orEmpty()) {
            val m = s as? Map<*, *> ?: continue
            val uid = m["uid"] as? String ?: continue
            val remoteTime = (m["updatedAt"] as? Number)?.toLong() ?: continue
            val remoteHasPdf = m["hasPdf"] == true
            val local = localStamps[uid]
            val tomb = localTombs[uid]
            if (tomb != null && tomb >= remoteTime) continue // supprimé ici après sa dernière modification
            val needSong = local == null || remoteTime > local.updatedAt
            if (needSong) {
                val o = Json.parseObject(String(get("$base/sync/song/" + uid), Charsets.UTF_8))
                repo.applyRemoteSong(songFromJson(o))
                received++
            }
            if (remoteHasPdf && (needSong || local?.hasPdf == false)) {
                runCatching {
                    val bytes = get("$base/sync/pdf/" + uid)
                    if (repo.applyRemotePdf(uid, "partition.pdf", bytes)) received++
                }
            }
        }

        // 3. Sets : on ajoute ceux qui manquent.
        val remoteSets = (manifest["sets"] as? List<*>).orEmpty().mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val name = m["name"] as? String ?: return@mapNotNull null
            name to (m["songs"] as? List<*>).orEmpty().mapNotNull { it as? String }
        }
        repo.mergeSets(remoteSets)
        received
    }

    private fun songFromJson(o: Map<String, Any?>): Song {
        fun str(k: String) = (o[k] as? String).orEmpty()
        fun int(k: String) = (o[k] as? Number)?.toInt() ?: 0
        fun long(k: String) = (o[k] as? Number)?.toLong() ?: 0L
        return Song(
            uid = str("uid"), title = str("title"), artist = str("artist"), songKey = str("key"),
            tempo = int("tempo"), timeSig = str("timeSig").ifBlank { "4/4" }, capo = int("capo"),
            content = str("content"), tags = str("tags"), notes = str("notes"), score = str("score"),
            createdAt = long("createdAt"), updatedAt = long("updatedAt")
        )
    }

    // ---------- Serveur : ce que les autres appareils viennent chercher ----------

    private fun handle(req: HttpRequest): HttpResponse {
        if (req.method != "GET") return httpError(405, "Méthode non permise")
        if (req.headers["x-sync"] != hash()) {
            Thread.sleep(300)
            return httpError(401, "Code de synchronisation incorrect")
        }
        val path = req.path.trimEnd('/')
        return try {
            when {
                path == "/sync/manifest" -> manifest()
                path.startsWith("/sync/song/") -> song(path.removePrefix("/sync/song/"))
                path.startsWith("/sync/pdf/") -> pdf(path.removePrefix("/sync/pdf/"))
                else -> httpError(404, "Route inconnue")
            }
        } catch (e: Exception) {
            httpError(500, e.message ?: "Erreur")
        }
    }

    private fun manifest(): HttpResponse = runBlocking {
        repo.ensureUids()
        val songs = repo.stamps().map { mapOf("uid" to it.uid, "updatedAt" to it.updatedAt, "hasPdf" to it.hasPdf) }
        val deleted = repo.tombstones().map { mapOf("uid" to it.key, "ts" to it.value) }
        val sets = repo.setsForSync().map { mapOf("name" to it.first, "songs" to it.second) }
        HttpResponse.json(200, Json.stringify(mapOf("device" to deviceId, "songs" to songs, "deleted" to deleted, "sets" to sets)))
    }

    private fun song(uid: String): HttpResponse = runBlocking {
        val s = repo.songByUid(uid) ?: return@runBlocking httpError(404, "Morceau introuvable")
        HttpResponse.json(
            200,
            Json.stringify(
                mapOf(
                    "uid" to s.uid, "title" to s.title, "artist" to s.artist, "key" to s.songKey, "tempo" to s.tempo,
                    "timeSig" to s.timeSig, "capo" to s.capo, "content" to s.content, "tags" to s.tags,
                    "notes" to s.notes, "score" to s.score, "createdAt" to s.createdAt, "updatedAt" to s.updatedAt
                )
            )
        )
    }

    private fun pdf(uid: String): HttpResponse = runBlocking {
        val s = repo.songByUid(uid) ?: return@runBlocking httpError(404, "Morceau introuvable")
        val bytes = repo.readPdfBytes(s.id) ?: return@runBlocking httpError(404, "Pas de PDF")
        HttpResponse(200, "application/pdf", bytes)
    }

    companion object {
        const val PORT_HTTP = 8099
        const val PORT_UDP = 8765

        /** Adresses IPv4 locales de l'appareil (pour l'afficher à l'écran). */
        fun localAddresses(): List<String> {
            val out = mutableListOf<String>()
            runCatching {
                for (nif in NetworkInterface.getNetworkInterfaces()) {
                    if (!nif.isUp || nif.isLoopback) continue
                    for (addr in nif.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                            out.add(addr.hostAddress.orEmpty())
                        }
                    }
                }
            }
            return out
        }
    }
}
