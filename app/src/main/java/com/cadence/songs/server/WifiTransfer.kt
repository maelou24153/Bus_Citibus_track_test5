package com.cadence.songs.server

import android.content.Context
import com.cadence.songs.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

/**
 * Les deux serveurs web de l'appli, activables séparément :
 *
 * - **Serveur 1 – Administration** (port 8080) : modifier les morceaux, créer des sets, joindre des PDF et des
 *   partitions MuseScore. Toujours protégé par un code à 4 chiffres.
 * - **Serveur 2 – Lecteur** (port 8090) : page en lecture seule pour lire les morceaux depuis un autre ordinateur
 *   ou téléphone. Code à 4 chiffres facultatif.
 *
 * Les serveurs tournent tant que l'appli reste ouverte.
 */
class WifiTransfer(context: Context, repo: Repository) {
    data class State(
        val running: Boolean = false,
        val addresses: List<String> = emptyList(),
        val pin: String = "",
        val error: String? = null,
        val readerRunning: Boolean = false,
        val readerAddresses: List<String> = emptyList(),
        val readerUseCode: Boolean = false,
        val readerPin: String = "",
        val readerError: String? = null
    )

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cadence", Context.MODE_PRIVATE)
    private val store: SongStore = AndroidSongStore(repo)

    private val adminHtml: String by lazy { readAsset("admin.html") }
    private val readerHtml: String by lazy { readAsset("reader.html") }

    private val admin = WebApi(store, { adminHtml }) { currentPin() }
    private val reader = ReaderApi(store, { readerHtml }) { if (readerUseCode()) currentReaderPin() else null }

    private var adminServer: MiniHttpServer? = null
    private var readerServer: MiniHttpServer? = null

    private val _state = MutableStateFlow(
        State(pin = currentPin(), readerUseCode = readerUseCode(), readerPin = currentReaderPin())
    )
    val state: StateFlow<State> = _state

    private fun readAsset(name: String): String =
        appContext.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    // ---------- Codes ----------

    private fun randomPin() = Random.nextInt(0, 10000).toString().padStart(4, '0')

    private fun currentPin(): String {
        val existing = prefs.getString("wifi_pin", null)
        if (existing != null && existing.length == 4) return existing
        return randomPin().also { prefs.edit().putString("wifi_pin", it).apply() }
    }

    private fun currentReaderPin(): String {
        val existing = prefs.getString("reader_pin", null)
        if (existing != null && existing.length == 4) return existing
        return randomPin().also { prefs.edit().putString("reader_pin", it).apply() }
    }

    private fun readerUseCode(): Boolean = prefs.getBoolean("reader_use_code", false)

    fun newPin() {
        prefs.edit().putString("wifi_pin", randomPin()).apply()
        _state.value = _state.value.copy(pin = currentPin())
    }

    fun newReaderPin() {
        prefs.edit().putString("reader_pin", randomPin()).apply()
        _state.value = _state.value.copy(readerPin = currentReaderPin())
    }

    fun setReaderUseCode(on: Boolean) {
        prefs.edit().putBoolean("reader_use_code", on).apply()
        _state.value = _state.value.copy(readerUseCode = on)
    }

    // ---------- Serveur 1 : administration ----------

    fun refreshAddresses() {
        val a = adminServer
        val r = readerServer
        _state.value = _state.value.copy(
            addresses = if (a != null && a.isRunning) localUrls(a.port) else _state.value.addresses,
            readerAddresses = if (r != null && r.isRunning) localUrls(r.port) else _state.value.readerAddresses
        )
    }

    @Synchronized
    fun start() {
        if (adminServer?.isRunning == true) return
        try {
            val s = MiniHttpServer(8080) { admin.handle(it) }
            val port = s.start()
            adminServer = s
            _state.value = _state.value.copy(running = true, addresses = localUrls(port), pin = currentPin(), error = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(running = false, error = "Impossible de démarrer : ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        adminServer?.stop()
        adminServer = null
        _state.value = _state.value.copy(running = false, addresses = emptyList(), pin = currentPin())
    }

    // ---------- Serveur 2 : lecteur (lecture seule) ----------

    @Synchronized
    fun startReader() {
        if (readerServer?.isRunning == true) return
        try {
            val s = MiniHttpServer(8090) { reader.handle(it) }
            val port = s.start()
            readerServer = s
            _state.value = _state.value.copy(readerRunning = true, readerAddresses = localUrls(port), readerError = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(readerRunning = false, readerError = "Impossible de démarrer : ${e.message}")
        }
    }

    @Synchronized
    fun stopReader() {
        readerServer?.stop()
        readerServer = null
        _state.value = _state.value.copy(readerRunning = false, readerAddresses = emptyList())
    }

    private fun localUrls(port: Int): List<String> {
        val found = mutableListOf<Pair<Boolean, String>>()
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val wifi = nif.name.startsWith("wlan") || nif.name.startsWith("eth")
                        found.add(wifi to "http://${addr.hostAddress}:$port")
                    }
                }
            }
        }
        return found.sortedByDescending { it.first }.map { it.second }
    }
}
