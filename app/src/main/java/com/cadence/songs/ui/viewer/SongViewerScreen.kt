package com.cadence.songs.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.withFrameNanos
import com.cadence.songs.data.EntryWithSong
import com.cadence.songs.data.Song
import com.cadence.songs.music.ChordPro
import com.cadence.songs.music.Chords
import com.cadence.songs.music.Metronome
import com.cadence.songs.ui.AppViewModel
import com.cadence.songs.ui.LocalOpenScore
import com.cadence.songs.ui.settings.ColorSwatch
import com.cadence.songs.ui.theme.ChordColorNames
import com.cadence.songs.ui.theme.chordPalette
import com.cadence.songs.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Écran de lecture. [setId] > 0 : lecture d'un set (boutons précédent / suivant, pédale de page).
 * [onBack] = null en mode « volet » sur tablette.
 */
@Composable
fun SongViewerScreen(
    vm: AppViewModel,
    songId: Long,
    setId: Long,
    onBack: (() -> Unit)?,
    onEdit: (Long) -> Unit,
    onTogglePanels: (() -> Unit)? = null,
    panelsHidden: Boolean = false
) {
    var currentId by remember(songId) { mutableLongStateOf(songId) }
    val entries by remember(setId) {
        if (setId > 0) vm.entries(setId) else flowOf(emptyList<EntryWithSong>())
    }.collectAsState(initial = emptyList<EntryWithSong>())
    val song by remember(currentId) { vm.song(currentId) }.collectAsState(initial = null)

    val current = song
    if (current == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val index = entries.indexOfFirst { it.song.id == currentId }

    key(current.id) {
        SongViewerContent(
            vm = vm,
            song = current,
            onBack = onBack,
            onEdit = onEdit,
            onTogglePanels = onTogglePanels,
            panelsHidden = panelsHidden,
            position = if (index >= 0) "${index + 1}/${entries.size}" else null,
            hasPrev = index > 0,
            hasNext = index >= 0 && index < entries.size - 1,
            onPrev = { entries.getOrNull(index - 1)?.let { currentId = it.song.id } },
            onNext = { entries.getOrNull(index + 1)?.let { currentId = it.song.id } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongViewerContent(
    vm: AppViewModel,
    song: Song,
    onBack: (() -> Unit)?,
    onEdit: (Long) -> Unit,
    onTogglePanels: (() -> Unit)?,
    panelsHidden: Boolean,
    position: String?,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dark = isDarkTheme()
    val scrollState = rememberScrollState()

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var autoScroll by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(song.scrollSpeed.coerceIn(1, 30).toFloat()) }
    var liveFont by remember { mutableFloatStateOf(settings.fontSize) }
    var showSheet by remember { mutableStateOf(false) }
    var showMetroDialog by remember { mutableStateOf(false) }
    var diagramChord by remember { mutableStateOf<String?>(null) }
    var listenMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var metroOn by remember { mutableStateOf(false) }
    var beatTick by remember { mutableIntStateOf(0) }
    var showPdf by remember { mutableStateOf(song.content.isBlank() && song.pdfPath != null) }
    var viewportPx by remember { mutableIntStateOf(0) }
    val pdfMode = showPdf && song.pdfPath != null

    LaunchedEffect(settings.fontSize) { liveFont = settings.fontSize }

    // ----- Écran allumé -----
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // ----- Métronome -----
    val metronome = remember { Metronome { beatTick++ } }
    DisposableEffect(Unit) { onDispose { metronome.stop() } }
    LaunchedEffect(metroOn, song.tempo, song.timeSig) {
        metronome.bpm = if (song.tempo > 0) song.tempo else 100
        metronome.beats = song.timeSig.substringBefore('/').trim().toIntOrNull()?.coerceIn(1, 12) ?: 4
        if (metroOn) metronome.start() else metronome.stop()
    }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(beatTick) {
        if (metroOn && beatTick > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(260))
        }
    }

    // ----- Défilement automatique -----
    LaunchedEffect(autoScroll, speed) {
        if (!autoScroll) return@LaunchedEffect
        val pxPerSecond = with(density) { (speed * 5f).dp.toPx() }
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            scrollState.scrollBy(pxPerSecond * dt)
            if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue) {
                autoScroll = false
                return@LaunchedEffect
            }
        }
    }

    // ----- Accords affichés (transposition + capo + notation) -----
    val keyForCalc = remember(song.songKey, song.content) {
        song.songKey.ifBlank { ChordPro.firstChord(song.content).orEmpty() }
    }
    val semis = song.transpose - song.capo
    val flatsShapes = remember(keyForCalc, semis, song.content) {
        if (keyForCalc.isNotBlank()) Chords.useFlats(keyForCalc, semis) else ChordPro.contentPrefersFlats(song.content)
    }
    val flatsSound = remember(keyForCalc, song.transpose, song.content) {
        if (keyForCalc.isNotBlank()) Chords.useFlats(keyForCalc, song.transpose) else ChordPro.contentPrefersFlats(song.content)
    }
    val keyIndex = remember(keyForCalc) { Chords.keyIndex(keyForCalc) ?: 0 }
    val chordStyle = settings.chordStyle
    val mapChord: (String) -> String = remember(semis, flatsShapes, keyIndex, chordStyle) {
        { c: String ->
            when (chordStyle) {
                1 -> Chords.nashville(c, keyIndex)
                2 -> Chords.solfege(c, semis, flatsShapes)
                else -> Chords.transpose(c, semis, flatsShapes)
            }
        }
    }
    val soundingKey = Chords.transposeKey(keyForCalc, song.transpose, flatsSound)
    val shapeKey = Chords.transposeKey(keyForCalc, semis, flatsShapes)
    val lines = remember(song.content) { ChordPro.parse(song.content) }
    val chordColor = if (settings.chordColorCustom != 0) Color(settings.chordColorCustom)
    else chordPalette(dark)[settings.chordColor.coerceIn(0, 4)]
    val openScore = LocalOpenScore.current

    fun setTranspose(value: Int) {
        var n = ((value % 12) + 12) % 12
        if (n > 6) n -= 12
        vm.updateSong(song.copy(transpose = n))
    }

    fun page(forward: Boolean) {
        val step = (viewportPx * 0.85f).coerceAtLeast(200f)
        if (forward && scrollState.value >= scrollState.maxValue - 4 && hasNext) {
            onNext()
        } else if (!forward && scrollState.value <= 4 && hasPrev) {
            onPrev()
        } else {
            scope.launch { scrollState.animateScrollBy(if (forward) step else -step) }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || pdfMode) {
                    false
                } else {
                    when (event.key) {
                        Key.PageDown, Key.DirectionRight, Key.DirectionDown, Key.Spacebar -> { page(true); true }
                        Key.PageUp, Key.DirectionLeft, Key.DirectionUp -> { page(false); true }
                        else -> false
                    }
                }
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                song.title.ifBlank { "Sans titre" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (song.artist.isNotBlank()) {
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.toggleFavorite(song) }) {
                            Icon(
                                if (song.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Favori",
                                tint = if (song.favorite) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (song.score.isNotBlank()) {
                            IconButton(onClick = { openScore(song.id) }) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = "Partition en solfège")
                            }
                        }
                        if (song.pdfPath != null && song.content.isNotBlank()) {
                            IconButton(onClick = { showPdf = !showPdf }) {
                                Icon(
                                    if (showPdf) Icons.Rounded.MusicNote else Icons.Rounded.PictureAsPdf,
                                    contentDescription = if (showPdf) "Voir le texte" else "Voir le PDF"
                                )
                            }
                        }
                        if (onTogglePanels != null) {
                            IconButton(onClick = onTogglePanels) {
                                Icon(
                                    if (panelsHidden) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    contentDescription = if (panelsHidden) "Afficher le volet" else "Masquer le volet"
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { listenMenu = true }) {
                                Icon(Icons.Rounded.Headphones, contentDescription = "Écouter la chanson")
                            }
                            DropdownMenu(expanded = listenMenu, onDismissRequest = { listenMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Écouter sur Spotify") },
                                    onClick = {
                                        listenMenu = false
                                        listenOn(context, song.title, song.artist, ListenService.SPOTIFY)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Voir sur YouTube") },
                                    onClick = {
                                        listenMenu = false
                                        listenOn(context, song.title, song.artist, ListenService.YOUTUBE)
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { showSheet = true }) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Affichage")
                        }
                        IconButton(onClick = { onEdit(song.id) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Modifier")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                if (!pdfMode) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (soundingKey.isNotBlank()) InfoChip("Tonalité $soundingKey")
                        if (song.capo > 0) {
                            InfoChip(
                                if (shapeKey.isNotBlank()) "Capo ${song.capo} · formes $shapeKey"
                                else "Capo ${song.capo}"
                            )
                        }
                        InfoChip(
                            if (song.tempo > 0) "♩ ${song.tempo}  ${song.timeSig}" else "Métronome",
                            onClick = { showMetroDialog = true }
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewportPx = it.height }
        ) {
            if (pdfMode) {
                PdfView(
                    path = song.pdfPath.orEmpty(),
                    invert = dark,
                    onTap = { controlsVisible = !controlsVisible }
                )
            } else if (lines.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Ce morceau est vide.",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    if (song.score.isNotBlank()) {
                        FilledTonalButton(onClick = { openScore(song.id) }) { Text("Ouvrir la partition en solfège") }
                        Spacer(Modifier.height(8.dp))
                    }
                    FilledTonalButton(onClick = { onEdit(song.id) }) { Text("Ajouter les paroles et les accords") }
                }
            } else {
                ChartView(
                    lines = lines,
                    fontSize = liveFont,
                    columns = settings.columns,
                    showChords = settings.showChords,
                    chordColor = chordColor,
                    mapChord = mapChord,
                    scrollState = scrollState,
                    onTap = { controlsVisible = !controlsVisible },
                    onChordClick = { raw -> diagramChord = Chords.transpose(raw, semis, flatsShapes) }
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    AnimatedVisibility(visible = autoScroll && !pdfMode) {
                        Row(
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = speed,
                                onValueChange = { speed = it },
                                onValueChangeFinished = { vm.updateSong(song.copy(scrollSpeed = speed.toInt())) },
                                valueRange = 1f..30f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                            )
                            Text(speed.toInt().toString(), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (position != null) {
                            IconButton(onClick = onPrev, enabled = hasPrev) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Morceau précédent")
                            }
                        }
                        if (!pdfMode) {
                            TransposeControl(
                                label = soundingKey.ifBlank {
                                    if (song.transpose == 0) "0" else "%+d".format(song.transpose)
                                },
                                onMinus = { setTranspose(song.transpose - 1) },
                                onPlus = { setTranspose(song.transpose + 1) },
                                onReset = { setTranspose(0) }
                            )
                            FilledIconButton(onClick = { autoScroll = !autoScroll }) {
                                Icon(
                                    if (autoScroll) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (autoScroll) "Arrêter le défilement" else "Défilement automatique"
                                )
                            }
                        }
                        IconButton(onClick = { metroOn = !metroOn }) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = flash.value * 0.45f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Timer,
                                    contentDescription = "Métronome",
                                    tint = if (metroOn) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (position != null) {
                            Text(
                                position,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = onNext, enabled = hasNext) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Morceau suivant")
                            }
                        }
                    }
                }
            }
        }
    }

    diagramChord?.let { chord ->
        ChordDiagramDialog(
            chord = chord,
            instrument = settings.instrument,
            onInstrument = { i -> vm.updateSettings { it.copy(instrument = i) } },
            onDismiss = { diagramChord = null }
        )
    }

    if (showMetroDialog) {
        MetronomeDialog(
            bpm = if (song.tempo > 0) song.tempo else 100,
            running = metroOn,
            onBpm = { vm.updateSong(song.copy(tempo = it.coerceIn(30, 260))) },
            onToggle = { metroOn = !metroOn },
            onDismiss = { showMetroDialog = false }
        )
    }

    if (showSheet) {
        DisplaySheet(
            vm = vm,
            song = song,
            fontSize = liveFont,
            onFontChange = { liveFont = it },
            onFontDone = { vm.updateSettings { s -> s.copy(fontSize = liveFont) } },
            dark = dark,
            onDismiss = { showSheet = false }
        )
    }
}

@Composable
private fun InfoChip(text: String, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    val base = Modifier
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
private fun TransposeControl(label: String, onMinus: () -> Unit, onPlus: () -> Unit, onReset: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, contentDescription = "Transposer d'un demi-ton vers le bas") }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(52.dp)
                .clickable(onClick = onReset)
        )
        IconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, contentDescription = "Transposer d'un demi-ton vers le haut") }
    }
}

