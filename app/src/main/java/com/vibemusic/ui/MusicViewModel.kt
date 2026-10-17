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
import androidx.room.withTransaction
import com.google.common.util.concurrent.ListenableFuture
import com.vibemusic.VibeApplication
import com.vibemusic.data.*
import com.vibemusic.downloads.DownloadCoordinator
import com.vibemusic.model.*
import com.vibemusic.playback.PlaybackService
import com.vibemusic.playback.toMediaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import okhttp3.HttpUrl.Companion.toHttpUrl

data class PlaybackState(val currentId: String? = null, val playing: Boolean = false, val position: Long = 0, val duration: Long = 0, val shuffle: Boolean = false, val repeat: Int = 0, val queue: List<String> = emptyList(), val connecting: Boolean = true, val canNext: Boolean = false)
data class SearchIntent(val query: String, val requestId: String?, val nonce: Long = System.nanoTime())
class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VibeApplication
    val repo = app.repository
    private val transfers = PlaylistTransferRepository(app, app.database)
    val tracks = repo.tracks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = repo.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val membership = repo.members.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlistRequests = repo.requests.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads = repo.downloads.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    private val _playback = MutableStateFlow(PlaybackState())
    val playback = _playback.asStateFlow()
    private val included by lazy { CatalogRepository.bundled(app) }
    private val publicSource = CommonsMusicSource()
    private val youtube = YouTubeMusic(app)
    private val _youtubeTracks = MutableStateFlow<List<YouTubeTrack>>(emptyList())
    val youtubeTracks = _youtubeTracks.asStateFlow()
    private val _youtubeLoading = MutableStateFlow(false)
    val youtubeLoading = _youtubeLoading.asStateFlow()
    private val _youtubeError = MutableStateFlow<String?>(null)
    val youtubeError = _youtubeError.asStateFlow()
    private val _youtubeNext = MutableStateFlow<Int?>(null)
    val youtubeNext = _youtubeNext.asStateFlow()
    private var youtubeJob: Job? = null
    private var youtubeGeneration = 0L
    private val _catalog = MutableStateFlow<List<CatalogTrack>>(emptyList())
    val catalog = _catalog.asStateFlow()
    private val _catalogLoading = MutableStateFlow(false)
    val catalogLoading = _catalogLoading.asStateFlow()
    private val _catalogError = MutableStateFlow<String?>(null)
    val catalogError = _catalogError.asStateFlow()
    private val _nextOffset = MutableStateFlow<Int?>(null)
    val nextOffset = _nextOffset.asStateFlow()
    private val _searchIntent = MutableStateFlow<SearchIntent?>(null)
    val searchIntent = _searchIntent.asStateFlow()
    private var catalogJob: Job? = null
    private var catalogGeneration = 0L
    private var catalogQuery = ""
    private var customCache: Pair<String, List<CatalogTrack>>? = null
    private var resolveJob: Job? = null
    private val _resolveProgress = MutableStateFlow<String?>(null)
    val resolveProgress = _resolveProgress.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 12)
    private val coordinator = DownloadCoordinator(app, app.database)
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var pendingPlay: (() -> Unit)? = null
    private var cleared = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refreshPlayback()
        override fun onPlayerError(error: PlaybackException) { messages.tryEmit("No se pudo reproducir el audio: ${error.errorCodeName}") }
    }
    init {
        connect()
        viewModelScope.launch { while (isActive) { delay(500);refreshPlayback() } }
        viewModelScope.launch {
            // Also populate covers for songs downloaded before cover support was added.
            val attempted = mutableSetOf<String>()
            combine(tracks, app.settings.settings.map { it.offlineOnly }.distinctUntilChanged()) { list, offline -> list to offline }
                .collectLatest { (list, offline) ->
                    if (!offline) list.filter { it.sourceUrl?.let(YouTubeSearch::videoId) != null && it.artworkPath?.let { path -> File(path).isFile } != true }
                        .forEach { track ->
                            if (attempted.add(track.id)) try { repo.cacheYouTubeArtwork(track) }
                            catch (e: CancellationException) { attempted.remove(track.id);throw e }
                        }
                }
        }
    }
    private fun connect() {
        if (cleared || controller?.isConnected == true || future?.isDone == false) return
        _playback.value = _playback.value.copy(connecting = true)
        val building = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(disconnected: MediaController) {
                    if (controller === disconnected) { controller = null;_playback.value = _playback.value.copy(playing = false, connecting = false) }
                }
            }).buildAsync()
        future = building
        building.addListener({
            if (!cleared && future === building) {
                try {
                    controller = building.get();controller?.addListener(listener);refreshPlayback()
                    val action = pendingPlay;pendingPlay = null;action?.invoke()
                } catch (e: Exception) {
                    pendingPlay = null;messages.tryEmit("No se pudo conectar con el reproductor. Vuelve a abrir Vibe Music.")
                    _playback.value = _playback.value.copy(connecting = false)
                }
            }
        }, ContextCompat.getMainExecutor(app))
    }
    private fun refreshPlayback() {
        controller?.takeIf { it.isConnected }?.let { c ->
            _playback.value = PlaybackState(c.currentMediaItem?.mediaId, c.isPlaying, c.currentPosition.coerceAtLeast(0), c.duration.coerceAtLeast(0), c.shuffleModeEnabled, c.repeatMode, (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }, false, c.hasNextMediaItem())
        }
    }
    fun runTask(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { messages.emit(e.message ?: "No se pudo completar la acción") } } }
    fun play(items: List<Track>, index: Int = 0, shuffle: Boolean = false) {
        if (items.isEmpty()) return
        if (items.any { !File(it.localPath).exists() }) { messages.tryEmit("Falta un archivo de esta colección. Vuelve a importarlo o descargarlo");return }
        val c = controller?.takeIf { it.isConnected }
        if (c == null) { pendingPlay = { play(items, index, shuffle) };connect();return }
        c.setMediaItems(items.map { it.toMediaItem() }, index.coerceIn(items.indices), 0)
        c.shuffleModeEnabled = shuffle;c.prepare();c.play()
    }
    fun togglePlay() {
        val c = controller?.takeIf { it.isConnected }
        if (c == null) { pendingPlay = { togglePlay() };connect();return }
        if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_ENDED) c.seekTo(0);c.play() }
    }
    fun next() { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    fun pauseForExternalPlayback() { pendingPlay = null;controller?.takeIf { it.isConnected }?.pause() }
    fun previous() { controller?.let { if (it.currentPosition > 3000 || !it.hasPreviousMediaItem()) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seek(position: Long) { controller?.seekTo(position.coerceAtLeast(0)) }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }
    fun queueJump(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) { it.seekToDefaultPosition(index);it.play() } } }
    fun favorite(track: Track) = runTask { repo.dao.toggleFavorite(track.id) }
    fun import(uris: List<Uri>) = runTask {
        if (_busy.value) return@runTask
        _busy.value = true;var count = 0;var errors = 0
        try { uris.forEach { uri -> try { repo.importMusic(uri);count++ } catch (e: CancellationException) { throw e } catch (e: Exception) { errors++ } } }
        finally { _busy.value = false }
        messages.emit("$count archivo(s) importado(s)" + if (errors > 0) ". $errors archivo(s) no compatibles o inaccesibles" else ". Disponibles sin conexión")
    }
    fun addOriginals() = runTask {
        if (_busy.value) return@runTask
        _busy.value = true
        try { repo.addOriginals();messages.emit("Vibe Originals guardado. Ya puedes escuchar sin conexión") } finally { _busy.value = false }
    }
    fun createPlaylist(name: String, trackId: String? = null) = runTask {
        repo.database.withTransaction { val playlist = repo.createPlaylist(name);if (trackId != null && repo.dao.track(trackId) != null) repo.addToPlaylist(playlist.id, trackId) }
    }
    fun rename(playlist: Playlist, name: String) = runTask { repo.dao.updatePlaylist(playlist.copy(name = name.trim().take(100).ifBlank { playlist.name })) }
    fun deletePlaylist(playlist: Playlist) = runTask { repo.deletePlaylist(playlist) }
    fun playlistArtById(id: String, uri: Uri) = runTask { repo.dao.playlist(id)?.let { repo.setPlaylistArt(it, uri) } ?: error("La playlist ya no existe") }
    fun exportPlaylistById(id: String, uri: Uri) = runTask { repo.dao.playlist(id)?.let { transfers.exportFile(it, uri);messages.emit("Lista exportada como CSV, sin archivos de audio") } ?: error("La playlist ya no existe") }
    fun playlistArt(playlist: Playlist, uri: Uri) = runTask { repo.setPlaylistArt(playlist, uri) }
    fun addToPlaylist(playlist: Playlist, track: Track) = runTask { repo.addToPlaylist(playlist.id, track.id);messages.emit("Añadida a ${playlist.name}") }
    fun removeFromPlaylist(playlist: Playlist, track: Track) = runTask { repo.database.withTransaction { repo.dao.deleteRequestsForTrack(playlist.id, track.id);repo.dao.removeMember(playlist.id, track.id) } }
    fun reorder(playlist: Playlist, track: Track, delta: Int) = runTask { repo.move(playlist.id, track.id, delta) }
    fun deleteTrack(track: Track) = runTask {
        repo.deleteTrack(track)
        controller?.let { c -> val index = (0 until c.mediaItemCount).indexOfFirst { c.getMediaItemAt(it).mediaId == track.id };if (index >= 0) c.removeMediaItem(index) }
    }
    fun setWifi(value: Boolean) = runTask { app.settings.wifiOnly(value);coordinator.pauseAll();messages.emit("Preferencia guardada. Reanuda las descargas con la nueva red") }
    fun setOffline(value: Boolean) = runTask { app.settings.offlineOnly(value);if (value) { cancelCatalogSearch();cancelYouTubeSearch();cancelResolve();coordinator.pauseAll() } }
    fun setQuality(value: String) = runTask { app.settings.quality(value) }
    fun setCatalog(value: String) = runTask {
        if (value.isNotBlank()) { val parsed = value.toHttpUrl();require(parsed.isHttps && parsed.username.isBlank() && parsed.password.isBlank()) { "Utiliza una dirección HTTPS sin credenciales" } }
        app.settings.catalogUrl(value);customCache = null;loadCatalog(catalogQuery);messages.emit("Fuente adicional guardada")
    }
    fun rememberSearch(value: String) = runTask { app.settings.rememberSearch(value) }
    fun searchYouTube(query: String, append: Boolean = false) {
        youtubeJob?.cancel();val generation = ++youtubeGeneration
        if (query.isBlank()) { _youtubeTracks.value = emptyList();_youtubeNext.value = null;_youtubeError.value = null;_youtubeLoading.value = false;return }
        youtubeJob = viewModelScope.launch {
            val offset = if (append) _youtubeNext.value ?: return@launch else 0
            if (!append) { _youtubeTracks.value = emptyList();_youtubeNext.value = null }
            _youtubeLoading.value = true;_youtubeError.value = null
            try {
                check(!app.settings.settings.first().offlineOnly) { "YouTube necesita conexión a internet" }
                val page = youtube.search(query, offset)
                _youtubeTracks.value = (_youtubeTracks.value + page.tracks).distinctBy { it.id };_youtubeNext.value = page.nextOffset
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _youtubeError.value = "No se pudo buscar en YouTube. Comprueba tu conexión; si persiste, el motor puede necesitar actualizarse. Puedes buscar en su página dentro de Vibe con el botón de abajo" }
            finally { if (generation == youtubeGeneration) _youtubeLoading.value = false }
        }
    }
    fun cancelYouTubeSearch() { youtubeGeneration++;youtubeJob?.cancel();_youtubeLoading.value = false }
    suspend fun resolveYouTubeAudio(track: YouTubeTrack): YouTubeAudioStream {
        check(!app.settings.settings.first().offlineOnly) { "YouTube necesita conexión a internet" }
        return youtube.resolveAudio(track.url)
    }
    fun downloadYouTube(track: YouTubeTrack, requestId: String? = null) = runTask {
        val id = coordinator.enqueueYouTube(track, app.settings.settings.first(), requestId)
        messages.emit(if (repo.dao.track(id) != null) "MP3 disponible sin conexión" else "Descarga y conversión a MP3 añadidas. Consulta Descargas")
        if (requestId != null) clearSearchTarget()
    }
    fun loadCatalog(query: String = catalogQuery, append: Boolean = false) {
        catalogJob?.cancel();catalogQuery = query
        val generation = ++catalogGeneration
        catalogJob = viewModelScope.launch {
            val current = app.settings.settings.first()
            val offset = if (append) _nextOffset.value ?: return@launch else 0
            _catalogLoading.value = true;_catalogError.value = null
            if (!append) { _nextOffset.value = null;_catalog.value = included.filter { MusicMatching.matchesQuery(query, it.title, it.artist, it.album) } }
            try {
                if (current.offlineOnly) { _catalogError.value = "Modo sin conexión: puedes usar tu biblioteca y el audio incluido";return@launch }
                val errors = mutableListOf<String>()
                try {
                    val page = publicSource.search(query, offset)
                    _catalog.value = (_catalog.value + page.tracks).distinctBy { it.id }
                    _nextOffset.value = page.nextOffset
                } catch (e: CancellationException) { throw e } catch (e: Exception) { errors.add(e.message ?: "No se pudo conectar con Wikimedia Commons") }
                if (!append && current.catalogUrl.isNotBlank()) {
                    try {
                        val custom = customCache?.takeIf { it.first == current.catalogUrl }?.second ?: CatalogRepository().load(current.catalogUrl).also { customCache = current.catalogUrl to it }
                        _catalog.value = (_catalog.value + custom.filter { MusicMatching.matchesQuery(query, it.title, it.artist, it.album) }).distinctBy { it.id }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { errors.add("Fuente adicional: ${e.message}") }
                }
                _catalogError.value = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
            } catch (e: CancellationException) { throw e }
            finally { if (generation == catalogGeneration) _catalogLoading.value = false }
        }
    }
    fun cancelCatalogSearch() { catalogGeneration++;catalogJob?.cancel();_catalogLoading.value = false }
    fun searchForRequest(request: PlaylistRequest) { _searchIntent.value = SearchIntent("${request.title} ${request.artist}", request.id) }
    fun clearSearchTarget() { _searchIntent.value = null }
    private suspend fun saveCatalogTrack(track: CatalogTrack, quality: CatalogQuality, requestId: String? = null): String {
        if (track.assetPath != null) {
            val saved = app.assets.open(track.assetPath).use { repo.importStream(it, "${track.title}.wav", "original") }
            if (requestId != null) PlaylistLinks.link(app.database, requestId, saved)
            return saved.id
        }
        return coordinator.enqueue(track, quality, app.settings.settings.first(), requestId)
    }
    fun download(track: CatalogTrack, quality: CatalogQuality, requestId: String? = null) = runTask {
        val id = saveCatalogTrack(track, quality, requestId)
        messages.emit(if (repo.dao.track(id) != null) "Canción disponible sin conexión" else "Descarga añadida. Consulta su progreso en Descargas")
        if (requestId != null) clearSearchTarget()
    }
    fun downloadAlbum(items: List<CatalogTrack>) = runTask {
        var saved = 0;var errors = 0
        items.forEach { track -> try { saveCatalogTrack(track, track.qualities.firstOrNull { it.label == settings.value.quality } ?: track.qualities.first());saved++ } catch (e: CancellationException) { throw e } catch (e: Exception) { errors++ } }
        messages.emit("$saved canciones guardadas o en cola" + if (errors > 0) "; $errors no se pudieron añadir" else "")
    }
    fun importPlaylistFile(uri: Uri) = runTask {
        if (_busy.value) return@runTask
        _busy.value = true
        try {
            val result = transfers.importFile(uri)
            messages.emit("${result.playlists} playlist(s), ${result.songs} canciones: ${result.matched} disponibles y ${result.songs - result.matched} pendientes. ${result.duplicates} duplicados y ${result.skipped} filas sin datos omitidos")
        } finally { _busy.value = false }
    }
    fun exportPlaylist(playlist: Playlist, uri: Uri) = runTask { transfers.exportFile(playlist, uri);messages.emit("Lista exportada como CSV. El archivo contiene títulos, no audio") }
    fun linkRequest(request: PlaylistRequest, track: Track) = runTask { PlaylistLinks.link(app.database, request.id, track);messages.emit("Canción vinculada a la playlist") }
    fun deleteRequest(request: PlaylistRequest) = runTask { repo.dao.deleteRequest(request.id) }
    fun resolvePlaylist(playlist: Playlist) {
        if (resolveJob?.isActive == true) return
        resolveJob = viewModelScope.launch {
            var found = 0;var failed = 0
            try {
                check(!app.settings.settings.first().offlineOnly) { "Desactiva el modo sin conexión para buscar fuentes" }
                val pending = repo.dao.requests().filter { it.playlistId == playlist.id && it.trackId == null && it.downloadId == null }
                pending.forEachIndexed { index, request ->
                    _resolveProgress.value = "Buscando ${index + 1} de ${pending.size} · ${request.title}"
                    try {
                        val matches = (included + publicSource.search("${request.title} ${request.artist}").tracks).filter { MusicMatching.sameRecording(request.title, request.artist, it.title, it.artist) }.distinctBy { it.id }
                        if (matches.size == 1) { val track = matches.single();saveCatalogTrack(track, track.qualities.first(), request.id);found++ }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { failed++ }
                    if (index < pending.lastIndex) delay(1000)
                }
                messages.emit("$found coincidencias autorizadas guardadas o en cola. ${pending.size - found} pendientes" + if (failed > 0) "; $failed búsquedas con error de conexión" else "")
            } catch (e: CancellationException) { messages.tryEmit("Búsqueda detenida. Las descargas ya añadidas continúan");throw e }
            catch (e: Exception) { messages.emit(e.message ?: "No se pudo buscar la playlist") }
            finally { _resolveProgress.value = null }
        }
    }
    fun cancelResolve() { resolveJob?.cancel() }
    fun pauseDownload(id: String) = runTask { coordinator.pause(id) }
    fun resumeDownload(id: String) = runTask { coordinator.resume(id, app.settings.settings.first()) }
    fun cancelDownload(id: String) = runTask { coordinator.cancel(id) }
    fun sleep(minutes: Int) {
        val c = controller?.takeIf { it.isConnected } ?: run { messages.tryEmit("El reproductor no está conectado. Inicia una canción primero");return }
        val command = c.sendCustomCommand(SessionCommand("vibe.sleep", Bundle.EMPTY), Bundle().apply { putInt("minutes", minutes) })
        command.addListener({
            runCatching { check(command.get().resultCode == SessionResult.RESULT_SUCCESS) }
                .onSuccess { messages.tryEmit(if (minutes == 0) "Temporizador desactivado" else "La música se detendrá en $minutes minutos") }
                .onFailure { messages.tryEmit("No se pudo activar el temporizador") }
        }, ContextCompat.getMainExecutor(app))
    }
    override fun onCleared() { cleared = true;controller?.removeListener(listener);future?.let(MediaController::releaseFuture);super.onCleared() }
}
