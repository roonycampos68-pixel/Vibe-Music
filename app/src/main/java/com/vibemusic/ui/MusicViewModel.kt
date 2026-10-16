package com.vibemusic.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.*
import com.vibemusic.VibeApplication
import com.vibemusic.data.CatalogRepository
import com.vibemusic.data.Settings
import com.vibemusic.downloads.DownloadCoordinator
import com.vibemusic.model.*
import com.vibemusic.playback.PlaybackService
import com.vibemusic.playback.toMediaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PlaybackState(val currentId: String? = null, val playing: Boolean = false, val position: Long = 0, val duration: Long = 0, val shuffle: Boolean = false, val repeat: Int = 0, val queue: List<String> = emptyList(), val connecting: Boolean = true)
class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VibeApplication
    val repo = app.repository
    val tracks = repo.tracks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = repo.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val membership = repo.members.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads = repo.downloads.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    private val _playback = MutableStateFlow(PlaybackState())
    val playback = _playback.asStateFlow()
    private val _catalog = MutableStateFlow<List<CatalogTrack>>(emptyList())
    val catalog = _catalog.asStateFlow()
    private val _catalogLoading = MutableStateFlow(false)
    val catalogLoading = _catalogLoading.asStateFlow()
    private val _catalogError = MutableStateFlow<String?>(null)
    val catalogError = _catalogError.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val coordinator = DownloadCoordinator(app, app.database)
    private var controller: MediaController? = null
    private val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refreshPlayback()
        override fun onPlayerError(error: PlaybackException) { messages.tryEmit("No se pudo reproducir el audio: ${error.errorCodeName}") }
    }
    init {
        future.addListener({
            try { controller = future.get();controller?.addListener(listener);refreshPlayback() }
            catch (e: Exception) { messages.tryEmit("No se pudo conectar con el reproductor. Vuelve a abrir Vibe Music.");_playback.value = PlaybackState(connecting = false) }
        }, ContextCompat.getMainExecutor(app))
        viewModelScope.launch { while (isActive) { delay(500);refreshPlayback() } }
    }
    private fun refreshPlayback() {
        controller?.let { c -> _playback.value = PlaybackState(c.currentMediaItem?.mediaId, c.isPlaying, c.currentPosition.coerceAtLeast(0), c.duration.coerceAtLeast(0), c.shuffleModeEnabled, c.repeatMode, (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }, false) }
    }
    fun runTask(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { messages.emit(e.message ?: "No se pudo completar la acción") } } }
    fun play(items: List<Track>, index: Int = 0, shuffle: Boolean = false) {
        if (items.isEmpty()) return
        val c = controller ?: run { messages.tryEmit("El reproductor se está conectando. Inténtalo de nuevo en un momento.");return }
        c.setMediaItems(items.map { it.toMediaItem() }, index.coerceIn(items.indices), 0)
        c.shuffleModeEnabled = shuffle;c.prepare();c.play()
    }
    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekTo(0);it.play() } } }
    fun next() { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() else it.seekTo(0) } }
    fun previous() { controller?.let { if (it.currentPosition > 3000 || !it.hasPreviousMediaItem()) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seek(position: Long) { controller?.seekTo(position.coerceAtLeast(0)) }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }
    fun queueJump(index: Int) { controller?.let { it.seekToDefaultPosition(index);it.play() } }
    fun favorite(track: Track) = runTask { repo.dao.toggleFavorite(track.id) }
    fun import(uris: List<Uri>) = runTask {
        _busy.value = true
        var count = 0;var errors = 0
        try { uris.forEach { uri -> try { repo.importMusic(uri);count++ } catch (e: Exception) { errors++ } } }
        finally { _busy.value = false }
        messages.emit("$count archivo(s) importado(s)" + if (errors > 0) ". $errors archivo(s) no compatibles o inaccesibles" else ". Disponibles sin conexión")
    }
    fun addOriginals() = runTask {
        _busy.value = true
        try { repo.addOriginals();messages.emit("Vibe Originals guardado. Ya puedes escuchar sin conexión") }
        finally { _busy.value = false }
    }
    fun createPlaylist(name: String) = runTask { repo.createPlaylist(name) }
    fun rename(playlist: Playlist, name: String) = runTask { repo.dao.updatePlaylist(playlist.copy(name = name.trim().take(100).ifBlank { playlist.name })) }
    fun deletePlaylist(playlist: Playlist) = runTask { repo.deletePlaylist(playlist) }
    fun playlistArt(playlist: Playlist, uri: Uri) = runTask { repo.setPlaylistArt(playlist, uri) }
    fun addToPlaylist(playlist: Playlist, track: Track) = runTask { repo.addToPlaylist(playlist.id, track.id);messages.emit("Añadida a ${playlist.name}") }
    fun removeFromPlaylist(playlist: Playlist, track: Track) = runTask { repo.dao.removeMember(playlist.id, track.id) }
    fun reorder(playlist: Playlist, track: Track, delta: Int) = runTask { repo.move(playlist.id, track.id, delta) }
    fun deleteTrack(track: Track) = runTask {
        controller?.let { c ->
            val index = (0 until c.mediaItemCount).indexOfFirst { c.getMediaItemAt(it).mediaId == track.id }
            if (index >= 0) c.removeMediaItem(index)
        }
        repo.deleteTrack(track)
    }
    fun setWifi(value: Boolean) = runTask { app.settings.wifiOnly(value);coordinator.pauseAll();messages.emit("Preferencia guardada. Reanuda las descargas con la nueva red") }
    fun setOffline(value: Boolean) = runTask { if (value) coordinator.pauseAll();app.settings.offlineOnly(value) }
    fun setQuality(value: String) = runTask { app.settings.quality(value) }
    fun setCatalog(value: String) = runTask {
        require(value.isBlank() || value.startsWith("https://")) { "Utiliza una dirección HTTPS" }
        app.settings.catalogUrl(value);_catalog.value = emptyList();_catalogError.value = null;messages.emit("Catálogo guardado. Ábrelo desde Buscar")
    }
    fun rememberSearch(value: String) = runTask { app.settings.rememberSearch(value) }
    fun loadCatalog() = runTask {
        val currentSettings = app.settings.settings.first()
        if (currentSettings.offlineOnly) { _catalogError.value = "El modo sin conexión está activo";return@runTask }
        if (currentSettings.catalogUrl.isBlank()) { _catalogError.value = "Configura un catálogo autorizado en Ajustes";return@runTask }
        _catalogLoading.value = true;_catalogError.value = null
        try { _catalog.value = CatalogRepository().load(currentSettings.catalogUrl) }
        catch (e: Exception) { _catalogError.value = e.message ?: "No se pudo cargar el catálogo" }
        finally { _catalogLoading.value = false }
    }
    fun download(track: CatalogTrack, quality: CatalogQuality) = runTask { coordinator.enqueue(track, quality, settings.value);messages.emit("Descarga añadida") }
    fun downloadAlbum(items: List<CatalogTrack>) = runTask {
        items.forEach { track -> coordinator.enqueue(track, track.qualities.firstOrNull { it.label == settings.value.quality } ?: track.qualities.first(), settings.value) }
        messages.emit("Álbum añadido a Descargas")
    }
    fun pauseDownload(id: String) = runTask { withContext(Dispatchers.IO) { coordinator.pause(id) } }
    fun resumeDownload(id: String) = runTask { coordinator.resume(id, settings.value) }
    fun cancelDownload(id: String) = runTask { coordinator.cancel(id) }
    fun sleep(minutes: Int) {
        controller?.sendCustomCommand(SessionCommand("vibe.sleep", Bundle.EMPTY), Bundle().apply { putInt("minutes", minutes) })
        messages.tryEmit(if (minutes == 0) "Temporizador desactivado" else "La música se detendrá en $minutes minutos")
    }
    override fun onCleared() { controller?.removeListener(listener);MediaController.releaseFuture(future);super.onCleared() }
}
