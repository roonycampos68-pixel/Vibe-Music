package com.vibemusic.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibemusic.model.*

@Composable fun VibeApp(vm: MusicViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val members by vm.membership.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var collectionKind by rememberSaveable { mutableStateOf<String?>(null) }
    var collectionKey by rememberSaveable { mutableStateOf("") }
    var collectionTitle by rememberSaveable { mutableStateOf("") }
    val collection = collectionKind?.let { Collection(it, collectionKey, collectionTitle) }
    fun openCollection(c: Collection) { collectionKind = c.kind;collectionKey = c.key;collectionTitle = c.title;settingsOpen = false }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf<Track?>(null) }
    var addTo by remember { mutableStateOf<Track?>(null) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var createPlaylist by rememberSaveable { mutableStateOf(false) }
    var renamePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deleteTrack by remember { mutableStateOf<Track?>(null) }
    var coverFor by remember { mutableStateOf<Playlist?>(null) }
    var addSongs by remember { mutableStateOf<Playlist?>(null) }
    val snack = remember { SnackbarHostState() }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.import(uris) }
    val cover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) coverFor?.let { vm.playlistArt(it, uri) };coverFor = null }
    val importMusic = { import.launch(arrayOf("audio/*")) }
    val newPlaylist = { playlistName = "";createPlaylist = true }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    BackHandler(settingsOpen || collection != null) { settingsOpen = false;collectionKind = null }
    Scaffold(containerColor = VibeBackground, snackbarHost = { SnackbarHost(snack) }, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = {
        Column(Modifier.navigationBarsPadding()) {
            tracks.find { it.id == playback.currentId }?.let { MiniPlayer(it, playback, vm) { playerOpen = true } }
            NavigationBar(containerColor = VibeBackground, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                val labels = listOf("Inicio", "Buscar", "Tu biblioteca", "Descargas")
                val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.LibraryMusic, Icons.Rounded.DownloadForOffline)
                labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index && !settingsOpen && collection == null, onClick = { tab = index;settingsOpen = false;collectionKind = null }, icon = { Icon(icons[index], null) }, label = { Text(label, maxLines = 1, fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedIconColor = Color.White, unselectedIconColor = VibeMuted, selectedTextColor = Color.White, unselectedTextColor = VibeMuted)) }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                settingsOpen -> SettingsScreen(vm, settings, tracks, { settingsOpen = false }, { settingsOpen = false;tab = 3 })
                collection != null -> CollectionScreen(collection, tracks, playlists, members, vm, playback, { collectionKind = null }, { options = it }, { renamePlaylist = it;playlistName = it.name }, { coverFor = it;cover.launch(arrayOf("image/*")) }, { deletePlaylist = it }, { addSongs = it })
                else -> Crossfade(tab, label = "Navegación") { page -> when (page) {
                    0 -> HomeScreen(tracks, playlists, playback, busy, importMusic, vm::addOriginals, { settingsOpen = true }, ::openCollection, { list, index -> vm.play(list, index) }, { options = it })
                    1 -> SearchScreen(vm, tracks, playlists, playback, ::openCollection, { options = it }, { settingsOpen = true })
                    2 -> LibraryScreen(tracks, playlists, busy, importMusic, newPlaylist, ::openCollection)
                    3 -> DownloadsScreen(vm, tracks, downloads, playback, { options = it }, { tab = 1 })
                } }
            }
        }
    }
    tracks.find { it.id == playback.currentId }?.let { current -> if (playerOpen) PlayerScreen(current, playback, tracks, vm, { playerOpen = false }, { options = it }, { playerOpen = false;openCollection(Collection("album", current.album, current.album)) }) }
    options?.let { selected ->
        val track = tracks.find { it.id == selected.id } ?: selected
        AlertDialog(onDismissRequest = { options = null }, title = { Text(track.title) }, text = {
            Column {
                Text("${track.artist}\n${MusicRules.formatSize(track.sizeBytes)} · Disponible sin conexión", color = VibeMuted)
                TextButton({ vm.favorite(track);options = null }, Modifier.fillMaxWidth()) { Icon(if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null);Spacer(Modifier.width(10.dp));Text(if (track.favorite) "Quitar de favoritas" else "Añadir a favoritas") }
                TextButton({ addTo = track;options = null }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.PlaylistAdd, null);Spacer(Modifier.width(10.dp));Text("Añadir a playlist") }
                TextButton({ playerOpen = false;options = null;openCollection(Collection("album", track.album, track.album)) }, Modifier.fillMaxWidth()) { Text("Ir al álbum") }
                if (track.license != null) Text("${track.license} · ${track.attribution.orEmpty()}", fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp), color = VibeMuted)
                TextButton({ deleteTrack = track;options = null }, Modifier.fillMaxWidth()) { Text("Eliminar del dispositivo", color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton({ options = null }) { Text("Cerrar") } })
    }
    if (createPlaylist || renamePlaylist != null) AlertDialog(onDismissRequest = { createPlaylist = false;renamePlaylist = null }, title = { Text(if (renamePlaylist == null) "Crear playlist" else "Renombrar playlist") }, text = { OutlinedTextField(playlistName, { playlistName = it.take(100) }, label = { Text("Nombre de la playlist") }, singleLine = true) }, confirmButton = { TextButton({ val p = renamePlaylist;if (p == null) vm.createPlaylist(playlistName) else vm.rename(p, playlistName);createPlaylist = false;renamePlaylist = null }, enabled = playlistName.isNotBlank()) { Text("Guardar") } }, dismissButton = { TextButton({ createPlaylist = false;renamePlaylist = null }) { Text("Cancelar") } })
    addTo?.let { track -> AlertDialog(onDismissRequest = { addTo = null }, title = { Text("Añadir a playlist") }, text = {
        LazyColumn { items(playlists, key = { it.id }) { playlist -> ListItem(headlineContent = { Text(playlist.name) }, leadingContent = { Icon(Icons.Rounded.QueueMusic, null) }, modifier = Modifier.clickable { vm.addToPlaylist(playlist, track);addTo = null }) };if (playlists.isEmpty()) item { Text("Crea tu primera playlist en Tu biblioteca.", color = VibeMuted) } }
    }, confirmButton = { TextButton({ addTo = null;newPlaylist() }) { Text("Nueva playlist") } }, dismissButton = { TextButton({ addTo = null }) { Text("Cerrar") } }) }
    addSongs?.let { playlist ->
        val existing = members.filter { it.playlistId == playlist.id }.map { it.trackId }.toSet()
        AlertDialog(onDismissRequest = { addSongs = null }, title = { Text("Añadir canciones") }, text = {
            LazyColumn { items(tracks, key = { it.id }) { track -> ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text(track.artist) }, trailingContent = { Icon(if (track.id in existing) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline, null, tint = if (track.id in existing) VibeGreen else VibeMuted) }, modifier = Modifier.clickable(enabled = track.id !in existing) { vm.addToPlaylist(playlist, track) }) };if (tracks.isEmpty()) item { Text("Importa música primero desde Tu biblioteca.") } }
        }, confirmButton = { TextButton({ addSongs = null }) { Text("Listo") } })
    }
    deletePlaylist?.let { playlist -> AlertDialog(onDismissRequest = { deletePlaylist = null }, title = { Text("¿Eliminar ${playlist.name}?") }, text = { Text("Las canciones permanecerán en tu biblioteca.") }, confirmButton = { TextButton({ vm.deletePlaylist(playlist);deletePlaylist = null;collectionKind = null }) { Text("Eliminar") } }, dismissButton = { TextButton({ deletePlaylist = null }) { Text("Cancelar") } }) }
    deleteTrack?.let { track -> AlertDialog(onDismissRequest = { deleteTrack = null }, title = { Text("¿Eliminar ${track.title}?") }, text = { Text("Se borrará la copia privada y se quitará de tus playlists. El archivo original de tu teléfono permanece intacto.") }, confirmButton = { TextButton({ vm.deleteTrack(track);deleteTrack = null }) { Text("Eliminar") } }, dismissButton = { TextButton({ deleteTrack = null }) { Text("Cancelar") } }) }
}
