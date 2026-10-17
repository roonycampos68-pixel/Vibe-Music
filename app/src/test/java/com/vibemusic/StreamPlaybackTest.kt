package com.vibemusic

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.vibemusic.data.*
import com.vibemusic.playback.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class StreamPlaybackTest {
    private val first = YouTubeTrack("7Y-xGsEPnsU", "Primera", "Artista", 180000)
    private val second = YouTubeTrack("dQw4w9WgXcQ", "Segunda", "Artista", 200000)
    private fun audio() = YouTubeAudioStream("https://rr1.googlevideo.com/audio", mapOf("User-Agent" to "test"))
    private class FakePlayer(val ready: Boolean = true) {
        val listeners = mutableListOf<Player.Listener>()
        val items = mutableListOf<MediaItem>()
        var stopped = false
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "addListener" -> listeners.add(args!![0] as Player.Listener)
                "removeListener" -> listeners.remove(args!![0] as Player.Listener)
                "setMediaItem" -> items.add(args!![0] as MediaItem)
                "stop" -> stopped = true
                "play" -> if (ready) listeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY) }
            }
            when (method.returnType) { java.lang.Boolean.TYPE -> false;java.lang.Integer.TYPE -> 0;java.lang.Long.TYPE -> 0L;else -> null }
        } as Player
    }
    @Test fun prefetchIsReusedAndExpiredLinksAreResolvedAgain() = runTest {
        var requests = 0
        val resolver = StreamResolver(backgroundScope, { requests++;delay(500);audio() }, { testScheduler.currentTime })
        resolver.prefetch(first);runCurrent()
        val selected = async { resolver.resolve(first) };advanceTimeBy(501);runCurrent()
        assertEquals(audio(), selected.await());assertEquals(1, requests)
        assertEquals(audio(), resolver.resolve(first));assertEquals(1, requests)
        advanceTimeBy(300001)
        val renewed = async { resolver.resolve(first) };advanceTimeBy(501);runCurrent();renewed.await()
        assertEquals(2, requests)
    }
    @Test fun aLateOldSelectionCannotReplaceTheNewSongAndHeadersReachThePlayer() = runTest {
        val fake = FakePlayer();val state = OnlinePlaybackState();var headers = emptyMap<String, String>()
        val resolver = StreamResolver(backgroundScope, { track -> if (track == first) delay(10000);audio() })
        val playback = OnlinePlayback(backgroundScope, fake.player, resolver, state) { headers = it }
        playback.select(first);runCurrent();assertTrue(state.state.value.loading)
        playback.select(second);runCurrent()
        assertEquals(second, state.state.value.track);assertFalse(state.state.value.loading)
        assertEquals(listOf(second.downloadId), fake.items.map { it.mediaId });assertEquals("test", headers["User-Agent"])
        advanceTimeBy(11000);runCurrent();assertEquals(1, fake.items.size)
        playback.cancel(stop = true);assertNull(state.state.value.track);assertTrue(fake.stopped)
    }
    @Test fun bothResolutionAndInitialBufferingHaveABoundedWait() = runTest {
        for (slowResolver in listOf(true, false)) {
            val fake = FakePlayer(ready = false);val state = OnlinePlaybackState()
            val resolver = StreamResolver(backgroundScope, { if (slowResolver) delay(180000);audio() })
            val playback = OnlinePlayback(backgroundScope, fake.player, resolver, state) {}
            playback.select(first);runCurrent();advanceTimeBy(25001);runCurrent()
            assertFalse(state.state.value.loading);assertNotNull(state.state.value.error)
            assertTrue(fake.stopped);assertTrue(fake.listeners.isEmpty())
            playback.cancel()
        }
    }
    @Test fun cancellingAnOnlineRequestPreventsItFromReplacingALocalQueue() = runTest {
        val fake = FakePlayer();val state = OnlinePlaybackState()
        val resolver = StreamResolver(backgroundScope, { delay(5000);audio() })
        val playback = OnlinePlayback(backgroundScope, fake.player, resolver, state) {}
        playback.select(first);runCurrent();playback.cancel()
        fake.player.setMediaItem(MediaItem.Builder().setMediaId("local").setUri("file:///music.mp3").build())
        advanceTimeBy(6000);runCurrent()
        assertEquals(listOf("local"), fake.items.map { it.mediaId });assertNull(state.state.value.track)
    }
    @Test fun streamingDoesNotInheritDownloadRetriesOrPersistPlayableFilePaths() {
        val request = YouTubeCommands.preview(first.url)
        assertEquals("6", request.getOption("--socket-timeout"));assertEquals("0", request.getOption("--retries"))
        assertEquals("0", request.getOption("--extractor-retries"))
        assertEquals("youtube-online", first.asOnlineTrack().source);assertEquals("", first.asOnlineTrack().localPath)
        assertTrue(audio().mediaItem(first).isOnlineAudio())
    }
}
