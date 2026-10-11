package com.cadence.songs.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cadence.songs.CadenceApp
import com.cadence.songs.data.EntryWithSong
import com.cadence.songs.data.Repository
import com.cadence.songs.data.SetList
import com.cadence.songs.data.Settings
import com.cadence.songs.data.SettingsStore
import com.cadence.songs.data.Song
import com.cadence.songs.server.SyncManager
import com.cadence.songs.server.WifiTransfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val repo: Repository,
    private val store: SettingsStore,
    val wifi: WifiTransfer,
    val sync: SyncManager
) : ViewModel() {

    val songs: StateFlow<List<Song>> =
        repo.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val setLists: StateFlow<List<SetList>> =
        repo.setLists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val setCounts: StateFlow<Map<Long, Int>> =
        repo.setCounts.map { list -> list.associate { it.setListId to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val settings: StateFlow<Settings> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun song(id: Long): Flow<Song?> = repo.observeSong(id)
    fun setList(id: Long): Flow<SetList?> = repo.observeSetList(id)
    fun entries(id: Long): Flow<List<EntryWithSong>> = repo.observeEntries(id)
    suspend fun getSong(id: Long): Song? = repo.getSong(id)
    fun exportText(song: Song): String = repo.exportText(song)

    fun saveSong(song: Song, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch { onDone(repo.saveSong(song)) }
    }

    fun updateSong(song: Song) {
        viewModelScope.launch { repo.updateSong(song) }
    }

    fun toggleFavorite(song: Song) = updateSong(song.copy(favorite = !song.favorite))

    fun deleteSong(song: Song) {
        viewModelScope.launch { repo.deleteSong(song) }
    }

    fun createSetList(name: String, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch { onDone(repo.createSetList(name)) }
    }

    fun renameSetList(setList: SetList, name: String) {
        viewModelScope.launch { repo.renameSetList(setList, name) }
    }

    fun deleteSetList(setList: SetList) {
        viewModelScope.launch { repo.deleteSetList(setList) }
    }

    fun addToSet(setId: Long, songIds: List<Long>) {
        viewModelScope.launch { repo.addToSetList(setId, songIds) }
    }

    fun removeEntry(setId: Long, entryId: Long) {
        viewModelScope.launch { repo.removeEntry(setId, entryId) }
    }

    fun moveEntry(setId: Long, entryId: Long, delta: Int) {
        viewModelScope.launch { repo.moveEntry(setId, entryId, delta) }
    }

    fun importUris(uris: List<Uri>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            var ok = 0
            for (uri in uris) if (repo.importFile(uri) != null) ok++
            onDone(ok)
        }
    }

    fun attachPdf(uri: Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch { onDone(repo.copyPdf(uri)) }
    }

    fun exportBackup(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.exportBackup(uri)) }
    }

    fun importBackup(uri: Uri, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repo.importBackup(uri)) }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { store.update(transform) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CadenceApp
                AppViewModel(app.repo, app.settingsStore, app.wifi, app.sync)
            }
        }
    }
}
