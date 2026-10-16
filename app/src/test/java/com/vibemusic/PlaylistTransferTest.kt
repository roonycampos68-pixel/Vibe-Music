package com.vibemusic

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vibemusic.data.*
import com.vibemusic.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PlaylistTransferTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: VibeDatabase
    private fun open() = Room.databaseBuilder(context, VibeDatabase::class.java, "transfer-test.db").allowMainThreadQueries().build()
    private fun track(id: String, title: String = "Canción $id") = Track(id, title, "Autor", "Álbum", 32000, "/$id.wav", contentHash = "hash-$id")
    @Before fun setup() { context.deleteDatabase("transfer-test.db");db = open() }
    @After fun cleanup() { db.close();context.deleteDatabase("transfer-test.db") }
    @Test fun importsMatchingAndMissingTracksWithoutDroppingMetadataOnReopen() = runTest {
        db.music().insertTrack(track("a"))
        val result = PlaylistTransferRepository(context, db).importPlaylists(listOf(ImportedPlaylist("Viaje", listOf(ImportedSong("cancion a", "AUTOR"), ImportedSong("Canción b", "Autor")))))
        assertEquals(2, result.songs);assertEquals(1, result.matched)
        db.close();db = open()
        val requests = db.music().requests()
        assertEquals(listOf("cancion a", "Canción b"), requests.map { it.title })
        assertEquals("a", requests.first().trackId);assertNull(requests.last().trackId)
        assertEquals(1, db.music().members(requests.first().playlistId).size)
    }
    @Test fun importingOwnAudioLaterResolvesPendingEntryInItsOriginalPosition() = runTest {
        PlaylistTransferRepository(context, db).importPlaylists(listOf(ImportedPlaylist("Lista", listOf(ImportedSong("Otro", "Autora"), ImportedSong("Canción b", "Autor")))))
        val audio = track("b");db.music().insertTrack(audio);PlaylistLinks.reconcile(db, audio)
        val request = db.music().requests().last()
        assertEquals("b", request.trackId);assertEquals(1, db.music().members(request.playlistId).single().position)
    }
    @Test fun doesNotAutomaticallyChooseBetweenAmbiguousLocalRecordings() = runTest {
        val a = track("a", "Mismo título");val b = track("b", "Mismo título")
        db.music().insertTrack(a);db.music().insertTrack(b)
        PlaylistTransferRepository(context, db).importPlaylists(listOf(ImportedPlaylist("Lista", listOf(ImportedSong(a.title, a.artist)))))
        PlaylistLinks.reconcile(db, b)
        val pending = db.music().requests().single();assertNull(pending.trackId)
        db.music().updateRequest(pending.copy(downloadId = "chosen-download"))
        PlaylistLinks.reconcile(db, a, "chosen-download")
        assertEquals(a.id, db.music().requests().single().trackId)
    }
    @Test fun removingDownloadedAudioRetainsTheRequestedSpotifyTitle() = runTest {
        val audio = track("a");db.music().insertTrack(audio)
        PlaylistTransferRepository(context, db).importPlaylists(listOf(ImportedPlaylist("Lista", listOf(ImportedSong(audio.title, audio.artist)))))
        db.music().deleteTrack(audio)
        assertNull(db.music().requests().single().trackId)
        assertEquals(audio.title, db.music().requests().single().title)
        assertTrue(db.music().members(db.music().requests().single().playlistId).isEmpty())
    }
    @Test fun playlistDeletionCascadesRequestsButPreservesAudio() = runTest {
        val audio = track("a");db.music().insertTrack(audio)
        PlaylistTransferRepository(context, db).importPlaylists(listOf(ImportedPlaylist("Lista", listOf(ImportedSong(audio.title, audio.artist)))))
        db.music().deletePlaylist(db.music().observePlaylists().first().single())
        assertTrue(db.music().requests().isEmpty());assertNotNull(db.music().track(audio.id))
    }
    @Test fun reorderingAndCsvExportPreservePendingSongsAndRealOrder() = runTest {
        db.music().insertTrack(track("a"));db.music().insertTrack(track("c"))
        val transfer = PlaylistTransferRepository(context, db)
        transfer.importPlaylists(listOf(ImportedPlaylist("Lista", listOf(ImportedSong("Canción a", "Autor"), ImportedSong("Falta b", "Autor"), ImportedSong("Canción c", "Autor")))))
        val playlist = db.music().observePlaylists().first().single()
        MusicRepository(context, db).move(playlist.id, "c", -1)
        val file = File(context.cacheDir, "roundtrip.csv")
        transfer.exportFile(playlist, Uri.fromFile(file))
        val songs = PlaylistFileParser.parse(file.readText(), "Lista").single().songs
        assertEquals(listOf("Canción c", "Falta b", "Canción a"), songs.map { it.title })
        assertEquals(listOf("c", "a"), db.music().members(playlist.id).map { it.trackId })
    }
    @Test fun importingCsvViaContentResolverCreatesDurableRequests() = runTest {
        val file = File(context.cacheDir, "import.csv").apply { writeText("Track Name,Artist Name(s)\nCanción,Autor") }
        val result = PlaylistTransferRepository(context, db).importFile(Uri.fromFile(file))
        assertEquals(1, result.songs);assertEquals(0, result.matched);assertEquals("Canción", db.music().requests().single().title)
    }
}
