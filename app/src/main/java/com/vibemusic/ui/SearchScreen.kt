package com.vibemusic.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibemusic.model.*

@Composable fun SearchScreen(vm: MusicViewModel, tracks: List<Track>, playlists: List<Playlist>, playback: PlaybackState, onCollection: (Collection) -> Unit, onMore: (Track) -> Unit, onSettings: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("Mi música") }
    var kind by rememberSaveable { mutableStateOf("Canciones") }
    val settings by vm.settings.collectAsState()
    val catalog by vm.catalog.collectAsState()
    val loading by vm.catalogLoading.collectAsState()
    val error by vm.catalogError.collectAsState()
    var chooseQuality by remember { mutableStateOf<CatalogTrack?>(null) }
    val results = remember(tracks, query) { tracks.filter { MusicRules.matches(it, query) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("Buscar", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("¿Qué quieres escuchar?") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconAction(Icons.Rounded.Close, "Borrar búsqueda", { query = "" }) }, singleLine = true, shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.rememberSearch(query) }), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = VibeCard, focusedContainerColor = VibeCard))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Mi música", "Catálogo legal").forEach { label -> Pill(label, source == label) { source = label;if (label == "Catálogo legal" && catalog.isEmpty()) vm.loadCatalog() } }
            }
            Spacer(Modifier.height(14.dp))
            if (source == "Mi música") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Canciones", "Artistas", "Álbumes", "Playlists").forEach { label -> Pill(label, kind == label) { kind = label } }
            }
        }
        if (source == "Mi música") {
            if (query.isBlank() && settings.recentSearches.isNotEmpty()) {
                item { SectionTitle("Búsquedas recientes") }
                items(settings.recentSearches) { term -> ListItem(headlineContent = { Text(term) }, leadingContent = { Icon(Icons.Rounded.History, null) }, modifier = Modifier.clickable { query = term }) }
            }
            item { SectionTitle(if (query.isBlank()) "Explora tu biblioteca" else "Resultados") }
            when (kind) {
                "Canciones" -> items(results, key = { it.id }) { track -> TrackRow(track, track.id == playback.currentId, { vm.rememberSearch(query);vm.play(results, results.indexOf(track)) }, { onMore(track) }) }
                "Artistas", "Álbumes" -> {
                    val groups = if (kind == "Artistas") results.groupBy { it.artist } else results.groupBy { it.album }
                    items(groups.entries.toList(), key = { it.key }) { (name, group) ->
                        ListItem(headlineContent = { Text(name) }, supportingContent = { Text("${group.size} canciones · Sin conexión") }, leadingContent = { Art(group.first(), Modifier.size(54.dp)) }, modifier = Modifier.clickable { vm.rememberSearch(query);onCollection(Collection(if (kind == "Artistas") "artist" else "album", name, name)) })
                    }
                }
                "Playlists" -> items(playlists.filter { it.name.contains(query, true) }, key = { it.id }) { p -> ListItem(headlineContent = { Text(p.name) }, leadingContent = { Icon(Icons.Rounded.QueueMusic, null) }, modifier = Modifier.clickable { vm.rememberSearch(query);onCollection(Collection("playlist", p.id, p.name)) }) }
            }
            if ((kind != "Playlists" && results.isEmpty()) || (kind == "Playlists" && playlists.none { it.name.contains(query, true) })) item { EmptyState(Icons.Rounded.Search, "Todavía no hay resultados", "Prueba otro nombre o importa música desde Tu biblioteca.") }
        } else {
            item {
                SectionTitle("Música con permiso para quedarse")
                Text("Solo archivos con descarga autorizada, licencia pública y verificación de integridad. La escucha empieza cuando la descarga termina.", color = VibeMuted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null) EmptyState(Icons.Rounded.CloudOff, "No pudimos abrir el catálogo", error!!, if (settings.catalogUrl.isBlank()) "Configurar catálogo" else "Reintentar", { if (settings.catalogUrl.isBlank()) onSettings() else vm.loadCatalog() })
                if (!loading && error == null && catalog.isEmpty()) EmptyState(Icons.Rounded.LibraryMusic, "Conecta una fuente autorizada", "Añade la dirección de un catálogo compatible en Ajustes. Tu biblioteca local funciona sin configurar nada.", "Abrir ajustes", onSettings)
            }
            val filtered = catalog.filter { listOf(it.title, it.artist, it.album).any { field -> field.contains(query, true) } }
            items(filtered, key = { it.id }) { track ->
                ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text("${track.artist} · ${track.license}") }, leadingContent = { Box(Modifier.size(50.dp).background(VibeCard), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = VibeGreen) } }, trailingContent = { IconAction(Icons.Rounded.Download, "Descargar ${track.title}", { chooseQuality = track }, VibeGreen) }, modifier = Modifier.clickable { chooseQuality = track })
            }
            if (catalog.isNotEmpty()) item {
                SectionTitle("Descargar un álbum")
                catalog.groupBy { it.album }.forEach { (name, songs) -> ListItem(headlineContent = { Text(name) }, supportingContent = { Text("${songs.size} archivos autorizados") }, trailingContent = { IconAction(Icons.Rounded.DownloadForOffline, "Descargar álbum $name", { vm.downloadAlbum(songs) }) }) }
            }
        }
    }
    chooseQuality?.let { track ->
        AlertDialog(onDismissRequest = { chooseQuality = null }, title = { Text(track.title) }, text = {
            Column {
                Text(track.attribution, color = VibeMuted, fontSize = 12.sp)
                Text(track.license, color = VibeGreen, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                track.qualities.forEach { quality ->
                    TextButton({ vm.download(track, quality);chooseQuality = null }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Download, null);Spacer(Modifier.width(10.dp));Text(quality.label + if (quality.sizeBytes > 0) " · ${MusicRules.formatSize(quality.sizeBytes)}" else "") }
                }
            }
        }, confirmButton = { TextButton({ chooseQuality = null }) { Text("Cerrar") } })
    }
}
