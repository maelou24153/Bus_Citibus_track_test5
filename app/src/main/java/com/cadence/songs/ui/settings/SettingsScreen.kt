package com.cadence.songs.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.songs.music.Synth
import com.cadence.songs.server.SyncManager
import com.cadence.songs.ui.AppViewModel
import com.cadence.songs.ui.theme.AccentChoices
import com.cadence.songs.ui.theme.BackgroundChoices
import com.cadence.songs.ui.theme.TextChoices
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cadence.songs.ui.theme.ChordColorNames
import com.cadence.songs.ui.theme.chordPalette
import com.cadence.songs.ui.theme.isDarkTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val dark = isDarkTheme()
    val prefs = remember { context.getSharedPreferences("cadence", android.content.Context.MODE_PRIVATE) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            vm.exportBackup(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "Sauvegarde enregistrée" else "Échec de la sauvegarde",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importBackup(uri) { n ->
                Toast.makeText(
                    context,
                    if (n >= 0) "$n morceau(x) restauré(s)" else "Fichier de sauvegarde invalide",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Réglages",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 4.dp)
        )

        SectionCard("Apparence") {
            val themes = listOf("Auto", "Clair", "Sombre", "Scène")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                themes.forEachIndexed { i, label ->
                    FilterChip(
                        selected = settings.theme == i,
                        onClick = { vm.updateSettings { it.copy(theme = i) } },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                "Le mode Scène est un noir pur très contrasté, pensé pour jouer sur une scène sombre.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorChoiceRow("Couleur de l'application", AccentChoices, settings.accentColor) { c ->
                vm.updateSettings { it.copy(accentColor = c) }
            }
            ColorChoiceRow("Couleur du fond", BackgroundChoices, settings.backgroundColor) { c ->
                vm.updateSettings { it.copy(backgroundColor = c) }
            }
            ColorChoiceRow("Couleur du texte", TextChoices, settings.textColor) { c ->
                vm.updateSettings { it.copy(textColor = c) }
            }
            ColorChoiceRow("Couleur des accords (personnalisée)", AccentChoices, settings.chordColorCustom) { c ->
                vm.updateSettings { it.copy(chordColorCustom = c) }
            }
            Text(
                "« Défaut » revient aux couleurs du thème choisi ci-dessus.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("Affichage des morceaux") {
            Text("Taille du texte : ${settings.fontSize.toInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.fontSize,
                onValueChange = { v -> vm.updateSettings { it.copy(fontSize = v) } },
                valueRange = 14f..44f
            )

            Text("Notation des accords", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.chordStyle == 0,
                    onClick = { vm.updateSettings { it.copy(chordStyle = 0) } },
                    label = { Text("Lettres (C, Am)") }
                )
                FilterChip(
                    selected = settings.chordStyle == 1,
                    onClick = { vm.updateSettings { it.copy(chordStyle = 1) } },
                    label = { Text("Nashville (1, 6m)") }
                )
                FilterChip(
                    selected = settings.chordStyle == 2,
                    onClick = { vm.updateSettings { it.copy(chordStyle = 2) } },
                    label = { Text("Do Ré Mi (Do, Lam)") }
                )
            }

            Text("Colonnes (utile sur tablette)", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { n ->
                    FilterChip(
                        selected = settings.columns == n,
                        onClick = { vm.updateSettings { it.copy(columns = n) } },
                        label = { Text("$n") }
                    )
                }
            }

            Text("Couleur des accords", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                chordPalette(dark).forEachIndexed { i, color ->
                    ColorSwatch(
                        color = color,
                        selected = settings.chordColor == i,
                        description = ChordColorNames[i],
                        onClick = { vm.updateSettings { it.copy(chordColor = i) } }
                    )
                }
            }

            ToggleRow(
                "Afficher les accords",
                settings.showChords
            ) { on -> vm.updateSettings { it.copy(showChords = on) } }
            ToggleRow(
                "Garder l'écran allumé",
                settings.keepScreenOn
            ) { on -> vm.updateSettings { it.copy(keepScreenOn = on) } }
        }

        SectionCard("Serveur 1 · Administration (ordinateur)") {
            val wifi by vm.wifi.state.collectAsState()
            Text(
                "Depuis un ordinateur du même Wi-Fi, avec n'importe quel navigateur : modifier les morceaux, créer des sets, " +
                    "joindre des PDF et des partitions MuseScore, transposer, dupliquer, exporter, télécharger une sauvegarde.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToggleRow("Activer le serveur d'administration", wifi.running) { on ->
                prefs.edit().putBoolean("auto_admin", on).apply()
                if (on) vm.wifi.start() else vm.wifi.stop()
            }
            if (wifi.error != null) {
                Text(wifi.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (wifi.running) {
                Text("Sur l'ordinateur, ouvrez cette adresse :", style = MaterialTheme.typography.bodyMedium)
                if (wifi.addresses.isEmpty()) {
                    Text(
                        "Aucune adresse trouvée : connectez l'appareil à un Wi-Fi.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                wifi.addresses.forEach { url ->
                    Text(url, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text("Puis saisissez le code :", style = MaterialTheme.typography.bodyMedium)
                Text(wifi.pin, style = MaterialTheme.typography.headlineMedium, letterSpacing = 6.sp)
                FilledTonalButton(onClick = { vm.wifi.newPin() }) { Text("Changer le code") }
            }
        }

        SectionCard("Serveur 2 · Lecteur (lecture seule)") {
            val wifi by vm.wifi.state.collectAsState()
            Text(
                "Une page de lecture pour un autre ordinateur ou téléphone du même Wi-Fi : on peut lire les morceaux " +
                    "(accords, solfège, PDF) et les sets, transposer et changer la notation, mais rien ne peut être modifié.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToggleRow("Activer le serveur lecteur", wifi.readerRunning) { on ->
                prefs.edit().putBoolean("auto_reader", on).apply()
                if (on) vm.wifi.startReader() else vm.wifi.stopReader()
            }
            ToggleRow("Demander un code pour lire", wifi.readerUseCode) { on -> vm.wifi.setReaderUseCode(on) }
            if (wifi.readerError != null) {
                Text(wifi.readerError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (wifi.readerRunning) {
                Text("Ouvrez cette adresse sur l'autre appareil :", style = MaterialTheme.typography.bodyMedium)
                if (wifi.readerAddresses.isEmpty()) {
                    Text(
                        "Aucune adresse trouvée : connectez l'appareil à un Wi-Fi.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                wifi.readerAddresses.forEach { url ->
                    Text(url, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (wifi.readerUseCode) {
                    Text("Code : ${wifi.readerPin}", style = MaterialTheme.typography.titleLarge, letterSpacing = 4.sp)
                    FilledTonalButton(onClick = { vm.wifi.newReaderPin() }) { Text("Changer le code") }
                }
            }
        }

        SectionCard("Synchronisation automatique entre appareils") {
            val sync by vm.sync.state.collectAsState()
            var peerText by remember(sync.manualPeer) { mutableStateOf(sync.manualPeer) }
            var codeText by remember(sync.code) { mutableStateOf(sync.code) }
            Text(
                "Sur le même Wi-Fi, vos appareils Cadence se retrouvent seuls et échangent morceaux, partitions, PDF, sets " +
                    "et suppressions : le plus récent l'emporte. Utilisez le même code sur tous les appareils. " +
                    "Les réglages propres à chaque appareil (thème, transposition, vitesse) ne sont pas partagés. " +
                    "Gardez l'appli ouverte pendant la synchronisation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToggleRow("Synchroniser automatiquement", sync.running) { on ->
                prefs.edit().putBoolean("auto_sync", on).apply()
                if (on) vm.sync.start() else vm.sync.stop()
            }
            OutlinedTextField(
                value = codeText,
                onValueChange = { codeText = it.filter { ch -> ch.isLetterOrDigit() }.take(12) },
                label = { Text("Code de synchronisation (identique sur chaque appareil)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { vm.sync.setCode(codeText) },
                    enabled = codeText.length >= 4 && codeText != sync.code
                ) { Text("Enregistrer le code") }
                TextButton(onClick = { vm.sync.newCode() }) { Text("Nouveau code") }
            }
            OutlinedTextField(
                value = peerText,
                onValueChange = { peerText = it },
                label = { Text("Adresse d'un autre appareil (facultatif)") },
                placeholder = { Text("192.168.1.20") },
                supportingText = { Text("À remplir seulement si la recherche automatique ne trouve rien.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(onClick = { vm.sync.setManualPeer(peerText) }, enabled = peerText != sync.manualPeer) {
                Text("Enregistrer l'adresse")
            }
            if (sync.running) {
                val mine = SyncManager.localAddresses().joinToString(", ")
                if (mine.isNotBlank()) {
                    Text("Adresse de cet appareil : $mine", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (sync.peers.isEmpty()) "Aucun autre appareil trouvé pour l'instant." else "Appareils trouvés : " + sync.peers.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (sync.message.isNotBlank()) Text(sync.message, style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = { vm.sync.syncNow() }, enabled = !sync.busy) {
                    Text(if (sync.busy) "Synchronisation…" else "Synchroniser maintenant")
                }
            }
            if (sync.error != null) {
                Text(sync.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard("Partitions MuseScore") {
            Text(
                "Importez un fichier .mscz ou .mscx (menu « + » de la bibliothèque, ou depuis l'ordinateur). " +
                    "Les notes sont affichées en solfège et la partition peut être jouée avec 8 instruments.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Notes en solfège", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.solfegeFixed,
                    onClick = { vm.updateSettings { it.copy(solfegeFixed = true) } },
                    label = { Text("Do fixe") }
                )
                FilterChip(
                    selected = !settings.solfegeFixed,
                    onClick = { vm.updateSettings { it.copy(solfegeFixed = false) } },
                    label = { Text("Do mobile") }
                )
            }
            ToggleRow("Afficher les accords sur la partition", settings.scoreShowChords) { on ->
                vm.updateSettings { it.copy(scoreShowChords = on) }
            }
            Text("Instrument de lecture par défaut", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Synth.INSTRUMENTS.forEachIndexed { i, name ->
                    FilterChip(
                        selected = settings.scoreInstrument == i,
                        onClick = { vm.updateSettings { it.copy(scoreInstrument = i) } },
                        label = { Text(name) }
                    )
                }
            }
        }

        SectionCard("Sauvegarde") {
            Text(
                "Exportez tous vos morceaux et vos sets dans un fichier, ou restaurez-les sur un autre appareil. " +
                    "La restauration ajoute les morceaux sans rien écraser. Les partitions PDF ne sont pas incluses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { exportLauncher.launch("cadence-sauvegarde.json") }) {
                    Icon(Icons.Rounded.Backup, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exporter")
                }
                FilledTonalButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                    Icon(Icons.Rounded.Restore, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Restaurer")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ColorSwatch(color: Color, selected: Boolean, description: String, onClick: () -> Unit) {
    val ring = BorderStroke(3.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(ring, CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = description, tint = Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorChoiceRow(title: String, choices: List<Pair<Int, String>>, selected: Int, onPick: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == 0, onClick = { onPick(0) }, label = { Text("Défaut") })
        choices.forEach { (argb, name) ->
            ColorSwatch(color = Color(argb), selected = selected == argb, description = name, onClick = { onPick(argb) })
        }
    }
}
