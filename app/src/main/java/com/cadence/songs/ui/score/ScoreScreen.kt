package com.cadence.songs.ui.score

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.songs.music.Chords
import com.cadence.songs.music.Score
import com.cadence.songs.music.ScorePlayer
import com.cadence.songs.music.Solfege
import com.cadence.songs.music.SolfegeMeasure
import com.cadence.songs.music.Synth
import com.cadence.songs.ui.AppViewModel
import com.cadence.songs.ui.theme.chordPalette
import com.cadence.songs.ui.theme.isDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lecture d'une partition MuseScore : notes en solfège (Do fixe ou mobile) et lecture avec 8 instruments. */
@Composable
fun ScoreScreen(vm: AppViewModel, songId: Long, onBack: () -> Unit) {
    val song by vm.song(songId).collectAsState(initial = null)
    val s = song
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val score = remember(s.score) { Score.fromJson(s.score) }
    if (score == null) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Cette partition n'a pas pu être lue.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onBack) { Text("Retour") }
        }
        return
    }
    ScoreContent(vm, s.title, score, onBack)
}

private fun guessInstrument(name: String, fallback: Int): Int {
    val n = name.lowercase()
    return when {
        "guitar" in n || "guitare" in n -> 1
        "org" in n -> 2
        "fl" in n && ("ute" in n || "ûte" in n) -> 3
        "violin" in n || "violon" in n || "string" in n || "cordes" in n || "alto" in n || "cello" in n || "violoncelle" in n -> 4
        "marimba" in n || "xylo" in n || "vibra" in n || "glock" in n -> 5
        "bass" in n || "basse" in n -> 6
        "trump" in n || "trompette" in n || "cornet" in n || "brass" in n || "cuivre" in n -> 7
        "piano" in n || "clavier" in n || "keyboard" in n -> 0
        else -> fallback
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScoreContent(vm: AppViewModel, title: String, score: Score, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val dark = isDarkTheme()
    val scope = rememberCoroutineScope()

    var track by remember { mutableIntStateOf(0) }
    var transpose by remember { mutableIntStateOf(0) }
    var tempoScale by remember { mutableFloatStateOf(1f) }
    val programs = remember {
        mutableStateMapOf<Int, Int>().apply {
            score.tracks.forEachIndexed { i, t -> put(i, guessInstrument(t.name, settings.scoreInstrument)) }
        }
    }
    var muted by remember { mutableStateOf(setOf<Int>()) }
    var showInstruments by remember { mutableStateOf(false) }

    val player = remember { ScorePlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var preparing by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var posSec by remember { mutableDoubleStateOf(0.0) }
    var renderedFor by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun stopPlayback() {
        player.stop()
        playing = false
        posSec = 0.0
    }

    fun playKey(): String = "${programs.toSortedMap()}|$muted|${(tempoScale * 20).toInt()}|$transpose"

    fun play(fromSeconds: Double? = null) {
        if (preparing) return
        error = null
        val key = playKey()
        if (renderedFor == key && player.loaded) {
            if (fromSeconds != null) player.seekTo(fromSeconds)
            player.play()
            playing = true
            return
        }
        preparing = true
        val progs = programs.toMap()
        val mutedNow = muted
        val scale = (tempoScale * 20).toInt() / 20.0
        val t = transpose
        scope.launch {
            val ok = withContext(Dispatchers.Default) {
                val pcm = Synth.render(score, progs, mutedNow, scale, t)
                if (pcm == null) false else { player.load(pcm); true }
            }
            preparing = false
            if (!ok) {
                error = "Partition trop longue pour la lecture (7 minutes maximum)."
            } else {
                renderedFor = key
                if (fromSeconds != null) player.seekTo(fromSeconds)
                player.play()
                playing = true
            }
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            posSec = player.positionSeconds()
            if (player.finished()) {
                player.stop()
                playing = false
                posSec = 0.0
            }
            delay(60)
        }
    }

    val measures = remember(score, track, transpose, settings.solfegeFixed) {
        Solfege.measures(score, track, transpose, settings.solfegeFixed)
    }
    val keyName = Solfege.keyName(score.fifths)
    val flats = remember(keyName, transpose) { Chords.useFlats(keyName, transpose) }
    val keyIndex = remember(keyName) { Chords.keyIndex(keyName) ?: 0 }
    val chordStyle = settings.chordStyle
    val mapChord: (String) -> String = remember(transpose, flats, keyIndex, chordStyle) {
        { c: String ->
            when (chordStyle) {
                1 -> Chords.nashville(c, keyIndex)
                2 -> Chords.solfege(c, transpose, flats)
                else -> Chords.transpose(c, transpose, flats)
            }
        }
    }
    val chordColor = if (settings.chordColorCustom != 0) Color(settings.chordColorCustom)
    else chordPalette(dark)[settings.chordColor.coerceIn(0, 4)]
    val activeMeasure = if (playing) score.measureAtBeat(score.secondsToBeat(posSec, (tempoScale * 20).toInt() / 20.0)) else -1
    val shownKey = Chords.transposeKey(keyName, transpose, flats)
    val hasChords = score.harmonies.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(title.ifBlank { "Partition" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Tonalité $shownKey · ${score.beats}/${score.beatType} · ♩ ${score.firstTempo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { stopPlayback(); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            },
            actions = {
                IconButton(onClick = { showInstruments = true }) { Icon(Icons.Rounded.Tune, "Instruments") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        // Réglages de lecture de la partition
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (score.tracks.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    score.tracks.forEachIndexed { i, t ->
                        FilterChip(selected = track == i, onClick = { track = i }, label = { Text(t.name) })
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                if (hasChords) {
                    FilterChip(
                        selected = settings.scoreShowChords,
                        onClick = { vm.updateSettings { it.copy(scoreShowChords = !it.scoreShowChords) } },
                        label = { Text("Accords") }
                    )
                }
                IconButton(onClick = { transpose = (transpose - 1).coerceAtLeast(-11); stopPlayback() }) { Icon(Icons.Rounded.Remove, "Transposition moins") }
                Text(
                    if (transpose == 0) "Original" else (if (transpose > 0) "+$transpose" else "$transpose") + " demi-ton" + (if (kotlin.math.abs(transpose) > 1) "s" else ""),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(120.dp)
                )
                IconButton(onClick = { transpose = (transpose + 1).coerceAtMost(11); stopPlayback() }) { Icon(Icons.Rounded.Add, "Transposition plus") }
            }
            if (!settings.solfegeFixed) {
                Text(
                    "Do mobile : le Do est la tonique du morceau. La transposition ne change alors que le son joué.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val fontSize = (settings.fontSize * 0.85f).coerceIn(14f, 40f)
            FlowRow(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (m in measures) {
                    MeasureCard(
                        m = m,
                        active = m.number - 1 == activeMeasure,
                        fontSize = fontSize,
                        showChords = settings.scoreShowChords && hasChords,
                        mapChord = mapChord,
                        chordColor = chordColor,
                        onClick = {
                            val sec = score.beatToSeconds(m.startBeat, (tempoScale * 20).toInt() / 20.0)
                            if (player.loaded && renderedFor == playKey()) {
                                player.seekTo(sec)
                                if (!playing) { player.play(); playing = true }
                            } else {
                                play(sec)
                            }
                        }
                    )
                }
                Spacer(Modifier.height(120.dp).width(1.dp))
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledIconButton(onClick = {
                        if (playing) { player.pause(); playing = false } else play()
                    }) {
                        if (preparing) {
                            CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Lire")
                        }
                    }
                    IconButton(onClick = { stopPlayback() }) { Icon(Icons.Rounded.Stop, "Arrêter") }
                    Column(Modifier.weight(1f)) {
                        Text("Vitesse ${(tempoScale * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = tempoScale,
                            onValueChange = { tempoScale = it },
                            onValueChangeFinished = { stopPlayback() },
                            valueRange = 0.5f..1.5f
                        )
                    }
                }
                val names = score.tracks.indices.filter { it !in muted }.map { Synth.INSTRUMENTS[programs[it] ?: 0] }.distinct()
                Text(
                    "Instruments : " + names.joinToString(", ") + "  ·  touchez une mesure pour lire à partir de là",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showInstruments) {
        AlertDialog(
            onDismissRequest = { showInstruments = false },
            confirmButton = { TextButton(onClick = { showInstruments = false }) { Text("OK") } },
            title = { Text("Instruments de lecture") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    score.tracks.forEachIndexed { i, t ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(if (i in muted) "Muet" else "Joue", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = i !in muted,
                                    onCheckedChange = { on -> muted = if (on) muted - i else muted + i; stopPlayback() },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Synth.INSTRUMENTS.forEachIndexed { k, name ->
                                    FilterChip(
                                        selected = programs[i] == k,
                                        onClick = { programs[i] = k; stopPlayback() },
                                        label = { Text(name) }
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Les sons sont synthétisés dans l'appli : aucun fichier à télécharger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun MeasureCard(
    m: SolfegeMeasure,
    active: Boolean,
    fontSize: Float,
    showChords: Boolean,
    mapChord: (String) -> String,
    chordColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                m.number.toString(),
                fontSize = (fontSize * 0.55f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (m.cells.isEmpty()) {
                Text("–", fontSize = fontSize.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (c in m.cells) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showChords) {
                        Text(
                            c.chord?.let(mapChord) ?: " ",
                            color = chordColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = (fontSize * 0.8f).sp,
                            maxLines = 1
                        )
                    }
                    Text(
                        c.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize.sp,
                        color = if (c.rest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(c.duration, fontSize = (fontSize * 0.55f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(c.lyric.ifBlank { " " }, fontSize = (fontSize * 0.75f).sp, maxLines = 1)
                }
            }
        }
    }
}
