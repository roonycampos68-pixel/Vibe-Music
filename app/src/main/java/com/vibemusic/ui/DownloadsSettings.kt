package com.vibemusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibemusic.data.Settings
import com.vibemusic.model.*
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@Composable fun DownloadsScreen(vm: MusicViewModel, tracks: List<Track>, downloads: List<DownloadEntry>, playback: PlaybackState, onMore: (Track) -> Unit, onSearch: () -> Unit) {
    val offline = tracks.filter { it.source == "download" }
    val settings by vm.settings.collectAsState()
    val filesPath = LocalContext.current.filesDir.path
    val totalStorage = remember(filesPath) { android.os.StatFs(filesPath).totalBytes.toFloat() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("Descargas", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp));Text("La música se queda contigo.", color = VibeMuted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Surface(color = VibeCard, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.OfflinePin, null, tint = VibeGreen);Spacer(Modifier.width(10.dp));Text("${offline.size} canciones sin conexión", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp));Text("${MusicRules.formatSize(offline.sumOf { it.sizeBytes })} guardados · ${MusicRules.formatSize(tracks.sumOf { it.sizeBytes })} en la biblioteca", color = VibeMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp));LinearProgressIndicator(progress = { (tracks.sumOf { it.sizeBytes }.toFloat() / totalStorage).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text("Modo sin conexión", Modifier.weight(1f));Switch(settings.offlineOnly, vm::setOffline) }
        }
        val pending = downloads.filter { it.state != "completed" }
        if (pending.isNotEmpty()) item { SectionTitle("En curso") }
        items(pending, key = { it.id }) { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(entry.title, fontWeight = FontWeight.Bold)
                Text("${entry.artist} · ${entry.quality}", color = VibeMuted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                if (entry.totalBytes > 0) LinearProgressIndicator(progress = { (entry.downloadedBytes.toFloat() / entry.totalBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) else if (entry.state == "downloading") LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(when (entry.state) { "queued" -> "En espera de red";"downloading" -> "Descargando";"paused" -> "En pausa";else -> "No se pudo descargar" }, color = VibeMuted, fontSize = 12.sp)
                        Text(MusicRules.formatSize(entry.downloadedBytes) + if (entry.totalBytes > 0) " / ${MusicRules.formatSize(entry.totalBytes)}" else "", color = VibeMuted, fontSize = 11.sp)
                    }
                    IconAction(if (entry.state in listOf("queued", "downloading")) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (entry.state in listOf("queued", "downloading")) "Pausar descarga" else "Reanudar descarga", { if (entry.state in listOf("queued", "downloading")) vm.pauseDownload(entry.id) else vm.resumeDownload(entry.id) })
                    IconAction(Icons.Rounded.Close, "Cancelar descarga", { vm.cancelDownload(entry.id) })
                }
                entry.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }
        item { SectionTitle("Disponibles sin internet") }
        items(offline, key = { it.id }) { track -> TrackRow(track, track.id == playback.currentId, { vm.play(offline, offline.indexOf(track)) }, { onMore(track) }) }
        if (offline.isEmpty()) item { EmptyState(Icons.Rounded.DownloadForOffline, "Llévate la música contigo", "Conecta un catálogo autorizado y descarga sus archivos. Tu música importada también funciona sin internet desde Tu biblioteca.", "Explorar catálogos", onSearch) }
    }
}

