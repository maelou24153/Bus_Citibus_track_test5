package com.cadence.songs

import android.app.Application
import com.cadence.songs.data.AppDatabase
import com.cadence.songs.data.Repository
import com.cadence.songs.data.SettingsStore
import com.cadence.songs.data.Song
import com.cadence.songs.server.SyncManager
import com.cadence.songs.server.WifiTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CadenceApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var wifi: WifiTransfer
        private set
    lateinit var sync: SyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this, AppDatabase.build(this))
        settingsStore = SettingsStore(this)
        wifi = WifiTransfer(this, repo)
        sync = SyncManager(this, repo)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repo.ensureUids() }

        // Les serveurs et la synchronisation qui étaient activés restent activés au prochain lancement.
        val flags = getSharedPreferences("cadence", MODE_PRIVATE)
        if (flags.getBoolean("auto_admin", false)) wifi.start()
        if (flags.getBoolean("auto_reader", false)) wifi.startReader()
        if (flags.getBoolean("auto_sync", false)) sync.start()

        val prefs = getSharedPreferences("cadence", MODE_PRIVATE)
        if (!prefs.getBoolean("seeded", false)) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                repo.seedIfEmpty(sampleSong())
                prefs.edit().putBoolean("seeded", true).apply()
            }
        }
    }

    private fun sampleSong() = Song(
        title = "Bienvenue dans Cadence",
        artist = "Morceau d'exemple",
        songKey = "C",
        tempo = 96,
        timeSig = "4/4",
        tags = "exemple",
        content = """
{start_of_verse: Couplet 1}
[C]Les doigts sur les [G]cordes, la nuit qui [Am]tombe
[F]Une mélodie qui [C]monte, sans faire de [G]bruit
{end_of_verse}

{start_of_chorus}
[F]Chante avec [C]moi, la [G]scène est à [Am]toi
[F]Chante avec [C]moi, [G]un accord, [C]c'est tout
{end_of_chorus}

{start_of_verse: Couplet 2}
[C]Transpose d'un [G]geste, change de to[Am]nalité
[F]Lance le défilement, [C]garde les mains [G]libres
{end_of_verse}

{comment: Touchez l'écran pour masquer les commandes}
""".trimIndent()
    )
}
