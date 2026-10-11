package com.cadence.songs.ui.setlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cadence.songs.data.EntryWithSong
import com.cadence.songs.ui.AppViewModel
import com.cadence.songs.ui.common.ConfirmDialog
import com.cadence.songs.ui.common.EmptyState
import com.cadence.songs.ui.common.KeyBadge
import com.cadence.songs.ui.common.NameDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDetailScreen(
    vm: AppViewModel,
    setId: Long,
    onBack: () -> Unit,
    onOpenSong: (Long) -> Unit
) {
    val setList by remember(setId) { vm.setList(setId) }.collectAsState(initial = null)
    val entries by remember(setId) { vm.entries(setId) }.collectAsState(initial = emptyList<EntryWithSong>())
    val songs by vm.songs.collectAsState()

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(setList?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Renommer") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, "Supprimer le set") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (entries.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenSong(entries.first().song.id) },
                    icon = { Icon(Icons.Rounded.PlayArrow, null) },
                    text = { Text("Jouer le set") }
                )
            }
        }
    ) { inner ->
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = "Set vide",
                    subtitle = "Ajoutez des morceaux dans l'ordre où vous les jouerez.",
                    action = {
                        FilledTonalButton(onClick = { picking = true }) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ajouter des morceaux")
                        }
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(entries, key = { _, e -> e.entryId }) { index, e ->
                        Surface(
                            onClick = { onOpenSong(e.song.id) },
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(26.dp)
                                )
                                KeyBadge(e.song)
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        e.song.title.ifBlank { "Sans titre" },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (e.song.artist.isNotBlank()) {
                                        Text(
                                            e.song.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Column {
                                    IconButton(
                                        onClick = { vm.moveEntry(setId, e.entryId, -1) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Rounded.KeyboardArrowUp, "Monter") }
                                    IconButton(
                                        onClick = { vm.moveEntry(setId, e.entryId, 1) },
                                        enabled = index < entries.size - 1,
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Rounded.KeyboardArrowDown, "Descendre") }
                                }
                                IconButton(onClick = { vm.removeEntry(setId, e.entryId) }) {
                                    Icon(Icons.Rounded.Close, "Retirer du set")
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick = { picking = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ajouter des morceaux")
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Renommer le set",
            initial = setList?.name.orEmpty(),
            confirmLabel = "Enregistrer",
            onDismiss = { renaming = false },
            onConfirm = { name ->
                setList?.let { vm.renameSetList(it, name) }
                renaming = false
            }
        )
    }

    if (deleting) {
        ConfirmDialog(
            title = "Supprimer ce set ?",
            message = "Les morceaux restent dans votre bibliothèque.",
            confirmLabel = "Supprimer",
            onDismiss = { deleting = false },
            onConfirm = {
                setList?.let { vm.deleteSetList(it) }
                deleting = false
                onBack()
            }
        )
    }

    if (picking) {
        val inSet = entries.map { it.song.id }.toSet()
        PickSongsDialog(
            candidates = songs.filter { it.id !in inSet },
            onDismiss = { picking = false },
            onConfirm = { ids ->
                if (ids.isNotEmpty()) vm.addToSet(setId, ids)
                picking = false
            }
        )
    }
}

@Composable
private fun PickSongsDialog(
    candidates: List<com.cadence.songs.data.Song>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Long>() }
    val shown = remember(candidates, query) {
        val q = query.trim().lowercase()
        candidates.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter des morceaux") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Rechercher") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (shown.isEmpty()) {
                    Text(
                        "Aucun morceau disponible.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(Modifier.height(320.dp)) {
                    items(shown, key = { it.id }) { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = s.id in selected,
                                onCheckedChange = { on ->
                                    if (on) selected.add(s.id) else selected.remove(s.id)
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.title.ifBlank { "Sans titre" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (s.artist.isNotBlank()) {
                                    Text(
                                        s.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text("Ajouter (${selected.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
