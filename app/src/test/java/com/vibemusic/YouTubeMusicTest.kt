package com.vibemusic

import androidx.test.core.app.ApplicationProvider
import com.vibemusic.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class YouTubeMusicTest {
    @Test fun acceptsSupportedYouTubeLinksAndRemovesPlaylistAndTrackingParameters() {
        val id = "dQw4w9WgXcQ"
        listOf("https://youtu.be/$id?si=tracking", "https://www.youtube.com/watch?v=$id&list=PLtest", "https://music.youtube.com/watch?v=$id", "https://m.youtube.com/shorts/$id").forEach {
            assertEquals(id, YouTubeSearch.videoId(it))
            assertEquals("https://www.youtube.com/watch?v=$id", YouTubeSearch.videoUrl(YouTubeSearch.videoId(it)!!).toString())
        }
        listOf("http://youtu.be/$id", "https://youtube.com.evil.example/watch?v=$id", "https://user@youtube.com/watch?v=$id", "file:///etc/passwd", "https://youtube.com/watch?v=--exec", "https://youtu.be/$id/extra").forEach { assertNull(YouTubeSearch.videoId(it)) }
    }
    @Test fun parsesRealFieldsAndKeepsRestrictedLiveOrInvalidItemsOutOfResults() {
        val json = """{"entries":[{"id":"dQw4w9WgXcQ","title":"Mi música","channel":"Mi artista","duration":213.5},{"id":"dQw4w9WgXcQ","title":"Duplicado"},{"id":"aaaaaaaaaaa","title":"Privado","availability":"private"},{"id":"bbbbbbbbbbb","title":"En vivo","is_live":true},{"id":"bad","title":"Inválido"},null]}"""
        val page = YouTubeMetadata.parse(json)
        val track = page.tracks.single()
        assertEquals("Mi música", track.title);assertEquals("Mi artista", track.artist);assertEquals(213500L, track.durationMs)
        assertEquals("youtube-dQw4w9WgXcQ", track.downloadId);assertNull(page.nextOffset)
    }
    @Test fun handlesSingleVideoEmptyResultsAndPagedSearchWithoutInventingTracks() {
        assertTrue(YouTubeMetadata.parse("{\"entries\":[]}").tracks.isEmpty())
        assertEquals(1, YouTubeMetadata.parse("{\"id\":\"dQw4w9WgXcQ\",\"title\":\"Una canción\"}").tracks.size)
        val entries = JSONArray();repeat(20) { entries.put(JSONObject().put("id", "dQw4w9WgXcQ").put("title", "Audio")) }
        assertEquals(40, YouTubeMetadata.parse(JSONObject().put("entries", entries).toString(), 20).nextOffset)
        assertNull(YouTubeMetadata.parse(JSONObject().put("entries", entries).toString(), 80).nextOffset)
        assertThrows(IllegalArgumentException::class.java) { YouTubeMetadata.parse("{}") }
    }
    @Test fun searchTreatsInputAsOneArgumentAndUsesFlatMetadataInsteadOfDownloading() {
        val request = YouTubeCommands.search("--exec echo test & artista", 20)
        val args = request.buildCommand()
        assertEquals("ytsearch100:--exec echo test & artista", args.last())
        assertFalse(request.hasOption("--exec"));assertTrue(request.hasOption("--skip-download"))
        assertEquals("21", request.getOption("--playlist-start"));assertEquals("40", request.getOption("--playlist-end"))
    }
    @Test fun mp3RequestDownloadsOnlyOneVideoAndKeepsPartialTransfersResumable() {
        val request = YouTubeCommands.download("https://youtu.be/dQw4w9WgXcQ?list=PLtest", File("/private/pending/job"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", request.buildCommand().last())
        assertTrue(request.hasOption("--extract-audio"));assertEquals("mp3", request.getOption("--audio-format"))
        assertTrue(request.hasOption("--continue"));assertTrue(request.hasOption("--no-playlist"))
        assertFalse(request.hasOption("--no-check-certificates"));assertFalse(request.hasOption("--allow-unplayable-formats"))
        assertThrows(IllegalStateException::class.java) { YouTubeCommands.download("https://example.org/file", File("/private")) }
    }
    @Test fun packagedEngineMatchesTheChecksumPublishedByItsMaintainer() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bytes = context.resources.openRawResource(R.raw.ytdlp).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(YouTubeMusic.ENGINE_SHA256, digest)
    }
    @Test fun resumingKeepsOriginalBytesAndIsolatesAnEncoderThatFinishesAfterCancellation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val base = File(context.cacheDir, "conversion-resume")
        try {
            val first = ConversionWorkspace.prepare(base)
            File(first, "audio.webm.part").writeText("original bytes")
            val oldMp3 = File(first, "audio.mp3")
            oldMp3.outputStream().use { staleEncoder ->
                staleEncoder.write("incomplete mp3".toByteArray())
                val second = ConversionWorkspace.prepare(base)
                assertNotEquals(first.absolutePath, second.absolutePath)
                assertEquals("original bytes", File(second, "audio.webm.part").readText())
                assertFalse(File(second, "audio.mp3").exists())
                File(second, "audio.mp3").writeText("complete new mp3")
                staleEncoder.write("late old bytes".toByteArray());staleEncoder.flush()
                assertEquals("complete new mp3", File(second, "audio.mp3").readText())
            }
        } finally { base.deleteRecursively() }
    }
}
