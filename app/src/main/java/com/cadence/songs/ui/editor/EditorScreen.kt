package com.cadence.songs.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.songs.data.Song
import com.cadence.songs.music.ChordPro
import com.cadence.songs.music.Chords
import com.cadence.songs.ui.AppViewModel
import com.cadence.songs.ui.common.ConfirmDialog
import java.io.File

private val TimeSignatures = listOf("2/4", "3/4", "4/4", "5/4", "6/8", "7/8", "12/8")

/** Accords « de la gamme » de la tonalité : I, ii, iii, IV, V, vi (une tonalité mineure utilise sa relative majeure). */
private fun diatonicChords(key: String): List<String> {
    val p = Chords.parse(key) ?: return emptyList()
    var root = Chords.index(p.root)
    if (Chords.isMinorKey(key)) root = (root + 3) % 12
    val flats = Chords.useFlats(if (Chords.isMinorKey(key)) Chords.noteName(root, false) else key, 0)
    val steps = listOf(0 to "", 2 to "m", 4 to "m", 5 to "", 7 to "", 9 to "m")
    return steps.map { (offset, suffix) -> Chords.noteName(root + offset, flats) + suffix }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: AppViewModel, songId: Long, onClose: () -> Unit) {
    val context = LocalContext.current

    var loaded by remember { mutableStateOf(songId == 0L) }
    var base by remember { mutableStateOf(Song()) }

    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var tempo by remember { mutableStateOf("") }
    var timeSig by remember { mutableStateOf("4/4") }
    var capo by remember { mutableIntStateOf(0) }
    var tags by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var pdfPath by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf(TextFieldValue("")) }

    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmRemovePdf by remember { mutableStateOf(false) }
    var tapTimes by remember { mutableStateOf(listOf<Long>()) }

    LaunchedEffect(songId) {
        if (songId != 0L) {
            val s = vm.getSong(songId)
            if (s != null) {
                base = s
                title = s.title
                artist = s.artist
                key = s.songKey
                tempo = if (s.tempo > 0) s.tempo.toString() else ""
                timeSig = s.timeSig
                capo = s.capo
                tags = s.tags
                notes = s.notes
                pdfPath = s.pdfPath
                content = TextFieldValue(s.content)
            }
            loaded = true
        }
    }

    fun normTags(t: String) = t.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")

    fun build(): Song {
        val chosenKey = key.trim().ifBlank { ChordPro.firstChord(content.text).orEmpty() }
        return base.copy(
            title = title.trim(),
            artist = artist.trim(),
            songKey = chosenKey,
            tempo = tempo.toIntOrNull()?.coerceIn(0, 400) ?: 0,
            timeSig = timeSig.trim().ifBlank { "4/4" },
            capo = capo,
            tags = normTags(tags),
            notes = notes,
            pdfPath = pdfPath,
            content = content.text
        )
    }

    // « Modifié » = différent de ce qui est enregistré (la tonalité déduite automatiquement ne compte pas).
    val dirty = loaded && build().let { b ->
        b.copy(songKey = if (key.isBlank()) base.songKey else b.songKey) != base.copy(tags = normTags(base.tags))
    }

    fun save() {
        val song = build()
        if (song.title.isBlank() && song.content.isBlank() && song.pdfPath == null) {
            Toast.makeText(context, "Donnez au moins un titre", Toast.LENGTH_SHORT).show()
            return
        }
        vm.saveSong(song.copy(title = song.title.ifBlank { "Sans titre" })) { onClose() }
    }

    fun requestClose() {
        if (dirty) confirmDiscard = true else onClose()
    }

    BackHandler { requestClose() }

    fun insert(snippet: String, cursorOffset: Int = snippet.length) {
        val sel = content.selection
        val newText = content.text.replaceRange(sel.min, sel.max, snippet)
        content = TextFieldValue(newText, TextRange(sel.min + cursorOffset))
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.attachPdf(uri) { path ->
                if (path != null) pdfPath = path
                else Toast.makeText(context, "Impossible d'ajouter ce PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (songId == 0L) "Nouveau morceau" else "Modifier") },
                navigationIcon = {
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = loaded) { Text("Enregistrer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { inner ->
        if (!loaded) {
            Box(Modifier.padding(inner).fillMaxSize())
            return@Scaffold
        }
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artiste") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it.take(8) },
                        label = { Text("Tonalité") },
                        placeholder = { Text("Ex. G, Am") },
                        singleLine = true,
                        isError = key.isNotBlank() && !Chords.isChord(key.trim()),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tempo,
                        onValueChange = { v -> tempo = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Tempo (bpm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        val now = System.currentTimeMillis()
                        val recent = (tapTimes.filter { now - it < 2500 } + now).takeLast(8)
                        tapTimes = recent
                        if (recent.size >= 2) {
                            val avg = (recent.last() - recent.first()).toDouble() / (recent.size - 1)
                            tempo = (60000.0 / avg).toInt().coerceIn(30, 300).toString()
                        }
                    }) {
                        Icon(Icons.Rounded.TouchApp, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Tap tempo")
                    }
                    Text("Mesure", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TimeSignatures.forEach { ts ->
                        FilterChip(
                            selected = timeSig == ts,
                            onClick = { timeSig = ts },
                            label = { Text(ts) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Capo", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { capo = (capo - 1).coerceAtLeast(0) }) {
                        Icon(Icons.Rounded.Remove, "Capo moins")
                    }
                    Text(
                        if (capo == 0) "Aucun" else "Case $capo",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { capo = (capo + 1).coerceAtMost(11) }) {
                        Icon(Icons.Rounded.Add, "Capo plus")
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (séparés par des virgules)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ----- Paroles & accords -----
                Text("Paroles et accords", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Écrivez les accords entre crochets, juste avant la syllabe : [C]Les doigts sur les [G]cordes. " +
                        "Vous pouvez aussi coller un texte avec les accords au-dessus des paroles, puis le convertir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val quick = remember(key, content.text) {
                    val k = key.trim().ifBlank { ChordPro.firstChord(content.text).orEmpty() }
                    diatonicChords(k)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quick.forEach { c ->
                        AssistChip(onClick = { insert("[$c]") }, label = { Text(c) })
                    }
                    AssistChip(onClick = { insert("[]", 1) }, label = { Text("[ ]") })
                    AssistChip(
                        onClick = { insert("{start_of_verse: Couplet}\n\n{end_of_verse}\n", "{start_of_verse: Couplet}\n".length) },
                        label = { Text("Couplet") }
                    )
                    AssistChip(
                        onClick = { insert("{start_of_chorus}\n\n{end_of_chorus}\n", "{start_of_chorus}\n".length) },
                        label = { Text("Refrain") }
                    )
                    AssistChip(onClick = { insert("{comment: }", "{comment: ".length) }, label = { Text("Commentaire") })
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 22.sp),
                    minLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        content = TextFieldValue(ChordPro.convertChordsOverLyrics(content.text))
                        Toast.makeText(context, "Accords convertis au format [C]", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.AutoFixHigh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Convertir (accords au-dessus)")
                    }
                }

                // ----- PDF -----
                Text("Partition PDF", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (pdfPath == null) {
                    OutlinedButton(onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Rounded.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Joindre un PDF")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            File(pdfPath.orEmpty()).name.substringAfter('_'),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                        IconButton(onClick = { confirmRemovePdf = true }) {
                            Icon(Icons.Rounded.Delete, "Retirer le PDF")
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Abandonner les modifications ?",
            message = "Ce que vous avez saisi ne sera pas enregistré.",
            confirmLabel = "Abandonner",
            onDismiss = { confirmDiscard = false },
            onConfirm = { confirmDiscard = false; onClose() }
        )
    }
    if (confirmRemovePdf) {
        ConfirmDialog(
            title = "Retirer le PDF ?",
            message = "Le fichier est détaché du morceau après l'enregistrement.",
            confirmLabel = "Retirer",
            onDismiss = { confirmRemovePdf = false },
            onConfirm = { pdfPath = null; confirmRemovePdf = false }
        )
    }
}
