package com.cadence.songs.ui.library

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cadence.songs.data.Song
import com.cadence.songs.ui.AppViewModel
import com.cadence.songs.ui.common.ConfirmDialog
import com.cadence.songs.ui.common.EmptyState
import com.cadence.songs.ui.common.KeyBadge
import com.cadence.songs.ui.common.NameDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryList(
    vm: AppViewModel,
    selectedId: Long?,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onImportWeb: () -> Unit,
    onDeleted: (Long) -> Unit
) {
    val context = LocalContext.current
    val songs by vm.songs.collectAsState()
    val setLists by vm.setLists.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) } // 0 titre, 1 artiste, 2 récents
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Song?>(null) }
    var toAddToSet by remember { mutableStateOf<Song?>(null) }
    var creatingSetFor by remember { mutableStateOf<Song?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            vm.importUris(uris) { n ->
                val msg = if (n == uris.size) "$n morceau(x) importé(s)" else "$n sur ${uris.size} importé(s)"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val allTags = remember(songs) {
        songs.flatMap { s -> s.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
            .distinct().sortedBy { it.lowercase() }
    }

    val filtered = remember(songs, query, sort, onlyFavorites, tagFilter) {
        val q = query.trim().lowercase()
        val base = songs.filter { s ->
            (!onlyFavorites || s.favorite) &&
                (tagFilter == null || s.tags.split(',').any { it.trim() == tagFilter }) &&
                (q.isEmpty() ||
                    s.title.lowercase().contains(q) ||
                    s.artist.lowercase().contains(q) ||
                    s.tags.lowercase().contains(q) ||
                    (q.length >= 3 && s.content.lowercase().contains(q)))
        }
        when (sort) {
            1 -> base.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            2 -> base.sortedByDescending { it.updatedAt }
            else -> base.sortedBy { it.title.lowercase() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ----- En-tête -----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Morceaux", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${songs.size} chanson${if (songs.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                FilledTonalIconButton(onClick = { addMenu = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Ajouter")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Nouveau morceau") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null) },
                        onClick = { addMenu = false; onNew() }
                    )
                    DropdownMenuItem(
                        text = { Text("Ajouter un PDF") },
                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                        onClick = { addMenu = false; importLauncher.launch(arrayOf("application/pdf")) }
                    )
                    DropdownMenuItem(
                        text = { Text("Importer des fichiers (ChordPro, texte)") },
                        leadingIcon = { Icon(Icons.Rounded.UploadFile, null) },
                        onClick = { addMenu = false; importLauncher.launch(arrayOf("*/*")) }
                    )
                    DropdownMenuItem(
                        text = { Text("Importer une partition MuseScore (.mscz)") },
                        leadingIcon = { Icon(Icons.Rounded.UploadFile, null) },
                        onClick = { addMenu = false; importLauncher.launch(arrayOf("*/*")) }
                    )
                    DropdownMenuItem(
                        text = { Text("Importer depuis un site") },
                        leadingIcon = { Icon(Icons.Rounded.Language, null) },
                        onClick = { addMenu = false; onImportWeb() }
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Titre, artiste, paroles…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Effacer") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sortLabels = listOf("A–Z", "Artiste", "Récents")
            items(sortLabels.size) { i ->
                FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(sortLabels[i]) })
            }
            item {
                FilterChip(
                    selected = onlyFavorites,
                    onClick = { onlyFavorites = !onlyFavorites },
                    label = { Text("Favoris") },
                    leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.width(18.dp)) }
                )
            }
            items(allTags) { tag ->
                FilterChip(
                    selected = tagFilter == tag,
                    onClick = { tagFilter = if (tagFilter == tag) null else tag },
                    label = { Text("#$tag") }
                )
            }
        }

        if (filtered.isEmpty()) {
            if (songs.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "Votre bibliothèque est vide",
                    subtitle = "Créez un morceau ou importez des fichiers ChordPro, texte ou PDF.",
                    action = {
                        FilledTonalButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Rounded.UploadFile, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Importer des fichiers")
                        }
                    }
                )
            } else {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "Aucun résultat",
                    subtitle = "Essayez un autre mot ou retirez les filtres."
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                var lastHeader = ""
                filtered.forEach { s ->
                    if (sort == 0) {
                        val first = s.title.firstOrNull()?.uppercaseChar()
                        val header = if (first != null && first.isLetter()) first.toString() else "#"
                        if (header != lastHeader) {
                            lastHeader = header
                            item(key = "header_$header") {
                                Text(
                                    header,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                                )
                            }
                        }
                    }
                    item(key = s.id) {
                        SongCard(
                            song = s,
                            selected = s.id == selectedId,
                            onClick = { onOpen(s.id) },
                            onFavorite = { vm.toggleFavorite(s) },
                            onEdit = { onEdit(s.id) },
                            onAddToSet = { toAddToSet = s },
                            onShare = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, s.title)
                                    putExtra(Intent.EXTRA_TEXT, vm.exportText(s))
                                }
                                context.startActivity(Intent.createChooser(send, "Partager « ${s.title} »"))
                            },
                            onDelete = { toDelete = s }
                        )
                    }
                }
            }
        }
    }

    toDelete?.let { song ->
        ConfirmDialog(
            title = "Supprimer ce morceau ?",
            message = "« ${song.title.ifBlank { "Sans titre" }} » sera supprimé définitivement, ainsi que de tous les sets.",
            confirmLabel = "Supprimer",
            onDismiss = { toDelete = null },
            onConfirm = {
                vm.deleteSong(song)
                onDeleted(song.id)
                toDelete = null
            }
        )
    }

    toAddToSet?.let { song ->
        AlertDialog(
            onDismissRequest = { toAddToSet = null },
            title = { Text("Ajouter à un set") },
            text = {
                Column {
                    if (setLists.isEmpty()) {
                        Text("Aucun set pour l'instant.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    setLists.forEach { sl ->
                        TextButton(
                            onClick = {
                                vm.addToSet(sl.id, listOf(song.id))
                                Toast.makeText(context, "Ajouté à « ${sl.name} »", Toast.LENGTH_SHORT).show()
                                toAddToSet = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(sl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { creatingSetFor = song; toAddToSet = null }) { Text("Nouveau set") }
            },
            dismissButton = { TextButton(onClick = { toAddToSet = null }) { Text("Annuler") } }
        )
    }

    creatingSetFor?.let { song ->
        NameDialog(
            title = "Nouveau set",
            initial = "",
            confirmLabel = "Créer",
            onDismiss = { creatingSetFor = null },
            onConfirm = { name ->
                vm.createSetList(name) { id -> vm.addToSet(id, listOf(song.id)) }
                creatingSetFor = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongCard(
    song: Song,
    selected: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onAddToSet: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyBadge(song)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    song.title.ifBlank { "Sans titre" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = listOf(
                    song.artist,
                    if (song.tempo > 0) "${song.tempo} bpm" else ""
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (song.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "Favori",
                    tint = if (song.favorite) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Plus") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Modifier") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Ajouter à un set") }, onClick = { menu = false; onAddToSet() })
                    DropdownMenuItem(text = { Text("Partager (ChordPro)") }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text("Supprimer") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
