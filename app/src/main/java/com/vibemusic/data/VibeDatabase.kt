package com.vibemusic.data

import android.content.Context
import androidx.room.*
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
    @Query("UPDATE tracks SET lastPlayedAt = :time, playCount = playCount + 1 WHERE id = :id") suspend fun markPlayed(id: String, time: Long)
    @Delete suspend fun deleteTrack(track: Track)
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC") fun observePlaylists(): Flow<List<Playlist>>
    @Insert suspend fun insertPlaylist(playlist: Playlist)
    @Update suspend fun updatePlaylist(playlist: Playlist)
    @Delete suspend fun deletePlaylist(playlist: Playlist)
    @Query("SELECT * FROM playlist_tracks ORDER BY position") fun observeMembership(): Flow<List<PlaylistTrack>>
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position") suspend fun members(id: String): List<PlaylistTrack>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMember(member: PlaylistTrack)
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId") suspend fun removeMember(playlistId: String, trackId: String)
    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId") suspend fun position(playlistId: String, trackId: String, position: Int)
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC") fun observeDownloads(): Flow<List<DownloadEntry>>
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun download(id: String): DownloadEntry?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDownload(download: DownloadEntry)
    @Query("UPDATE downloads SET state = :state, error = :error WHERE id = :id") suspend fun downloadState(id: String, state: String, error: String? = null)
    @Query("UPDATE downloads SET downloadedBytes = :bytes, totalBytes = :total, etag = :etag WHERE id = :id") suspend fun progress(id: String, bytes: Long, total: Long, etag: String?)
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun deleteDownload(id: String)
}

@Database(entities = [Track::class, Playlist::class, PlaylistTrack::class, DownloadEntry::class], version = 1, exportSchema = true)
abstract class VibeDatabase : RoomDatabase() {
    abstract fun music(): MusicDao
    companion object {
        fun open(context: Context): VibeDatabase = Room.databaseBuilder(context, VibeDatabase::class.java, "vibe-music.db").build()
    }
}
