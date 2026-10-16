package com.vibemusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibemusic.model.*

@Composable fun LibraryScreen(tracks: List<Track>, playlists: List<Playlist>, busy: Boolean, onImport: () -> Unit, onCreate: () -> Unit, onCollection: (Collection) -> Unit, onSpotify: () -> Unit = {}, downloadedIds: Set<String> = emptySet()) {
    var category by rememberSaveable { mutableStateOf("Todo") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Tu biblioteca", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                IconAction(Icons.Rounded.CreateNewFolder, "Crear playlist", onCreate)
            }
            Text("Tu colección. Siempre contigo.", color = VibeMuted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Button(onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null);Spacer(Modifier.width(8.dp));Text(if (busy) "Importando archivos…" else "Importar música del teléfono") }
            OutlinedButton(onSpotify, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Icon(Icons.Rounded.PlaylistAdd, null);Spacer(Modifier.width(8.dp));Text("Importar playlist de Spotify") }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Todo", "Playlists", "Álbumes", "Artistas").forEach { Pill(it, category == it) { category = it } } }
            Spacer(Modifier.height(14.dp))
        }
        if (category == "Todo") {
            items(listOf(
                Triple("favorite", "Tus favoritas", tracks.count { it.favorite }), Triple("all", "Todas las canciones", tracks.size), Triple("download", "Descargadas", tracks.count { it.source == "download" || it.id in downloadedIds }), Triple("import", "Archivos importados", tracks.count { it.source == "import" })
            )) { (kind, title, count) ->
                ListItem(headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("$count canciones · Disponibles sin conexión") }, leadingContent = { Icon(if (kind == "favorite") Icons.Rounded.Favorite else if (kind == "download") Icons.Rounded.DownloadForOffline else Icons.Rounded.LibraryMusic, null, Modifier.size(40.dp), tint = VibeGreen) }, modifier = Modifier.clickable { onCollection(Collection(kind, title = title)) })
            }
        }
        if (category in listOf("Todo", "Playlists")) {
            item { SectionTitle("Tus playlists", "Crear", onCreate) }
            items(playlists, key = { it.id }) { p -> ListItem(headlineContent = { Text(p.name, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("Playlist · Creada por ti") }, leadingContent = { Art(modifier = Modifier.size(58.dp), label = p.name, path = p.artworkPath) }, modifier = Modifier.clickable { onCollection(Collection("playlist", p.id, p.name)) }) }
            if (playlists.isEmpty()) item { EmptyState(Icons.Rounded.QueueMusic, "Dale un lugar a cada momento", "Crea una playlist y reúne tus canciones favoritas.", "Crear playlist", onCreate) }
        }
        if (category in listOf("Álbumes", "Artistas")) {
            val albums = category == "Álbumes"
            val groups = tracks.groupBy { if (albums) it.album else it.artist }
            items(groups.entries.toList(), key = { it.key }) { (name, songs) ->
                ListItem(headlineContent = { Text(name) }, supportingContent = { Text("${songs.size} canciones") }, leadingContent = { Art(songs.first(), Modifier.size(58.dp)) }, modifier = Modifier.clickable { onCollection(Collection(if (albums) "album" else "artist", name, name)) })
            }
            if (groups.isEmpty()) item { EmptyState(Icons.Rounded.Album, "Tu colección empieza con una canción", "Los álbumes y artistas aparecen al importar tu música.") }
        }
    }
}

private data class CollectionRow(val track: Track?, val request: PlaylistRequest?, val position: Int)

