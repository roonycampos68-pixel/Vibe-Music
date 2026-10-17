package com.vibemusic.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.*
import com.vibemusic.data.YouTubeAudioStream
import com.vibemusic.data.YouTubeTrack
import com.vibemusic.data.YouTubeWeb
import com.vibemusic.downloads.YouTubeFailure
import com.vibemusic.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OnlineSelection(val track: YouTubeTrack? = null, val requestId: String? = null, val loading: Boolean = false, val error: String? = null)
class OnlinePlaybackState {
    private val mutable = MutableStateFlow(OnlineSelection())
    val state = mutable.asStateFlow()
    fun update(value: OnlineSelection) { mutable.value = value }
}
fun YouTubeTrack.asOnlineTrack() = Track(downloadId, title, artist, "YouTube", durationMs, "", contentHash = downloadId, source = "youtube-online", sourceUrl = url)
fun MediaItem.isOnlineAudio() = mediaMetadata.extras?.getBoolean("vibe.online") == true
fun YouTubeAudioStream.mediaItem(track: YouTubeTrack): MediaItem = MediaItem.Builder().setMediaId(track.downloadId).setUri(url).setMimeType(mimeType)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle("YouTube")
        .setArtworkUri(Uri.parse(YouTubeWeb.thumbnail(track.id))).setExtras(Bundle().apply { putBoolean("vibe.online", true) }).build()).build()

/** One service-owned selection; cancellation makes late resolutions unable to replace a newer song. */
class OnlinePlayback(
    private val scope: CoroutineScope,
    private val player: Player,
    private val resolver: StreamResolver,
    private val state: OnlinePlaybackState,
    private val setHeaders: (Map<String, String>) -> Unit
) {
    private var job: Job? = null
    private var generation = 0L
    fun select(track: YouTubeTrack, requestId: String? = null) {
        val current = ++generation
        job?.cancel();resolver.prioritize(track.id)
        player.stop();player.clearMediaItems()
        state.update(OnlineSelection(track, requestId, loading = true))
        job = scope.launch {
            try {
                withTimeout(25_000) {
                    val audio = resolver.resolve(track)
                    ensureActive()
                    setHeaders(audio.headers)
                    awaitReady(audio.mediaItem(track))
                }
                if (generation == current) state.update(OnlineSelection(track, requestId))
            } catch (e: TimeoutCancellationException) {
                if (generation == current) fail(track, requestId, "YouTube está tardando demasiado. Pulsa reproducir para reintentar")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == current) fail(track, requestId, YouTubeFailure.from(e).message) }
        }
    }
    private suspend fun awaitReady(item: MediaItem) = suspendCancellableCoroutine<Unit> { continuation ->
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(value: Int) {
                if (value == Player.STATE_READY && continuation.isActive) { player.removeListener(this);continuation.resume(Unit) }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (continuation.isActive) { player.removeListener(this);continuation.resumeWithException(error) }
            }
        }
        player.addListener(listener)
        continuation.invokeOnCancellation { scope.launch { player.removeListener(listener) } }
        try { player.setMediaItem(item);player.prepare();player.play() }
        catch (e: Exception) { player.removeListener(listener);if (continuation.isActive) continuation.resumeWithException(e) }
    }
    private fun fail(track: YouTubeTrack, requestId: String?, message: String) {
        resolver.invalidate(track.id);resolver.cancelPending();player.stop()
        state.update(OnlineSelection(track, requestId, error = message))
    }
    fun playbackFailed(error: PlaybackException) {
        val selected = state.state.value
        if (selected.track != null && !selected.loading) fail(selected.track, selected.requestId, "No se pudo continuar el audio. Pulsa reproducir para renovar la conexión")
    }
    fun cancel(stop: Boolean = false) {
        generation++;job?.cancel();job = null;resolver.cancelPending();state.update(OnlineSelection())
        if (stop) { player.stop();player.clearMediaItems() }
    }
}
