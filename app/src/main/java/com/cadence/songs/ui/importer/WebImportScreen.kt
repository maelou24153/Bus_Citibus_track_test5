package com.cadence.songs.ui.importer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cadence.songs.data.Song
import com.cadence.songs.music.WebImport
import com.cadence.songs.server.Json
import com.cadence.songs.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GRAB_SCRIPT =
    "(function(){var s='';try{s=String(window.getSelection())}catch(e){}" +
        "return JSON.stringify({html:document.documentElement.outerHTML,sel:s,title:document.title});})()"

private fun toUrl(input: String): String {
    val t = input.trim()
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    if (!t.contains(' ') && t.contains('.')) return "https://$t"
    return "https://www.google.com/search?q=" + Uri.encode("$t accords")
}

/**
 * Navigateur intégré pour ajouter une chanson depuis un site d'accords (Ultimate Guitar,
 * La Boîte à chansons…). On ouvre la page de la chanson, puis « Importer la page » ou, si la page
 * est inhabituelle, on sélectionne le texte à la main et on appuie sur « Importer la sélection ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebImportScreen(vm: AppViewModel, onClose: () -> Unit, onImported: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    fun go(text: String) {
        if (text.isBlank()) return
        webView?.loadUrl(toUrl(text))
    }

    fun grab(selectionOnly: Boolean) {
        val w = webView ?: return
        busy = true
        w.evaluateJavascript(GRAB_SCRIPT) { raw ->
            scope.launch {
                val song = withContext(Dispatchers.Default) {
                    runCatching {
                        val inner = Json.parse(raw ?: "null") as? String ?: return@runCatching null
                        val o = Json.parseObject(inner)
                        val sel = (o["sel"] as? String).orEmpty()
                        if (selectionOnly && sel.isBlank()) return@runCatching null
                        WebImport.extract(
                            html = if (selectionOnly) "" else (o["html"] as? String).orEmpty(),
                            selection = sel,
                            pageTitle = (o["title"] as? String).orEmpty()
                        )
                    }.getOrNull()
                }
                busy = false
                if (song == null) {
                    Toast.makeText(
                        context,
                        if (selectionOnly) "Sélectionnez d'abord le texte de la chanson sur la page (appui long)."
                        else "Chanson introuvable sur cette page. Sélectionnez le texte (appui long) puis « Importer la sélection ».",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    vm.saveSong(
                        Song(
                            title = song.title, artist = song.artist, songKey = song.key, capo = song.capo,
                            tempo = song.tempo, timeSig = song.timeSig, content = song.content
                        )
                    ) { id ->
                        Toast.makeText(context, "« ${song.title} » ajouté", Toast.LENGTH_SHORT).show()
                        onImported(id)
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fermer") }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                placeholder = { Text("Adresse ou titre de chanson") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go(address) }),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { go(address) }) { Icon(Icons.Rounded.Search, "Aller") }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = { go("https://www.ultimate-guitar.com/") }, label = { Text("Ultimate Guitar") })
            AssistChip(onClick = { go("https://www.laboiteachansons.com/") }, label = { Text("La Boîte à chansons") })
        }
        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            if (url != null) address = url
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                        }
                    }
                    loadUrl("https://www.ultimate-guitar.com/")
                    webView = this
                }
            },
            onRelease = { it.destroy() }
        )

        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Ouvrez la page d'une chanson, puis importez-la. Pour une page inhabituelle : appui long sur le texte, " +
                    "sélectionnez toute la chanson, puis « Importer la sélection ».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { grab(false) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Importer la page")
                }
                FilledTonalButton(onClick = { grab(true) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Importer la sélection")
                }
            }
        }
    }
}
