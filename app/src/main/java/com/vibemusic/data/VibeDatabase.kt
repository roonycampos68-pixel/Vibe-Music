package com.vibemusic.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vibemusic.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM tracks ORDER BY addedAt DESC") fun observeTracks(): Flow<List<Track>>
    @Query("SELECT * FROM tracks ORDER BY addedAt DESC") suspend fun tracks(): List<Track>
    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1") suspend fun track(id: String): Track?
    @Query("SELECT * FROM tracks WHERE contentHash = :hash LIMIT 1") suspend fun byHash(hash: String): Track?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTrack(track: Track): Long
    @Query("UPDATE tracks SET favorite = NOT favorite WHERE id = :id") suspend fun toggleFavorite(id: String)
    @Query("UPDATE tracks SET artworkPath = :path WHERE id = :id") suspend fun updateArtwork(id: String, path: String)
    @Query("UPDATE tracks SET lastPlayedAt = :time, playCount = playCount + 1 WHERE id = :id") suspend fun markPlayed(id: String, time: Long)
    @Delete suspend fun deleteTrack(track: Track)
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC") fun observePlaylists(): Flow<List<Playlist>>
    @Insert suspend fun insertPlaylist(playlist: Playlist)
    @Update suspend fun updatePlaylist(playlist: Playlist)
    @Query("SELECT * FROM playlists WHERE id = :id") suspend fun playlist(id: String): Playlist?
    @Delete suspend fun deletePlaylist(playlist: Playlist)
    @Query("SELECT * FROM playlist_tracks ORDER BY position") fun observeMembership(): Flow<List<PlaylistTrack>>
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position") suspend fun members(id: String): List<PlaylistTrack>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMember(member: PlaylistTrack)
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId") suspend fun removeMember(playlistId: String, trackId: String)
    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId") suspend fun position(playlistId: String, trackId: String, position: Int)
    @Query("SELECT * FROM playlist_requests ORDER BY position") fun observeRequests(): Flow<List<PlaylistRequest>>
    @Query("SELECT * FROM playlist_requests ORDER BY position") suspend fun requests(): List<PlaylistRequest>
    @Query("SELECT * FROM playlist_requests WHERE id = :id") suspend fun request(id: String): PlaylistRequest?
    @Insert suspend fun insertRequest(request: PlaylistRequest)
    @Update suspend fun updateRequest(request: PlaylistRequest)
    @Query("DELETE FROM playlist_requests WHERE id = :id") suspend fun deleteRequest(id: String)
    @Query("UPDATE playlist_requests SET trackId = NULL WHERE playlistId = :playlistId AND trackId = :trackId") suspend fun unlinkRequest(playlistId: String, trackId: String)
    @Query("DELETE FROM playlist_requests WHERE playlistId = :playlistId AND trackId = :trackId") suspend fun deleteRequestsForTrack(playlistId: String, trackId: String)
    @Query("UPDATE playlist_requests SET downloadId = NULL WHERE downloadId = :id") suspend fun clearDownloadLinks(id: String)
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC") fun observeDownloads(): Flow<List<DownloadEntry>>
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun download(id: String): DownloadEntry?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDownload(download: DownloadEntry)
    @Query("UPDATE downloads SET state = :state, error = :error WHERE id = :id") suspend fun downloadState(id: String, state: String, error: String? = null)
    @Query("UPDATE downloads SET state = 'completed', savedTrackId = :trackId, error = NULL WHERE id = :id") suspend fun completeDownload(id: String, trackId: String)
    @Query("UPDATE downloads SET downloadedBytes = :bytes, totalBytes = :total, etag = :etag WHERE id = :id") suspend fun progress(id: String, bytes: Long, total: Long, etag: String?)
    @Query("UPDATE downloads SET progressPercent = :percent, error = :stage WHERE id = :id AND state = 'downloading'") suspend fun conversionProgress(id: String, percent: Float?, stage: String)
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun deleteDownload(id: String)
    @Query("DELETE FROM downloads WHERE id = :trackId OR savedTrackId = :trackId") suspend fun deleteDownloadsForTrack(trackId: String)
}

@Database(entities = [Track::class, Playlist::class, PlaylistTrack::class, DownloadEntry::class, PlaylistRequest::class], version = 3, exportSchema = true)
abstract class VibeDatabase : RoomDatabase() {
    abstract fun music(): MusicDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN hashAlgorithm TEXT NOT NULL DEFAULT 'SHA-256'")
                db.execSQL("ALTER TABLE downloads ADD COLUMN savedTrackId TEXT")
                db.execSQL("UPDATE downloads SET savedTrackId = id WHERE state = 'completed' AND id IN (SELECT id FROM tracks)")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlist_requests (id TEXT NOT NULL, playlistId TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, spotifyUri TEXT, position INTEGER NOT NULL, trackId TEXT, downloadId TEXT, PRIMARY KEY(id), FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(trackId) REFERENCES tracks(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_requests_playlistId ON playlist_requests(playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_requests_trackId ON playlist_requests(trackId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_requests_downloadId ON playlist_requests(downloadId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN provider TEXT NOT NULL DEFAULT 'catalog'")
                db.execSQL("ALTER TABLE downloads ADD COLUMN progressPercent REAL")
            }
        }
        fun open(context: Context): VibeDatabase = Room.databaseBuilder(context, VibeDatabase::class.java, "vibe-music.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
