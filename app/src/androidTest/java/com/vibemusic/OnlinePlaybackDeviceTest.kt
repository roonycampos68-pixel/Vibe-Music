package com.vibemusic

import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibemusic.data.YouTubeAudioStream
import com.vibemusic.data.YouTubeTrack
import com.vibemusic.playback.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** Real decoder/clock through the shared online controller; resolver uses bundled audio, no network. */
@RunWith(AndroidJUnit4::class)
class OnlinePlaybackDeviceTest {
    @Test fun mediaSessionCommandPlaysInTheServiceThenAcceptsALocalQueue() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as VibeApplication
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val delegate = VibeApplication::class.java.getDeclaredField("streamResolver\$delegate").apply { isAccessible = true }
        val previous = delegate.get(app)
        lateinit var future: ListenableFuture<MediaController>
        var controller: MediaController? = null
        val fixture = java.io.File(app.cacheDir, "service-playback-fixture.wav")
        app.assets.open("originals/midnight.wav").use { input -> fixture.outputStream().use { input.copyTo(it) } }
        runBlocking { app.settings.offlineOnly(false) }
        try {
            instrumentation.runOnMainSync {
                delegate.set(app, lazy { StreamResolver(scope, { YouTubeAudioStream("asset:///originals/midnight.wav", emptyMap()) }) })
                future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            }
            val active = future.get(40, TimeUnit.SECONDS)
            controller = active
            instrumentation.runOnMainSync {
                active.sendCustomCommand(SessionCommand("vibe.youtube", Bundle.EMPTY), Bundle().apply {
                    putString("id", "7Y-xGsEPnsU");putString("title", "Audio del servicio")
                    putString("artist", "Vibe");putLong("duration", 32000)
                })
            }
            var position = 0L
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35)
            while (position < 1000 && System.nanoTime() < deadline) {
                Thread.sleep(250)
                instrumentation.runOnMainSync { position = active.currentPosition }
                if (app.onlinePlayback.state.value.error != null) break
            }
            assertNull(app.onlinePlayback.state.value.error)
            assertTrue("Service clock did not advance: $position", position >= 1000)
            instrumentation.runOnMainSync {
                assertEquals("youtube-7Y-xGsEPnsU", active.currentMediaItem?.mediaId)
                assertFalse(app.onlinePlayback.state.value.loading)
                active.setMediaItem(MediaItem.Builder().setMediaId("local-service-test").setUri(android.net.Uri.fromFile(fixture)).build())
                active.prepare();active.play()
            }
            val localDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (app.onlinePlayback.state.value.track != null && System.nanoTime() < localDeadline) Thread.sleep(100)
            assertNull("Local queue did not cancel online selection", app.onlinePlayback.state.value.track)
            instrumentation.runOnMainSync { assertEquals("local-service-test", active.currentMediaItem?.mediaId) }
        } finally {
            instrumentation.runOnMainSync { controller?.pause();controller?.release();app.stopService(Intent(app, PlaybackService::class.java));scope.cancel() }
            instrumentation.waitForIdleSync()
            delegate.set(app, previous);fixture.delete()
            app.getSharedPreferences("playback", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun sharedPlayerStartsSeeksAndSwitchesToLocalAudioWithoutADialog() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val selection = OnlinePlaybackState()
        lateinit var player: ExoPlayer
        lateinit var playback: OnlinePlayback
        val song = YouTubeTrack("7Y-xGsEPnsU", "Prueba de audio", "Vibe", 32000)
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(instrumentation.targetContext).build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.countDown() }
                override fun onPlayerError(exception: PlaybackException) { error.set(exception.errorCodeName);ready.countDown() }
            })
            val resolver = StreamResolver(scope, { YouTubeAudioStream("asset:///originals/midnight.wav", emptyMap()) })
            playback = OnlinePlayback(scope, player, resolver, selection) {}
            playback.select(song)
        }
        try {
            assertTrue("Audio never prepared", ready.await(40, TimeUnit.SECONDS))
            assertNull(error.get())
            var position = 0L
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (position < 1000 && System.nanoTime() < deadline && error.get() == null) {
                Thread.sleep(250)
                instrumentation.runOnMainSync { position = player.currentPosition }
            }
            assertNull(error.get());assertTrue("Clock did not advance: $position", position >= 1000)
            instrumentation.runOnMainSync {
                assertFalse(selection.state.value.loading);assertNull(selection.state.value.error)
                assertEquals(song.downloadId, player.currentMediaItem?.mediaId)
                assertEquals(song.title, player.mediaMetadata.title.toString())
                assertNotNull(player.mediaMetadata.artworkUri)
                assertTrue(player.duration in 31000..34000)
                player.seekTo(5000);assertTrue(player.currentPosition >= 5000)
                player.pause();assertFalse(player.playWhenReady)
                playback.cancel()
                player.setMediaItem(MediaItem.Builder().setMediaId("local").setUri("asset:///originals/midnight.wav").build())
                player.prepare();player.play()
                assertNull(selection.state.value.track)
                assertEquals("local", player.currentMediaItem?.mediaId)
            }
        } finally { instrumentation.runOnMainSync { playback.cancel(stop = true);scope.cancel();player.release() } }
    }
}
