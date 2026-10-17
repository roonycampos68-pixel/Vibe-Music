package com.vibemusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vibemusic.R
import com.vibemusic.MainActivity
import com.vibemusic.VibeApplication
import com.vibemusic.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.io.File
import android.net.Uri

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences("playback", MODE_PRIVATE) }
    private val sleep = Runnable { player.pause();preferences.edit().remove("sleepDeadline").apply() }
    private var restoring = true
    private lateinit var online: OnlinePlayback
    private var selectJob: Job? = null
    private val app get() = application as VibeApplication

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory().setConnectTimeoutMs(8_000).setReadTimeoutMs(8_000)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(1)))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 30_000, 750, 1500).build())
            .build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) { if (!restoring) persist() }
                override fun onPlayerError(error: PlaybackException) { if (::online.isInitialized) online.playbackFailed(error) }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (!restoring && mediaItem != null) scope.launch(Dispatchers.IO) { (application as VibeApplication).database.music().markPlayed(mediaItem.mediaId, System.currentTimeMillis()) }
                }
            })
        }
        online = OnlinePlayback(scope, player, app.streamResolver, app.onlinePlayback) { http.setDefaultRequestProperties(it) }
        scope.launch { app.settings.settings.collect { if (it.offlineOnly && app.onlinePlayback.state.value.track != null) online.cancel(stop = true) } }
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) })
        session = MediaSession.Builder(this, player).setSessionActivity(pending).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                if (controller.packageName != packageName && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
                val standard = super.onConnect(session, controller)
                // Custom commands are restricted to this app; standard media controls remain available to Android/Bluetooth.
                val commands = if (controller.packageName == packageName) standard.availableSessionCommands.buildUpon()
                    .add(SessionCommand("vibe.sleep", Bundle.EMPTY)).add(SessionCommand("vibe.youtube", Bundle.EMPTY))
                    .add(SessionCommand("vibe.youtube.stop", Bundle.EMPTY)).build() else standard.availableSessionCommands
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
            }
            override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
                // A local queue replaces any pending online selection before its resolver can finish.
                selectJob?.cancel();online.cancel()
                return super.onAddMediaItems(session, controller, mediaItems)
            }
            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (controller.packageName != packageName) return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                if (command.customAction == "vibe.youtube.stop") { selectJob?.cancel();online.cancel(stop = true);return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS)) }
                if (command.customAction == "vibe.youtube") {
                    val id = args.getString("id").orEmpty()
                    if (!id.matches(Regex("[a-zA-Z0-9_-]{11}"))) return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    val track = com.vibemusic.data.YouTubeTrack(id, args.getString("title").orEmpty().take(500), args.getString("artist").orEmpty().take(500), args.getLong("duration").coerceAtLeast(0))
                    selectJob?.cancel()
                    selectJob = scope.launch {
                        if (!app.settings.settings.first().offlineOnly) online.select(track, args.getString("requestId"))
                        else app.onlinePlayback.update(OnlineSelection(track, error = "Activa la conexión para escuchar YouTube"))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (command.customAction != "vibe.sleep") return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                handler.removeCallbacks(sleep)
                val minutes = args.getInt("minutes").coerceIn(0, 180)
                preferences.edit().putLong("sleepDeadline", if (minutes == 0) 0 else System.currentTimeMillis() + minutes * 60000L).apply()
                if (minutes > 0) handler.postDelayed(sleep, minutes * 60000L)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).build()
        scope.launch {
            val app = application as VibeApplication
            val saved = withContext(Dispatchers.IO) {
                val ids = runCatching { JSONArray(preferences.getString("queue", "[]")) }.getOrElse { JSONArray() }
                (0 until ids.length()).mapNotNull { app.database.music().track(ids.getString(it)) }.filter { File(it.localPath).exists() }
            }
            if (player.mediaItemCount == 0 && app.onlinePlayback.state.value.track == null && saved.isNotEmpty()) {
                val current = preferences.getString("currentId", null)
                val index = saved.indexOfFirst { it.id == current }.coerceAtLeast(0)
                val position = if (saved[index].id == current) preferences.getLong("position", 0).coerceIn(0, saved[index].durationMs) else 0L
                player.setMediaItems(saved.map { it.toMediaItem() }, index, position)
                player.shuffleModeEnabled = preferences.getBoolean("shuffle", false)
                player.repeatMode = preferences.getInt("repeat", Player.REPEAT_MODE_OFF)
                player.prepare()
            }
            restoring = false
            val deadline = preferences.getLong("sleepDeadline", 0)
            if (deadline > System.currentTimeMillis()) handler.postDelayed(sleep, deadline - System.currentTimeMillis())
            while (isActive) { delay(2000);persist() }
        }
    }
    private fun persist() {
        if (app.onlinePlayback.state.value.track != null || player.currentMediaItem?.isOnlineAudio() == true || player.mediaItemCount == 0) return // Keep the saved local queue when online playback stops.
        val queue = JSONArray()
        for (index in 0 until player.mediaItemCount) queue.put(player.getMediaItemAt(index).mediaId)
        preferences.edit().putString("queue", queue.toString()).putString("currentId", player.currentMediaItem?.mediaId)
            .putLong("position", player.currentPosition.coerceAtLeast(0)).putBoolean("shuffle", player.shuffleModeEnabled).putInt("repeat", player.repeatMode).apply()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) { persist();stopSelf() }
    }
    override fun onDestroy() {
        persist();restoring = true;handler.removeCallbacks(sleep);online.cancel();scope.cancel();session?.release();player.release();session = null
        super.onDestroy()
    }
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder().setMediaId(id).setUri(Uri.fromFile(File(localPath)))
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(artworkPath?.let { Uri.fromFile(File(it)) }).build()).build()
