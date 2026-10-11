package com.cadence.songs.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cadence.songs.ui.AppViewModel
import com.cadence.songs.ui.common.EmptyState
import com.cadence.songs.ui.library.LibraryList
import com.cadence.songs.ui.settings.SettingsScreen
import com.cadence.songs.ui.setlists.SetListsTab
import com.cadence.songs.ui.viewer.SongViewerScreen

private data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun HomeScreen(
    vm: AppViewModel,
    wide: Boolean,
    onOpenSong: (Long) -> Unit,
    onEditSong: (Long) -> Unit,
    onOpenSet: (Long) -> Unit,
    onImportWeb: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    // Tablette : masque le rail et la liste pour lire en plein écran.
    var panelsHidden by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = panelsHidden) { panelsHidden = false }
    val items = listOf(
        NavItem("Morceaux", Icons.Rounded.LibraryMusic),
        NavItem("Sets", Icons.AutoMirrored.Rounded.QueueMusic),
        NavItem("Réglages", Icons.Rounded.Settings)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!wide) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        Row(
            Modifier
                .padding(inner)
                .consumeWindowInsets(inner)
                .fillMaxSize()
        ) {
            if (wide && !panelsHidden) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Spacer(Modifier.height(16.dp))
                    items.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (tab) {
                    0 -> if (wide) {
                        Row(Modifier.fillMaxSize()) {
                            if (!panelsHidden) Box(
                                Modifier
                                    .width(400.dp)
                                    .fillMaxHeight()
                            ) {
                                LibraryList(
                                    vm = vm,
                                    selectedId = selected,
                                    onOpen = { selected = it },
                                    onEdit = onEditSong,
                                    onNew = { onEditSong(0L) },
                                    onImportWeb = onImportWeb,
                                    onDeleted = { id -> if (selected == id) selected = null }
                                )
                            }
                            if (!panelsHidden) VerticalDivider()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                val current = selected
                                if (current != null) {
                                    SongViewerScreen(
                                        vm = vm,
                                        songId = current,
                                        setId = -1L,
                                        onBack = null,
                                        onEdit = onEditSong,
                                        onTogglePanels = { panelsHidden = !panelsHidden },
                                        panelsHidden = panelsHidden
                                    )
                                } else {
                                    EmptyState(
                                        icon = Icons.Rounded.MusicNote,
                                        title = "Choisissez un morceau",
                                        subtitle = "Sélectionnez une chanson dans la liste pour l'afficher ici."
                                    )
                                }
                            }
                        }
                    } else {
                        LibraryList(
                            vm = vm,
                            selectedId = null,
                            onOpen = onOpenSong,
                            onEdit = onEditSong,
                            onNew = { onEditSong(0L) },
                                    onImportWeb = onImportWeb,
                            onDeleted = {}
                        )
                    }
                    1 -> SetListsTab(vm = vm, onOpenSet = onOpenSet)
                    else -> SettingsScreen(vm = vm)
                }
            }
        }
    }
}
