package com.vibemusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibemusic.model.*
import java.time.LocalTime

/** A collection is derived from the durable library, never a fabricated remote playlist. */
data class Collection(val kind: String, val key: String = "", val title: String)
fun Collection.select(tracks: List<Track>, members: List<PlaylistTrack>): List<Track> = when (kind) {
    "favorite" -> tracks.filter { it.favorite }
    "album" -> tracks.filter { it.album == key }
    "artist" -> tracks.filter { it.artist == key }
    "playlist" -> members.filter { it.playlistId == key }.sortedBy { it.position }.mapNotNull { m -> tracks.find { it.id == m.trackId } }
    "recent" -> tracks.filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }
    "import" -> tracks.filter { it.source == "import" }
    "download" -> tracks.filter { it.source == "download" }
    "original" -> tracks.filter { it.source == "original" }
    else -> tracks
}

@Composable fun HomeScreen(tracks: List<Track>, playlists: List<Playlist>, playback: PlaybackState, busy: Boolean, onImport: () -> Unit, onOriginals: () -> Unit, onSettings: () -> Unit, onCollection: (Collection) -> Unit, onPlay: (List<Track>, Int) -> Unit, onMore: (Track) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("Todo") }
    LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF20342D), VibeBackground), endY = 520f)), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(VibeGreen.copy(alpha = .18f)).clickable(onClick = onSettings), contentAlignment = Alignment.Center) { Text("v", color = VibeGreen, fontSize = 29.sp, fontWeight = FontWeight.Black) }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text("VIBE MUSIC", fontSize = 10.sp, letterSpacing = 2.sp, color = VibeMuted, fontWeight = FontWeight.SemiBold)
                    Text(when (LocalTime.now().hour) { in 5..11 -> "Buenos días";in 12..18 -> "Buenas tardes";else -> "Buenas noches" }, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                }
                IconAction(Icons.Rounded.Settings, "Abrir ajustes y perfil", onSettings)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Todo", "Música", "Playlists", "Descargas").forEach { Pill(it, filter == it) { filter = it } } }
            Spacer(Modifier.height(22.dp))
        }
        if (filter in listOf("Todo", "Playlists")) {
            item {
                val quick = listOf(Collection("favorite", title = "Tus favoritas"), Collection("all", title = "En este dispositivo")) + playlists.take(4).map { Collection("playlist", it.id, it.name) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { collection ->
                                Surface(onClick = { onCollection(collection) }, color = Color.White.copy(alpha = .075f), shape = RoundedCornerShape(5.dp), modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(52.dp).background(Brush.linearGradient(if (collection.kind == "favorite") listOf(Color(0xFF4531A7), Color(0xFFB1C5B8)) else listOf(Color(0xFF295A4A), Color(0xFF0D2821)))), contentAlignment = Alignment.Center) {
                                            Icon(if (collection.kind == "favorite") Icons.Rounded.Favorite else Icons.Rounded.LibraryMusic, null, Modifier.size(24.dp))
                                        }
                                        Text(collection.title, Modifier.padding(horizontal = 9.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (tracks.isEmpty() && filter != "Playlists") {
            item {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF29453C), Color(0xFF1A2320)))).padding(22.dp)) {
                    Column {
                        Text("HECHA PARA ESCUCHAR", color = VibeGreen, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp));Text("Tu música.\nA tu manera.", fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 39.sp, letterSpacing = (-1).sp)
                        Spacer(Modifier.height(12.dp));Text("Todos tus archivos. Sin conexión.\nSin suscripciones.", color = VibeMuted, fontSize = 14.sp, lineHeight = 21.sp)
                        Spacer(Modifier.height(22.dp));Button(onImport, enabled = !busy, shape = CircleShape) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Importar mi música", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                SectionTitle("Encuentra tu primera vibra")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C1C)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art(modifier = Modifier.size(100.dp), label = "Vibe Originals", color = Color(0xFF514280))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("Vibe Originals", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("3 composiciones originales\nIncluidas · Licencia CC0", fontSize = 12.sp, color = VibeMuted, lineHeight = 19.sp)
                        TextButton(onOriginals, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("Añadir a mi biblioteca", fontSize = 12.sp) }
                    }
                }
            }
        } else {
            val recent = tracks.filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }.take(8)
            if (filter in listOf("Todo", "Música")) {
                if (recent.isNotEmpty()) item {
                    SectionTitle("Continúa escuchando", "Ver todo", { onCollection(Collection("recent", title = "Escuchado recientemente")) })
                    AlbumRow(recent, { track -> onPlay(recent, recent.indexOf(track)) })
                }
                if (tracks.any { it.favorite }) item {
                    SectionTitle("Tus canciones favoritas", "Ver todo", { onCollection(Collection("favorite", title = "Tus favoritas")) })
                    AlbumRow(tracks.filter { it.favorite }.take(8), { track -> onPlay(tracks.filter { it.favorite }, tracks.filter { it.favorite }.indexOf(track)) })
                }
                item {
                    SectionTitle("Álbumes guardados")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        items(tracks.distinctBy { it.album }) { track ->
                            Column(Modifier.width(148.dp).clickable { onCollection(Collection("album", track.album, track.album)) }) {
                                Art(track, Modifier.size(148.dp));Spacer(Modifier.height(9.dp));Text(track.album, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis);Text(track.artist, color = VibeMuted, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (filter in listOf("Todo", "Descargas", "Música")) {
                val offline = if (filter == "Descargas") tracks.filter { it.source == "download" } else tracks
                item { SectionTitle(if (filter == "Descargas") "Música descargada" else "Siempre contigo", "Ver todo", { onCollection(Collection(if (filter == "Descargas") "download" else "all", title = "Disponible sin conexión")) }) }
                items(offline.take(5), key = { it.id }) { track -> TrackRow(track, track.id == playback.currentId, { onPlay(offline, offline.indexOf(track)) }, { onMore(track) }) }
                if (offline.isEmpty()) item { EmptyState(Icons.Rounded.Download, "Tu música, disponible siempre", "Importa archivos o descarga desde un catálogo autorizado en Buscar.") }
            }
        }
        if (filter == "Playlists" && playlists.isEmpty()) item { EmptyState(Icons.Rounded.QueueMusic, "Tu próxima playlist empieza aquí", "Crea tu primera playlist desde Tu biblioteca y añade tus canciones.") }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 20.dp), color = VibeGreen) }
    }
}
@Composable private fun AlbumRow(tracks: List<Track>, onPlay: (Track) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        items(tracks, key = { it.id }) { track ->
            Column(Modifier.width(148.dp).clickable { onPlay(track) }) {
                Art(track, Modifier.size(148.dp));Spacer(Modifier.height(9.dp));Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis);Text(track.artist, fontSize = 12.sp, color = VibeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
