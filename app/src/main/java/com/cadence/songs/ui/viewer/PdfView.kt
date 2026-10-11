package com.cadence.songs.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Rendu de pages PDF (PdfRenderer n'accepte qu'une page ouverte à la fois : accès sérialisé). */
private class PdfDoc(path: String) {
    private val descriptor = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val mutex = Mutex()
    @Volatile private var closed = false
    val pageCount: Int = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap? = mutex.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.IO) {
            val page = renderer.openPage(index)
            try {
                val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }
    }

    fun close() {
        runBlocking {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    runCatching { renderer.close() }
                    runCatching { descriptor.close() }
                }
            }
        }
    }
}

private val InvertMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

@Composable
fun PdfView(
    path: String,
    invert: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    val doc = remember(path) { runCatching { PdfDoc(path) }.getOrNull() }
    DisposableEffect(doc) { onDispose { doc?.close() } }
    val listState = rememberLazyListState()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    ) {
        if (doc == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Impossible d'ouvrir ce PDF", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(doc.pageCount) { index ->
                    val bitmap by produceState<Bitmap?>(null, index, widthPx) {
                        value = try {
                            doc.render(index, widthPx)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val current = bitmap
                    if (current != null) {
                        Image(
                            bitmap = current.asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            colorFilter = if (invert) ColorFilter.colorMatrix(InvertMatrix) else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.707f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
