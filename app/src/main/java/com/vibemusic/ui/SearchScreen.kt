package com.vibemusic.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibemusic.model.*
import kotlinx.coroutines.delay

@Composable fun SearchScreen(vm: MusicViewModel, tracks: List<Track>, playlists: List<Playlist>, playback: PlaybackState, onCollection: (Collection) -> Unit, onMore: (Track) -> Unit, onSettings: () -> Unit, onImportMusic: () -> Unit = {}, onImportSpotify: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("Todo") }
    var kind by rememberSaveable { mutableStateOf("Canciones") }
    val settings by vm.settings.collectAsState()
    val catalog by vm.catalog.collectAsState()
    val loading by vm.catalogLoading.collectAsState()
    val error by vm.catalogError.collectAsState()
    val next by vm.nextOffset.collectAsState()
    val searchIntent by vm.searchIntent.collectAsState()
    val requests by vm.playlistRequests.collectAsState()
    val target = requests.find { it.id == searchIntent?.requestId }
    var chooseQuality by remember { mutableStateOf<CatalogTrack?>(null) }
    val context = LocalContext.current
    val results = remember(tracks, query) { tracks.filter { MusicRules.matches(it, query) } }
    LaunchedEffect(searchIntent?.nonce) { searchIntent?.let { query = it.query;source = "Todo" } }
    LaunchedEffect(query, source, settings.offlineOnly) {
        vm.cancelCatalogSearch()
        if (source != "Mi música") { delay(600);vm.loadCatalog(query) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buscar", Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                IconAction(Icons.Rounded.PlaylistAdd, "Importar playlist de Spotify", onImportSpotify)
                IconAction(Icons.Rounded.Settings, "Fuentes de música", onSettings)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Canción, artista o álbum") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconAction(Icons.Rounded.Close, "Borrar búsqueda", { query = "" }) }, singleLine = true, shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.rememberSearch(query);if (source != "Mi música") vm.loadCatalog(query) }), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = VibeCard, focusedContainerColor = VibeCard))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Todo", "Mi música", "En línea").forEach { label -> Pill(label, source == label) { source = label } } }
            if (target != null) Surface(color = VibeCard, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Elige el audio que corresponde a ${target.title}. Se vinculará a tu playlist.", Modifier.weight(1f), fontSize = 12.sp)
                    IconAction(Icons.Rounded.Close, "Cancelar vinculación", vm::clearSearchTarget)
                }
            }
            if (source != "En línea") {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("Canciones", "Artistas", "Álbumes", "Playlists").forEach { label -> Pill(label, kind == label) { kind = label } } }
            }
        }
        if (source != "En línea") {
            if (query.isBlank() && settings.recentSearches.isNotEmpty()) {
                item { SectionTitle("Búsquedas recientes") }
                items(settings.recentSearches) { term -> ListItem(headlineContent = { Text(term) }, leadingContent = { Icon(Icons.Rounded.History, null) }, modifier = Modifier.clickable { query = term }) }
            }
            item { SectionTitle("En tu biblioteca") }
            when (kind) {
                "Canciones" -> items(results, key = { "local-${it.id}" }) { track -> TrackRow(track, track.id == playback.currentId, { if (target != null) vm.linkRequest(target, track) else { vm.rememberSearch(query);vm.play(results, results.indexOf(track)) } }, { onMore(track) }) }
                "Artistas", "Álbumes" -> {
                    val groups = if (kind == "Artistas") results.groupBy { it.artist } else results.groupBy { it.album }
                    items(groups.entries.toList(), key = { "group-${it.key}" }) { (name, group) -> ListItem(headlineContent = { Text(name) }, supportingContent = { Text("${group.size} canciones · Sin conexión") }, leadingContent = { Art(group.first(), Modifier.size(54.dp)) }, modifier = Modifier.clickable { onCollection(Collection(if (kind == "Artistas") "artist" else "album", name, name)) }) }
                }
                "Playlists" -> items(playlists.filter { MusicMatching.matchesQuery(query, it.name) }, key = { "playlist-${it.id}" }) { p -> ListItem(headlineContent = { Text(p.name) }, leadingContent = { Icon(Icons.Rounded.QueueMusic, null) }, modifier = Modifier.clickable { onCollection(Collection("playlist", p.id, p.name)) }) }
            }
            if ((kind != "Playlists" && results.isEmpty()) || (kind == "Playlists" && playlists.none { MusicMatching.matchesQuery(query, it.name) })) item {
                if (source == "Mi música") EmptyState(Icons.Rounded.LibraryMusic, "Todavía no tienes esa música", "Esta pestaña busca en tus archivos. En Todo o En línea puedes buscar audio público; también puedes importar tu música.", "Importar archivos", onImportMusic)
                else Text("Sin coincidencias locales. También estamos buscando en las fuentes disponibles.", color = VibeMuted, fontSize = 13.sp)
            }
        }
        if (source != "Mi música") {
            item {
                SectionTitle(if (query.isBlank()) "Descubre audio para guardar" else "Audio disponible para guardar")
                Text("Buscamos en Wikimedia Commons y en tu fuente adicional. Solo se ofrecen descargas con una licencia compatible. El catálogo no incluye todas las canciones comerciales de Spotify.", color = VibeMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null) Surface(color = VibeCard, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Column(Modifier.padding(12.dp)) { Text(error!!, fontSize = 12.sp);TextButton({ vm.loadCatalog(query) }) { Text("Reintentar búsqueda") } }
                }
            }
            items(catalog, key = { "online-${it.id}" }) { track ->
                val saved = tracks.find { local -> local.sourceUrl == track.qualities.first().url || (track.qualities.first().hashAlgorithm == "SHA-256" && local.contentHash == track.qualities.first().sha256) }
                val action = { if (saved != null && target == null) vm.play(listOf(saved)) else chooseQuality = track }
                ListItem(headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text("${track.artist}\n${if (track.assetPath != null) "Incluido en Vibe" else "${track.album} · ${track.license}"}" + if (saved != null) " · Guardado" else "", maxLines = 3, fontSize = 12.sp) }, leadingContent = { Art(modifier = Modifier.size(52.dp), label = track.title) }, trailingContent = { IconAction(if (saved != null && target == null) Icons.Rounded.PlayArrow else Icons.Rounded.Download, if (saved != null) "Escuchar ${track.title}" else "Guardar ${track.title}", action, VibeGreen) }, modifier = Modifier.clickable(onClick = action))
            }
            if (next != null && !loading) item { TextButton({ vm.loadCatalog(query, append = true) }, Modifier.fillMaxWidth()) { Text("Mostrar más resultados") } }
            if (!loading && catalog.isEmpty()) item {
                EmptyState(Icons.Rounded.SearchOff, "No encontramos audio descargable", "Prueba otro título o artista. Si la grabación no tiene una fuente con permiso de descarga, puedes importar un archivo propio y vincularlo a tu playlist.", "Importar música", onImportMusic)
            }
        }
    }
    chooseQuality?.let { track -> AlertDialog(onDismissRequest = { chooseQuality = null }, title = { Text(track.title) }, text = {
        Column {
            Text(track.attribution, color = VibeMuted, fontSize = 12.sp)
            Text(track.license, color = VibeGreen, fontSize = 12.sp)
            if (target != null) Text("Se usará esta grabación para «${target.title}» de ${target.artist}.", fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            track.sourcePage?.let { page -> TextButton({ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page))) }.onFailure { vm.messages.tryEmit("No se pudo abrir el navegador") } }) { Text("Consultar la fuente y sus permisos") } }
            Spacer(Modifier.height(12.dp))
            track.qualities.forEach { quality -> TextButton({ vm.rememberSearch(query);vm.download(track, quality, target?.id);chooseQuality = null }, Modifier.fillMaxWidth(), enabled = track.assetPath != null || !settings.offlineOnly) { Icon(Icons.Rounded.Download, null);Spacer(Modifier.width(8.dp));Text((if (target != null) "Vincular · " else "Guardar · ") + quality.label + if (quality.sizeBytes > 0) " · ${MusicRules.formatSize(quality.sizeBytes)}" else "") } }
        }
    }, confirmButton = { TextButton({ chooseQuality = null }) { Text("Cerrar") } }) }
}
