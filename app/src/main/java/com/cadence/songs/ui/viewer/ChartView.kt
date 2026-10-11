package com.cadence.songs.ui.viewer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.songs.music.Line
import com.cadence.songs.music.Seg

/**
 * Affiche un morceau : accords au-dessus des paroles, sections, refrains soulignés d'une barre,
 * 1 à 3 colonnes, défilement vertical partagé (donc compatible avec le défilement automatique).
 */
@Composable
fun ChartView(
    lines: List<Line>,
    fontSize: Float,
    columns: Int,
    showChords: Boolean,
    chordColor: Color,
    mapChord: (String) -> String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onChordClick: (String) -> Unit = {}
) {
    val chunks = remember(lines, columns) { splitColumns(lines, columns) }
    Row(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        chunks.forEach { chunk ->
            Column(Modifier.weight(1f)) {
                chunk.forEach { line ->
                    LineView(line, fontSize, showChords, chordColor, mapChord, onChordClick)
                }
                Spacer(Modifier.height(160.dp))
            }
        }
    }
}

private fun splitColumns(lines: List<Line>, n: Int): List<List<Line>> {
    if (n <= 1 || lines.size < 8) return listOf(lines)
    val target = lines.size / n
    val result = mutableListOf<List<Line>>()
    var start = 0
    for (k in 1 until n) {
        var end = (start + target).coerceAtMost(lines.size)
        var probe = end
        val limit = (end + 6).coerceAtMost(lines.size)
        while (probe < limit && lines[probe] !is Line.Blank) probe++
        if (probe < limit) end = probe + 1
        result.add(lines.subList(start, end))
        start = end
    }
    result.add(lines.subList(start, lines.size))
    return result
}

@Composable
private fun LineView(
    line: Line,
    fontSize: Float,
    showChords: Boolean,
    chordColor: Color,
    mapChord: (String) -> String,
    onChordClick: (String) -> Unit
) {
    when (line) {
        is Line.Blank -> Spacer(Modifier.height((fontSize * 0.6f).dp))
        is Line.Section -> SectionPill(line.label)
        is Line.Comment -> Text(
            line.text,
            fontStyle = FontStyle.Italic,
            fontSize = (fontSize * 0.85f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        is Line.Tab -> Text(
            line.text.replace(' ', ' '),
            fontFamily = FontFamily.Monospace,
            fontSize = (fontSize * 0.72f).sp,
            softWrap = false,
            overflow = TextOverflow.Clip,
            color = MaterialTheme.colorScheme.onBackground
        )
        is Line.Lyric -> LyricLine(line, fontSize, showChords, chordColor, mapChord, onChordClick)
    }
}

@Composable
private fun SectionPill(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/** Regroupe les segments en « mots » insécables pour que les retours à la ligne se fassent entre les mots. */
private fun tokenize(segs: List<Seg>): List<List<Seg>> {
    val tokens = mutableListOf<List<Seg>>()
    var current = mutableListOf<Seg>()
    for (seg in segs) {
        val pieces = if (seg.text.isEmpty()) {
            listOf("")
        } else {
            seg.text.split(Regex("(?<= )")).filter { it.isNotEmpty() }
        }
        pieces.forEachIndexed { i, piece ->
            current.add(Seg(if (i == 0) seg.chord else null, piece))
            if (piece.endsWith(" ")) {
                tokens.add(current)
                current = mutableListOf()
            }
        }
    }
    if (current.isNotEmpty()) tokens.add(current)
    return tokens
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLine(
    line: Line.Lyric,
    fontSize: Float,
    showChords: Boolean,
    chordColor: Color,
    mapChord: (String) -> String,
    onChordClick: (String) -> Unit
) {
    val tokens = remember(line) { tokenize(line.segs) }
    val hasChords = showChords && line.segs.any { it.chord != null }
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val textColor = MaterialTheme.colorScheme.onBackground

    val rowModifier = if (line.chorus) {
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = barColor,
                    size = Size(3.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
            .padding(start = 14.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    Box(rowModifier.padding(vertical = 2.dp)) {
        FlowRow(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            tokens.forEach { token ->
                Row {
                    token.forEach { seg ->
                        Column {
                            if (hasChords) {
                                Text(
                                    text = seg.chord?.let { mapChord(it).replace(' ', ' ') } ?: " ",
                                    color = chordColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (fontSize * 0.88f).sp,
                                    maxLines = 1,
                                    modifier = if (seg.chord != null) {
                                        Modifier
                                            .clickable { onChordClick(seg.chord) }
                                            .padding(end = 8.dp)
                                    } else Modifier
                                )
                            }
                            Text(
                                text = seg.text.replace(' ', ' '),
                                color = textColor,
                                fontSize = fontSize.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