@Composable fun SettingsScreen(vm: MusicViewModel, settings: Settings, tracks: List<Track>, onBack: () -> Unit, onDownloads: () -> Unit) {
    var catalogDialog by remember { mutableStateOf(false) }
    var catalogUrl by remember(settings.catalogUrl) { mutableStateOf(settings.catalogUrl) }
    var sleepDialog by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    var qualityDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { IconAction(Icons.Rounded.ArrowBack, "Volver", onBack);Text("Ajustes", fontSize = 27.sp, fontWeight = FontWeight.Bold) }
            ListItem(headlineContent = { Text("Mi espacio", fontWeight = FontWeight.Bold) }, supportingContent = { Text("Biblioteca privada en este dispositivo") }, leadingContent = { Icon(Icons.Rounded.AccountCircle, null, Modifier.size(48.dp), tint = VibeGreen) })
            SectionTitle("Escucha a tu manera")
            ListItem(headlineContent = { Text("Modo sin conexión") }, supportingContent = { Text("La biblioteca no necesita internet") }, trailingContent = { Switch(settings.offlineOnly, vm::setOffline) })
            ListItem(headlineContent = { Text("Descargar solo con Wi-Fi") }, supportingContent = { Text("No utiliza tus datos móviles") }, trailingContent = { Switch(settings.wifiOnly, vm::setWifi) })
            ListItem(headlineContent = { Text("Calidad de descarga") }, supportingContent = { Text("${settings.quality} · Según las opciones de la fuente") }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }, modifier = Modifier.clickable { qualityDialog = true })
            ListItem(headlineContent = { Text("Temporizador") }, supportingContent = { Text("Detener la música al dormir") }, leadingContent = { Icon(Icons.Rounded.Bedtime, null) }, modifier = Modifier.clickable { sleepDialog = true })
            ListItem(headlineContent = { Text("Tema oscuro") }, supportingContent = { Text("Siempre activo · Pensado para tu música") }, leadingContent = { Icon(Icons.Rounded.DarkMode, null) })
            SectionTitle("Almacenamiento")
            ListItem(headlineContent = { Text(MusicRules.formatSize(tracks.sumOf { it.sizeBytes })) }, supportingContent = { Text("${tracks.size} archivos en el almacenamiento privado") }, leadingContent = { Icon(Icons.Rounded.Storage, null) })
            ListItem(headlineContent = { Text("Administrar descargas") }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }, modifier = Modifier.clickable(onClick = onDownloads))
            SectionTitle("Catálogos y permisos")
            ListItem(headlineContent = { Text("Fuente de música autorizada") }, supportingContent = { Text(if (settings.catalogUrl.isBlank()) "Sin configurar · La música local ya funciona" else "Catálogo HTTPS configurado") }, leadingContent = { Icon(Icons.Rounded.CloudDownload, null) }, modifier = Modifier.clickable { catalogDialog = true })
            ListItem(headlineContent = { Text("Licencias y atribuciones") }, supportingContent = { Text("Consulta los permisos de tu música") }, modifier = Modifier.clickable { licenses = true })
            SectionTitle("Acerca de Vibe Music")
            Text("Versión 1.0.0\nReproductor independiente para tu propia música. Sin cuenta y sin suscripción.\n\nTus archivos, playlists y favoritos se guardan solo en este dispositivo. Desinstalar la app elimina su almacenamiento privado.\n\nMP3 · M4A · AAC · FLAC · WAV · OGG\nLa compatibilidad depende de los códecs del dispositivo.", color = VibeMuted, fontSize = 13.sp, lineHeight = 21.sp)
        }
    }
    if (catalogDialog) AlertDialog(onDismissRequest = { catalogDialog = false }, title = { Text("Catálogo autorizado") }, text = {
        Column {
            Text("Introduce la URL HTTPS de un manifiesto compatible con Vibe Music. Debe declarar la autorización, licencia, atribución y SHA-256 de cada archivo. Consulta docs/CATALOG.md en el proyecto.", color = VibeMuted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp));OutlinedTextField(catalogUrl, { catalogUrl = it }, label = { Text("Dirección HTTPS") }, singleLine = true)
            Text("Usa únicamente fuentes del titular o un distribuidor con permiso. No añadas claves privadas a la URL.", color = VibeMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }, confirmButton = { TextButton({ vm.setCatalog(catalogUrl);catalogDialog = false }) { Text("Guardar") } }, dismissButton = { TextButton({ catalogDialog = false }) { Text("Cancelar") } })
    if (sleepDialog) AlertDialog(onDismissRequest = { sleepDialog = false }, title = { Text("Temporizador de sueño") }, text = { Column { listOf(0, 15, 30, 45, 60, 90).forEach { minutes -> TextButton({ vm.sleep(minutes);sleepDialog = false }, Modifier.fillMaxWidth()) { Text(if (minutes == 0) "Desactivar" else "$minutes minutos") } } } }, confirmButton = {})
    if (qualityDialog) {
        val catalog by vm.catalog.collectAsState()
        val options = (listOf("Original") + catalog.flatMap { it.qualities.map { q -> q.label } }).distinct()
        AlertDialog(onDismissRequest = { qualityDialog = false }, title = { Text("Calidad preferida") }, text = { Column {
            Text("Se conserva el archivo de la fuente sin convertirlo. Si la calidad elegida no existe, se usa la primera disponible.", color = VibeMuted, fontSize = 12.sp)
            options.forEach { label -> TextButton({ vm.setQuality(label);qualityDialog = false }) { Text(label) } }
        } }, confirmButton = { TextButton({ qualityDialog = false }) { Text("Cerrar") } })
    }
    if (licenses) AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Licencias musicales") }, text = {
        LazyColumn {
            item { Text("Vibe Originals: composiciones y síntesis originales incluidas bajo CC0-1.0. Los archivos importados pertenecen a su titular. Vibe Music no concede derechos sobre ellos.", fontSize = 13.sp, color = VibeMuted) }
            items(tracks.filter { it.license != null }, key = { it.id }) { track ->
                Column(Modifier.padding(top = 16.dp)) {
                    Text(track.title, fontWeight = FontWeight.Bold);Text("${track.license}\n${track.attribution.orEmpty()}", fontSize = 12.sp, color = VibeMuted)
                    track.licenseUrl?.let { url -> TextButton({ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) { Text("Consultar licencia") } }
                }
            }
        }
    }, confirmButton = { TextButton({ licenses = false }) { Text("Cerrar") } })
}