@Composable
private fun MetronomeDialog(
    bpm: Int,
    running: Boolean,
    onBpm: (Int) -> Unit,
    onToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Métronome") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("$bpm", style = MaterialTheme.typography.headlineMedium)
                Text("battements par minute", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onBpm(bpm - 5) }) { Text("−5") }
                    TextButton(onClick = { onBpm(bpm - 1) }) { Text("−1") }
                    TextButton(onClick = { onBpm(bpm + 1) }) { Text("+1") }
                    TextButton(onClick = { onBpm(bpm + 5) }) { Text("+5") }
                }
                Slider(
                    value = bpm.toFloat(),
                    onValueChange = { onBpm(it.toInt()) },
                    valueRange = 30f..260f
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onToggle) { Text(if (running) "Arrêter" else "Démarrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplaySheet(
    vm: AppViewModel,
    song: Song,
    fontSize: Float,
    onFontChange: (Float) -> Unit,
    onFontDone: () -> Unit,
    dark: Boolean,
    onDismiss: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Affichage", style = MaterialTheme.typography.titleLarge)

            SheetBlock("Taille du texte · ${fontSize.toInt()}") {
                Slider(
                    value = fontSize,
                    onValueChange = onFontChange,
                    onValueChangeFinished = onFontDone,
                    valueRange = 14f..44f
                )
            }

            SheetBlock("Capo") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.updateSong(song.copy(capo = (song.capo - 1).coerceAtLeast(0))) }) {
                        Icon(Icons.Rounded.Remove, "Capo moins")
                    }
                    Text(
                        if (song.capo == 0) "Sans capo" else "Case ${song.capo}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(110.dp)
                    )
                    IconButton(onClick = { vm.updateSong(song.copy(capo = (song.capo + 1).coerceAtMost(11))) }) {
                        Icon(Icons.Rounded.Add, "Capo plus")
                    }
                }
            }

            SheetBlock("Notation des accords") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.chordStyle == 0,
                        onClick = { vm.updateSettings { it.copy(chordStyle = 0) } },
                        label = { Text("Lettres") }
                    )
                    FilterChip(
                        selected = settings.chordStyle == 1,
                        onClick = { vm.updateSettings { it.copy(chordStyle = 1) } },
                        label = { Text("Nashville") }
                    )
                    FilterChip(
                        selected = settings.chordStyle == 2,
                        onClick = { vm.updateSettings { it.copy(chordStyle = 2) } },
                        label = { Text("Do Ré Mi") }
                    )
                }
            }

            SheetBlock("Colonnes") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { n ->
                        FilterChip(
                            selected = settings.columns == n,
                            onClick = { vm.updateSettings { it.copy(columns = n) } },
                            label = { Text("$n") }
                        )
                    }
                }
            }

            SheetBlock("Couleur des accords") {
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
            }

            SheetBlock("Thème") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "Clair", "Sombre", "Scène").forEachIndexed { i, label ->
                        FilterChip(
                            selected = settings.theme == i,
                            onClick = { vm.updateSettings { it.copy(theme = i) } },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Afficher les accords", Modifier.weight(1f))
                Switch(
                    checked = settings.showChords,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(showChords = on) } }
                )
            }
        }
    }
}

@Composable
private fun SheetBlock(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}
