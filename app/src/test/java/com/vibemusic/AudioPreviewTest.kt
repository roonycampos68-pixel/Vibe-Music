package com.vibemusic

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.Player
import com.vibemusic.data.YouTubeAudioStream
import com.vibemusic.data.YouTubeCommands
import com.vibemusic.playback.ForegroundAudioPreview
import com.vibemusic.ui.playerBackgroundColors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AudioPreviewTest {
    @Test fun resolvesAudioWithoutDownloadingOrRequiringTheEmbeddedVideoPlayer() {
        val request = YouTubeCommands.preview("https://youtu.be/7Y-xGsEPnsU")
        assertTrue(request.hasOption("--skip-download"));assertTrue(request.hasOption("--dump-single-json"))
        assertEquals("bestaudio[ext=m4a]/bestaudio", request.getOption("--format"))
        assertFalse(request.hasOption("--extract-audio"));assertFalse(request.hasOption("--output"))
        assertEquals("https://www.youtube.com/watch?v=7Y-xGsEPnsU", request.buildCommand().last())
        val stream = YouTubeAudioStream.parse("""{"url":"https://rr1.googlevideo.com/videoplayback?expire=123","acodec":"mp4a.40.2","vcodec":"none","http_headers":{"User-Agent":"test","Referer":"https://www.youtube.com/","Authorization":"secret"}}""")
        assertEquals("test", stream.headers["User-Agent"]);assertFalse(stream.headers.containsKey("Authorization"))
        assertTrue(stream.url.contains("expire=123"))
        assertEquals("application/x-mpegURL", YouTubeAudioStream.parse("""{"url":"https://manifest.googlevideo.com/api/manifest/hls","acodec":"mp4a.40.2","vcodec":"none","protocol":"m3u8_native"}""").mimeType)
    }
    @Test fun refusesInvalidMediaUrlsVideoFormatsAndLiveStreams() {
        listOf("http://rr1.googlevideo.com/audio", "file:///private", "https://googlevideo.com.evil.test/a", "https://user:password@rr1.googlevideo.com/a", "https://rr1.googlevideo.com:444/a").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { YouTubeAudioStream.parse("""{"url":"$url","acodec":"opus","vcodec":"none"}""") }
        }
        assertThrows(IllegalArgumentException::class.java) { YouTubeAudioStream.parse("""{"url":"https://rr1.googlevideo.com/a","is_live":true}""") }
        assertThrows(IllegalArgumentException::class.java) { YouTubeAudioStream.parse("""{"url":"https://rr1.googlevideo.com/a","acodec":"opus","vcodec":"avc1"}""") }
    }
    @Test fun previewPausesInBackgroundDoesNotAutoplayAfterLateResolutionAndReleasesOnce() {
        val calls = mutableListOf<String>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            calls += method.name
            when (method.returnType) { java.lang.Boolean.TYPE -> false;java.lang.Integer.TYPE -> 0;java.lang.Long.TYPE -> 0L;else -> null }
        } as Player
        val owner = object : LifecycleOwner { val registry = LifecycleRegistry.createUnsafe(this);override val lifecycle: Lifecycle get() = registry }
        owner.registry.currentState = Lifecycle.State.RESUMED
        val preview = ForegroundAudioPreview(player, owner.lifecycle)
        preview.prepare("https://rr1.googlevideo.com/audio")
        assertEquals(listOf("setMediaItem", "prepare", "play"), calls)
        owner.registry.currentState = Lifecycle.State.CREATED
        assertTrue(calls.contains("pause"))
        calls.clear();preview.prepare("https://rr1.googlevideo.com/renewed")
        assertEquals(listOf("setMediaItem", "prepare"), calls)
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertFalse(calls.contains("play"))
        preview.release();preview.release()
        assertEquals(1, calls.count { it == "release" })
    }
    @Test fun playerGradientIsOpaqueEvenWhenTheTrackColorHasTransparency() {
        listOf(0xFF3C5861, 0x003C5861, 0x803C5861).forEach { color ->
            assertTrue(playerBackgroundColors(color).all { it.alpha == 1f })
        }
    }
}
