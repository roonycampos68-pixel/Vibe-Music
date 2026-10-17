package com.vibemusic.ui

import android.media.AudioManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import com.vibemusic.model.*

@Composable fun MiniPlayer(track: Track, state: PlaybackState, vm: MusicViewModel, onOpen: () -> Unit) {
    val online = track.source == "youtube-online"
    var seek by remember(track.id) { mutableStateOf<Float?>(null) }
    val duration = (state.duration.takeIf { it > 0 } ?: track.durationMs).coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 8.dp).clip(MaterialTheme.shapes.small).background(Color(track.color).copy(alpha = 1f)).clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 8.dp, top = 7.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(track, Modifier.size(42.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (state.loading) "Conectando…" else state.error ?: track.artist, fontSize = 11.sp, color = Color.White.copy(alpha = .85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (online) IconAction(Icons.Rounded.Download, "Descargar canción actual", vm::downloadPlayingYouTube)
            else IconAction(if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Marcar favorita", { vm.favorite(track) }, if (track.favorite) VibeGreen else Color.White)
            IconAction(if (state.loading) Icons.Rounded.Close else if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.loading) "Cancelar conexión" else if (state.playing) "Pausar" else "Reproducir", vm::togglePlay)
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), color = VibeGreen)
        else Slider(seek ?: state.position.toFloat().coerceIn(0f, duration.toFloat()), { seek = it },
            onValueChangeFinished = { seek?.let { vm.seek(it.toLong()) };seek = null }, valueRange = 0f..duration.toFloat(),
            enabled = state.error == null, modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(MusicRules.formatTime(state.position), color = Color.White, fontSize = 10.sp)
            Text(MusicRules.formatTime(duration), color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable fun PlayerScreen(track: Track, state: PlaybackState, tracks: List<Track>, vm: MusicViewModel, onDismiss: () -> Unit, onMore: (Track) -> Unit, onAlbum: () -> Unit) {
    val online = track.source == "youtube-online"
    var showQueue by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    var seek by remember(track.id) { mutableStateOf<Float?>(null) }
    val duration = state.duration.takeIf { it > 0 } ?: track.durationMs
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = VibeBackground, contentColor = Color.White) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(playerBackgroundColors(track.color)))) {
            Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 27.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(Icons.Rounded.KeyboardArrowDown, "Minimizar reproductor", onDismiss)
                    Column(Modifier.weight(1f).clickable(enabled = !online, onClick = onAlbum), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (online) "REPRODUCIENDO DESDE YOUTUBE" else "REPRODUCIENDO DESDE TU BIBLIOTECA", fontSize = 8.sp, letterSpacing = 1.sp, maxLines = 1)
                        Text(track.album, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (online) IconAction(Icons.Rounded.Download, "Descargar canción actual", vm::downloadPlayingYouTube) else IconAction(Icons.Rounded.MoreVert, "Opciones de la canción", { onMore(track) })
                }
                Spacer(Modifier.height(22.dp))
                Art(track, Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(track.id) {
                    detectVerticalDragGestures(onDragStart = { drag = 0f }, onVerticalDrag = { _, amount -> drag += amount }, onDragEnd = { if (drag > 100) onDismiss() })
                })
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = VibeMuted, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!online) IconAction(if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Añadir a favoritos", { vm.favorite(track) }, if (track.favorite) VibeGreen else Color.White)
                }
                Spacer(Modifier.height(12.dp))
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = Color.White) }
                Slider(value = seek ?: state.position.toFloat().coerceIn(0f, duration.toFloat()), onValueChange = { seek = it }, onValueChangeFinished = { seek?.let { vm.seek(it.toLong()) };seek = null }, valueRange = 0f..duration.toFloat().coerceAtLeast(1f), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = .2f)), modifier = Modifier.fillMaxWidth().height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(MusicRules.formatTime(seek?.toLong() ?: state.position), color = VibeMuted, fontSize = 11.sp);Text(MusicRules.formatTime(duration), color = VibeMuted, fontSize = 11.sp) }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconAction(Icons.Rounded.Shuffle, "Alternar reproducción aleatoria", vm::shuffle, if (state.shuffle) VibeGreen else VibeMuted)
                    IconAction(Icons.Rounded.SkipPrevious, "Canción anterior", vm::previous, modifier = Modifier.size(50.dp))
                    FilledIconButton(vm::togglePlay, Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pausar" else "Reproducir", Modifier.size(43.dp)) }
                    IconAction(Icons.Rounded.SkipNext, "Siguiente canción", vm::next, enabled = state.canNext, modifier = Modifier.size(50.dp))
                    IconAction(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Cambiar repetición", vm::repeat, if (state.repeat == Player.REPEAT_MODE_OFF) VibeMuted else VibeGreen)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(Icons.Rounded.VolumeUp, "Controlar volumen", { showVolume = true }, VibeMuted)
                    Text("SALIDA DE ANDROID", fontSize = 9.sp, letterSpacing = 1.sp, color = VibeGreen, modifier = Modifier.weight(1f))
                    if (!online) Icon(Icons.Rounded.CheckCircle, "Guardada sin conexión", Modifier.size(16.dp), tint = VibeGreen)
                    IconAction(Icons.Rounded.QueueMusic, "Ver cola de reproducción", { showQueue = true })
                }
                Text(if (online) "REPRODUCCIÓN EN LÍNEA" else "DISPONIBLE SIN CONEXIÓN", fontSize = 9.sp, letterSpacing = 1.5.sp, color = VibeMuted, modifier = Modifier.padding(top = 10.dp, bottom = 20.dp).align(Alignment.CenterHorizontally))
            }
        }
        }
    }
    if (showQueue) AlertDialog(onDismissRequest = { showQueue = false }, title = { Text("A continuación") }, text = {
        LazyColumn { itemsIndexed(state.queue) { index, id -> tracks.find { it.id == id }?.let { item ->
            ListItem(headlineContent = { Text(item.title, color = if (id == track.id) VibeGreen else Color.White) }, supportingContent = { Text(item.artist) }, leadingContent = { Art(item, Modifier.size(40.dp)) }, modifier = Modifier.clickable { vm.queueJump(index) })
        } } }
    }, confirmButton = { TextButton({ showQueue = false }) { Text("Cerrar") } })
    if (showVolume) {
        val audio = LocalContext.current.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
        var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
        AlertDialog(onDismissRequest = { showVolume = false }, title = { Text("Volumen multimedia") }, text = {
            Column {
                Slider(volume, { volume = it;audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..max)
                Text("La salida se gestiona con los controles de audio de Android. Puedes usar tus audífonos Bluetooth conectados.", color = VibeMuted, fontSize = 12.sp)
            }
        }, confirmButton = { TextButton({ showVolume = false }) { Text("Listo") } })
    }
}

internal fun playerBackgroundColors(color: Long): List<Color> {
    val top = Color(color).copy(alpha = 1f)
    val bottom = Color(0xFF101211)
    return listOf(top, lerp(top, bottom, .55f), bottom)
}
