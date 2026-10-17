package com.vibemusic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.vibemusic.data.YouTubeAudioStream
import com.vibemusic.data.YouTubeTrack
import com.vibemusic.downloads.YouTubeFailure
import com.vibemusic.model.MusicRules
import com.vibemusic.playback.ForegroundAudioPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** A foreground-only listening preview. It never creates a download or library entry. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun YouTubeAudioPreview(track: YouTubeTrack, resolve: suspend (YouTubeTrack) -> YouTubeAudioStream) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var attempt by remember(track.id) { mutableIntStateOf(0) }
    var stream by remember(track.id, attempt) { mutableStateOf<YouTubeAudioStream?>(null) }
    var error by remember(track.id, attempt) { mutableStateOf<String?>(null) }
    var resolving by remember(track.id, attempt) { mutableStateOf(true) }
    var buffering by remember(track.id, attempt) { mutableStateOf(false) }
    var playing by remember(track.id, attempt) { mutableStateOf(false) }
    var position by remember(track.id, attempt) { mutableLongStateOf(0) }
    var duration by remember(track.id, attempt) { mutableLongStateOf(track.durationMs) }
    var seek by remember(track.id, attempt) { mutableStateOf<Float?>(null) }
    LaunchedEffect(track.id, attempt) {
        try { stream = resolve(track) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = YouTubeFailure.from(e).message }
        finally { resolving = false }
    }
    val player = remember(stream, track.id, attempt) {
        stream?.let { audio ->
            val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(audio.headers)
                .setConnectTimeoutMs(20_000).setReadTimeoutMs(20_000)
            ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
                .build().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                    setHandleAudioBecomingNoisy(true)
                }
        }
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onEvents(source: Player, events: Player.Events) {
                playing = source.isPlaying;buffering = source.playbackState == Player.STATE_BUFFERING
                if (source.duration > 0) duration = source.duration
            }
            override fun onPlayerError(exception: PlaybackException) {
                error = "No se pudo escuchar este audio. Pulsa Reintentar audio para renovar la conexión o prueba la descarga."
                buffering = false
            }
        }
        val playback = player?.let { ForegroundAudioPreview(it, lifecycle) }
        player?.addListener(listener)
        stream?.let { playback?.prepare(it.url, it.mimeType) }
        onDispose { player?.removeListener(listener);playback?.release() }
    }
    LaunchedEffect(player) { while (isActive && player != null) { position = player.currentPosition.coerceAtLeast(0);delay(300) } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        YouTubeCover(track.id, track.title, Modifier.fillMaxWidth().aspectRatio(1f))
        if (resolving || buffering) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            Text(if (resolving) "Preparando audio…" else "Cargando audio…", color = VibeMuted)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        Slider(value = seek ?: position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
            onValueChange = { seek = it }, onValueChangeFinished = { seek?.let { player?.seekTo(it.toLong()) };seek = null },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f), enabled = player != null && error == null && duration > 0)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(MusicRules.formatTime(position), color = VibeMuted);Text(MusicRules.formatTime(duration), color = VibeMuted)
        }
        FilledIconButton(onClick = {
            player?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekTo(0);it.play() } }
        }, enabled = player != null && error == null, modifier = Modifier.size(64.dp)) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pausar audio de muestra" else "Reproducir audio de muestra", Modifier.size(40.dp))
        }
        if (error != null) TextButton({ attempt++ }) { Text("Reintentar audio") }
        Text("Vista previa en línea · Pulsa Descargar MP3 para guardarla", color = VibeMuted)
    }
}
