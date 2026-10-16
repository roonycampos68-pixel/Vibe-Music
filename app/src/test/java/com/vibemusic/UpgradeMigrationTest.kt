package com.vibemusic

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vibemusic.data.VibeDatabase
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class UpgradeMigrationTest {
    @Test fun upgradesRealVersionOneSchemaWithoutLosingLibraryFavoritesPlaylistOrDownload() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "upgrade.db";context.deleteDatabase(name)
        val path = context.getDatabasePath(name);path.parentFile!!.mkdirs()
        val schema = JSONObject(File("schemas/com.vibemusic.data.VibeDatabase/1.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) { val entity = entities.getJSONObject(i);old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")));val indexes = entity.getJSONArray("indices");for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))) }
            val setup = schema.getJSONArray("setupQueries");for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.insertOrThrow("tracks", null, ContentValues().apply {
                put("id", "kept");put("title", "Mi archivo");put("artist", "Mi artista");put("album", "Mi álbum");put("durationMs", 32000L);put("localPath", "/music/kept.mp3");put("color", 0xFF123456L);put("sizeBytes", 1024L);put("contentHash", "oldhash");put("source", "import");put("addedAt", 1L);put("lastPlayedAt", 2L);put("playCount", 3);put("favorite", 1)
            })
            old.insertOrThrow("playlists", null, ContentValues().apply { put("id", "list");put("name", "Mi lista");put("createdAt", 1L) })
            old.insertOrThrow("playlist_tracks", null, ContentValues().apply { put("playlistId", "list");put("trackId", "kept");put("position", 4) })
            old.insertOrThrow("downloads", null, ContentValues().apply {
                put("id", "download");put("title", "Descarga");put("artist", "Autor");put("album", "Álbum");put("url", "https://example.org/a.mp3");put("license", "CC0-1.0");put("licenseUrl", "https://creativecommons.org/publicdomain/zero/1.0/");put("attribution", "Autor");put("expectedSha256", "a".repeat(64));put("quality", "Original");put("state", "paused");put("downloadedBytes", 123L);put("totalBytes", 999L);put("addedAt", 1L)
            })
            old.version = 1
        }
        val db = Room.databaseBuilder(context, VibeDatabase::class.java, name).addMigrations(VibeDatabase.MIGRATION_1_2, VibeDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            val track = db.music().track("kept")!!
            assertTrue(track.favorite);assertEquals(3, track.playCount);assertEquals("/music/kept.mp3", track.localPath)
            assertEquals(4, db.music().members("list").single().position)
            val download = db.music().download("download")!!
            assertEquals("paused", download.state);assertEquals(123L, download.downloadedBytes);assertEquals("SHA-256", download.hashAlgorithm)
            assertNull(download.savedTrackId)
            assertEquals("catalog", download.provider);assertNull(download.progressPercent)
            assertTrue(db.music().requests().isEmpty())
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun upgradesVersionTwoAndPreservesPendingSpotifyEntries() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "upgrade-v2.db";context.deleteDatabase(name)
        val path = context.getDatabasePath(name);path.parentFile!!.mkdirs()
        val schema = JSONObject(File("schemas/com.vibemusic.data.VibeDatabase/2.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i);val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.getJSONArray("indices");for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries");for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO playlists (id,name,createdAt) VALUES ('list','Spotify',1)")
            old.execSQL("INSERT INTO playlist_requests (id,playlistId,title,artist,album,position) VALUES ('pending','list','Mi canción','Mi artista','Mi álbum',7)")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, VibeDatabase::class.java, name).addMigrations(VibeDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            val pending = db.music().requests().single()
            assertEquals("Mi canción", pending.title);assertEquals(7, pending.position);assertNull(pending.trackId)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
