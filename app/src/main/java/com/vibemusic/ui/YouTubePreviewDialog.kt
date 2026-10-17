package com.vibemusic.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vibemusic.data.YouTubeSearch
import com.vibemusic.data.YouTubeTrack
import com.vibemusic.model.MusicRules
import kotlinx.coroutines.delay

data class YouTubePreviewRequest(val track: YouTubeTrack? = null, val query: String? = null, val requestId: String? = null)

@Composable
fun YouTubePreviewDialog(request: YouTubePreviewRequest, vm: MusicViewModel, onDismiss: () -> Unit, onDownloads: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tracks by vm.tracks.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val settings by vm.settings.collectAsState()
    var selected by remember { mutableStateOf(request.track) }
    var page by remember { mutableStateOf(request.track == null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val webResult = remember(context) { runCatching { WebView(context) } }
    val session = remember(webResult) {
        webResult.getOrNull()?.let { web -> YouTubeWebSession(web, { selected = it }, { loading = it }, { error = it }, { page = true }).apply { configure() } }
    }
    val webHeight = (LocalConfiguration.current.screenHeightDp.dp * if (page) .58f else .38f).coerceIn(220.dp, 520.dp)
    LaunchedEffect(session) {
        vm.pauseForExternalPlayback()
        session?.let { if (request.track != null) it.preview(request.track) else it.showPage(YouTubeSearch.url(request.query.orEmpty()).toString()) }
    }
    LaunchedEffect(settings.offlineOnly) { if (settings.offlineOnly) onDismiss() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(loading) {
        if (loading) {
            delay(25_000)
            if (loading) { loading = false;error = "YouTube está tardando en responder. Puedes reintentar o probar su página aquí." }
        }
    }
    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> session?.resume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> session?.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session?.pause()
        onDispose { lifecycle.removeObserver(observer);session?.release() }
    }
    Dialog(onDismissRequest = { if (session?.back() != true) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = VibeBackground) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (page) "YouTube dentro de Vibe" else "Vista previa de YouTube", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    IconAction(Icons.Rounded.Close, "Cerrar vista de YouTube", onDismiss)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    selected?.let { track ->
                        Text(track.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text(track.artist, color = VibeMuted, fontSize = 13.sp)
                    }
                    if (session != null) {
                        AndroidView(factory = { session.view }, modifier = Modifier.fillMaxWidth().height(webHeight))
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton({ session.reload() }) { Text("Reintentar") }
                            if (!page && selected != null) TextButton({ session.showPage(selected!!.url) }) { Text("Ver página aquí") }
                        }
                        Text(if (page) "Abre un vídeo en esta página para descargar su audio. La selección se actualiza al cambiar de vídeo." else "Pulsa ▶ en el vídeo para escucharlo. El reproductor y sus anuncios pertenecen a YouTube.", color = VibeMuted, fontSize = 12.sp)
                    } else Text("No se pudo iniciar el reproductor. Instala o actualiza Android System WebView. Puedes seguir usando la descarga a MP3.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Text("Elige una canción y pulsa Descargar MP3. Vibe usa el vídeo seleccionado automáticamente y guarda el audio para escucharlo sin conexión ni anuncios. Descarga audio que tengas permiso para guardar.", color = VibeMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val current = selected
                    val task = current?.let { video -> downloads.find { it.id == video.downloadId } }
                    val saved = current?.let { video -> tracks.find { it.id == task?.savedTrackId || it.id == video.downloadId || it.sourceUrl == video.url } }
                    val active = task?.state in listOf("queued", "downloading")
                    Button({
                        if (saved != null && request.requestId == null) { session?.pause();onDismiss();vm.play(listOf(saved)) }
                        else current?.let { vm.downloadYouTube(it, request.requestId) }
                    }, Modifier.fillMaxWidth(), enabled = current != null && (!settings.offlineOnly || saved != null) && (!active || request.requestId != null)) {
                        Icon(if (saved != null) Icons.Rounded.OfflinePin else Icons.Rounded.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text(when {
                            saved != null -> if (request.requestId != null) "Vincular MP3 guardado" else "Escuchar sin conexión"
                            active -> "Descarga en curso"
                            else -> "Descargar MP3"
                        })
                    }
                    if (task != null && saved == null) {
                        val percent = task.progressPercent?.takeIf { it.isFinite() && task.state == "downloading" }
                        val status = when (task.state) {
                            "paused" -> "En pausa. Pulsa Descargar MP3 para continuar"
                            "queued" -> task.error ?: "En cola"
                            "failed" -> task.error ?: "La descarga ha fallado. Puedes reintentar"
                            else -> task.error ?: "Preparando MP3"
                        }
                        Text((percent?.let { "${it.coerceIn(0f, 100f).toInt()} % · " } ?: "") + status, fontSize = 12.sp, color = if (task.state == "failed") MaterialTheme.colorScheme.error else VibeMuted, maxLines = 3)
                        TextButton({ onDismiss();onDownloads() }, Modifier.fillMaxWidth()) { Text("Ver descargas") }
                    }
                    if (current == null) Text("Selecciona una canción en YouTube para habilitar «Descargar MP3».", color = VibeMuted, fontSize = 13.sp)
                    if (saved != null) Text("MP3 guardado · ${MusicRules.formatSize(saved.sizeBytes)}", color = VibeGreen, fontSize = 12.sp)
                }
                SnackbarHost(snackbar)
            }
        }
    }
}
