package com.vibemusic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vibemusic.data.*
import com.vibemusic.model.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DatabaseTest {
    private lateinit var db: VibeDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "test-vibe.db"
    private fun open() = Room.databaseBuilder(context, VibeDatabase::class.java, name).allowMainThreadQueries().build()
    private fun track(id: String) = Track(id, "Canción $id", "Artista", "Álbum", 32000, "/$id.mp3", contentHash = "hash-$id")
    @Before fun setup() { context.deleteDatabase(name);db = open() }
    @After fun cleanup() { db.close();context.deleteDatabase(name) }
    @Test fun favoritesPlaylistsAndOrderSurviveDatabaseReopen() = runTest {
        val repo = MusicRepository(context, db)
        repo.dao.insertTrack(track("a"));repo.dao.insertTrack(track("b"))
        val playlist = repo.createPlaylist("Mi playlist")
        repo.addToPlaylist(playlist.id, "a");repo.addToPlaylist(playlist.id, "b");repo.addToPlaylist(playlist.id, "a")
        repo.move(playlist.id, "b", -1);repo.dao.toggleFavorite("a")
        db.close();db = open()
        assertTrue(db.music().track("a")!!.favorite)
        assertEquals(listOf("b", "a"), db.music().members(playlist.id).map { it.trackId })
        db.music().deleteTrack(track("b"))
        assertEquals(listOf("a"), db.music().members(playlist.id).map { it.trackId })
    }
    @Test fun duplicateAudioIsNotInsertedTwice() = runTest {
        assertTrue(db.music().insertTrack(track("a")) != -1L)
        assertEquals(-1L, db.music().insertTrack(track("a").copy(id = "other-id")))
        assertEquals(1, db.music().tracks().size)
    }
    @Test fun partialDownloadAndPausedStatePersistAcrossReopen() = runTest {
        val entry = DownloadEntry("a", "Audio", "Autor", "Álbum", "https://example.org/a.mp3", "CC0-1.0", "https://creativecommons.org/publicdomain/zero/1.0/", "Autor", "a".repeat(64))
        db.music().saveDownload(entry);db.music().progress("a", 2048, 8192, "\"v1\"");db.music().downloadState("a", "paused")
        db.close();db = open()
        assertEquals("paused", db.music().download("a")!!.state)
        assertEquals(2048L, db.music().download("a")!!.downloadedBytes)
        assertEquals("\"v1\"", db.music().download("a")!!.etag)
    }
}
