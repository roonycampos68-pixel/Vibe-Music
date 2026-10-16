package com.vibemusic

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.vibemusic.data.*
import com.vibemusic.downloads.DownloadCoordinator
import com.vibemusic.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** WorkManager's test scheduler leaves network constraints unmet; no native process or network runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = VibeApplication::class)
class YouTubeDownloadTest {
    private val app = ApplicationProvider.getApplicationContext<VibeApplication>()
    private val track = YouTubeTrack("dQw4w9WgXcQ", "Canción de prueba", "Artista de prueba", 32000)
    @Before fun setup() { WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build()) }
    @Test fun queuesOnePersistentMp3JobAndLinksTheSelectedSpotifyEntryBeforeStarting() = runBlocking {
        PlaylistTransferRepository(app, app.database).importPlaylists(listOf(ImportedPlaylist("Mi lista", listOf(ImportedSong("Canción", "Artista")))))
        val request = app.database.music().requests().single()
        val coordinator = DownloadCoordinator(app, app.database)
        coordinator.enqueueYouTube(track, Settings(), request.id)
        coordinator.enqueueYouTube(track, Settings(), request.id)
        val saved = app.database.music().observeDownloads().first().single()
        assertEquals("youtube", saved.provider);assertEquals("LOCAL-SHA-256", saved.hashAlgorithm)
        assertEquals(track.url, saved.url);assertEquals("MP3 · alta calidad", saved.quality)
        assertEquals(track.downloadId, app.database.music().request(request.id)!!.downloadId)
        assertTrue(app.database.music().tracks().isEmpty())
        coordinator.pause(saved.id)
        assertEquals("paused", app.database.music().download(saved.id)!!.state)
        val partial = File(app.filesDir, "pending/youtube-${saved.id}/audio.webm.part").apply { parentFile!!.mkdirs();writeText("partial") }
        coordinator.cancel(saved.id)
        assertFalse(partial.exists());assertNull(app.database.music().download(saved.id))
        assertNull(app.database.music().request(request.id)!!.downloadId)
    }
    @Test fun offlineModeDoesNotQueueNativeDownloadsAndSavedAudioIsReused() = runBlocking {
        val coordinator = DownloadCoordinator(app, app.database)
        try { coordinator.enqueueYouTube(track, Settings(offlineOnly = true));fail("Offline download queued") } catch (_: IllegalStateException) { }
        assertTrue(app.database.music().observeDownloads().first().isEmpty())
        app.database.music().insertTrack(Track(track.downloadId, track.title, track.artist, "YouTube", track.durationMs, "/existing.mp3", contentHash = "existing-hash"))
        assertEquals(track.downloadId, coordinator.enqueueYouTube(track, Settings(offlineOnly = true)))
        assertTrue(app.database.music().observeDownloads().first().isEmpty())
        assertTrue(WorkManager.getInstance(app).getWorkInfosByTag("music-download").get().isEmpty())
    }
}