@Composable fun CollectionScreen(collection: Collection, tracks: List<Track>, playlists: List<Playlist>, membership: List<PlaylistTrack>, vm: MusicViewModel, playback: PlaybackState, onBack: () -> Unit, onMore: (Track) -> Unit, onRename: (Playlist) -> Unit, onCover: (Playlist) -> Unit, onDelete: (Playlist) -> Unit, onAddSongs: (Playlist) -> Unit, onSearchRequest: (PlaylistRequest) -> Unit = {}, onExport: (Playlist) -> Unit = {}, onDownloads: () -> Unit = {}) {
    val playlist = playlists.find { it.id == collection.key }.takeIf { collection.kind == "playlist" }
    val allRequests by vm.playlistRequests.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val resolveProgress by vm.resolveProgress.collectAsState()
    val requests = allRequests.filter { it.playlistId == playlist?.id }
    val pending = requests.filter { it.trackId == null }
    val source = collection.select(tracks, membership, downloads.filter { it.state == "completed" }.mapNotNull { it.savedTrackId }.toSet())
    var sort by rememberSaveable(collection.key, collection.kind) { mutableStateOf("Original") }
    var showSort by remember { mutableStateOf(false) }
    var linkLocal by remember { mutableStateOf<PlaylistRequest?>(null) }
    val rows = source.mapIndexed { index, track -> CollectionRow(track, null, membership.find { it.playlistId == playlist?.id && it.trackId == track.id }?.position ?: index) } + pending.map { CollectionRow(null, it, it.position) }
    val ordered = when (sort) {
        "Nombre" -> rows.sortedBy { MusicMatching.normalize(it.track?.title ?: it.request!!.title) }
        "Artista" -> rows.sortedBy { MusicMatching.normalize(it.track?.artist ?: it.request!!.artist) }
        "Añadidas" -> rows.sortedByDescending { it.track?.addedAt ?: 0L }
        "Recientes" -> rows.sortedByDescending { it.track?.lastPlayedAt ?: 0L }
        else -> rows.sortedBy { it.position }
    }
    val songs = ordered.mapNotNull { it.track }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.Rounded.ArrowBack, "Volver", onBack);Spacer(Modifier.weight(1f))
                if (playlist != null) {
                    IconAction(Icons.Rounded.IosShare, "Exportar lista como CSV", { onExport(playlist) })
                    IconAction(Icons.Rounded.Image, "Cambiar portada", { onCover(playlist) })
                    IconAction(Icons.Rounded.Edit, "Renombrar playlist", { onRename(playlist) })
                    IconAction(Icons.Rounded.DeleteOutline, "Eliminar playlist", { onDelete(playlist) })
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 15.dp), contentAlignment = Alignment.Center) { Art(songs.firstOrNull(), Modifier.size(205.dp), label = playlist?.name ?: collection.title, path = playlist?.artworkPath ?: songs.firstOrNull()?.artworkPath) }
            Text(playlist?.name ?: collection.title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("${songs.size} disponibles" + if (pending.isNotEmpty()) " · ${pending.size} pendientes" else " · ${MusicRules.formatTime(songs.sumOf { it.durationMs })}", color = VibeMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton({ showSort = true }) { Icon(Icons.Rounded.Sort, null, Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(sort, color = VibeMuted, fontSize = 12.sp) }
                    DropdownMenu(showSort, { showSort = false }) { listOf("Original", "Nombre", "Artista", "Añadidas", "Recientes").forEach { label -> DropdownMenuItem(text = { Text(label) }, onClick = { sort = label;showSort = false }) } }
                }
                if (playlist != null) IconAction(Icons.Rounded.Add, "Añadir canciones a playlist", { onAddSongs(playlist) })
                Spacer(Modifier.weight(1f))
                IconAction(Icons.Rounded.Shuffle, "Reproducir aleatoriamente", { vm.play(songs, shuffle = true) }, enabled = songs.isNotEmpty())
                FilledIconButton({ vm.play(songs) }, Modifier.size(56.dp), enabled = songs.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, "Reproducir colección", Modifier.size(32.dp)) }
            }
            if (playlist != null && pending.isNotEmpty()) {
                Text("Los títulos importados no contienen audio. Busca una fuente autorizada o vincula tus propios archivos. Solo las canciones disponibles se reproducen sin conexión.", fontSize = 12.sp, color = VibeMuted)
                if (resolveProgress == null) TextButton({ vm.resolvePlaylist(playlist) }) { Icon(Icons.Rounded.DownloadForOffline, null);Spacer(Modifier.width(8.dp));Text("Buscar y descargar coincidencias") }
            }
            if (resolveProgress != null) {
                Text(resolveProgress!!, fontSize = 12.sp, color = VibeGreen)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                TextButton(vm::cancelResolve) { Text("Detener búsqueda") }
            }
        }
        items(ordered, key = { it.track?.let { t -> "track-${t.id}" } ?: "pending-${it.request!!.id}" }) { row ->
            val track = row.track
            if (track != null) Column {
                TrackRow(track, track.id == playback.currentId, { vm.play(songs, songs.indexOf(track)) }, { onMore(track) })
                if (playlist != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconAction(Icons.Rounded.ArrowUpward, "Subir ${track.title}", { vm.reorder(playlist, track, -1) }, VibeMuted, enabled = source.indexOf(track) > 0 && sort == "Original")
                    IconAction(Icons.Rounded.ArrowDownward, "Bajar ${track.title}", { vm.reorder(playlist, track, 1) }, VibeMuted, enabled = source.indexOf(track) < source.lastIndex && sort == "Original")
                    IconAction(Icons.Rounded.PlaylistRemove, "Quitar ${track.title} de la playlist", { vm.removeFromPlaylist(playlist, track) }, VibeMuted)
                }
            } else {
                val request = row.request!!;val download = downloads.find { it.id == request.downloadId }
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text("${request.position + 1}. ${request.title}", fontWeight = FontWeight.SemiBold)
                    Text(request.artist, color = VibeMuted, fontSize = 12.sp)
                    Text(when (download?.state) { "downloading", "queued" -> "Descarga en curso o esperando red"; "paused" -> "Descarga en pausa"; "failed" -> "Descarga fallida: puedes reintentar"; else -> "Pendiente de conseguir un archivo de audio" }, color = VibeMuted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        if (download?.state in listOf("queued", "downloading")) IconAction(Icons.Rounded.Download, "Ver descargas", onDownloads, VibeGreen)
                        else if (download?.state in listOf("paused", "failed")) IconAction(Icons.Rounded.Refresh, "Reanudar descarga", { vm.resumeDownload(download!!.id) }, VibeGreen)
                        IconAction(Icons.Rounded.Search, "Buscar audio para ${request.title}", { onSearchRequest(request) })
                        IconAction(Icons.Rounded.Link, "Vincular archivo de mi biblioteca", { linkLocal = request })
                        IconAction(Icons.Rounded.Close, "Quitar título pendiente", { vm.deleteRequest(request) }, VibeMuted)
                    }
                }
            }
        }
        if (ordered.isEmpty()) item { EmptyState(Icons.Rounded.MusicNote, "Hay espacio para tus favoritas", if (playlist != null) "Pulsa + para añadir canciones de tu biblioteca." else "Importa música o guarda audio desde Buscar.") }
    }
    linkLocal?.let { request -> AlertDialog(onDismissRequest = { linkLocal = null }, title = { Text("Vincular ${request.title}") }, text = {
        LazyColumn { items(tracks, key = { it.id }) { track -> ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text(track.artist) }, modifier = Modifier.clickable { vm.linkRequest(request, track);linkLocal = null }) };if (tracks.isEmpty()) item { Text("Importa primero un archivo de audio desde Tu biblioteca.") } }
    }, confirmButton = { TextButton({ linkLocal = null }) { Text("Cerrar") } }) }
}
