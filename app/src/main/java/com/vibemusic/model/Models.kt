package com.vibemusic.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks", indices = [Index(value = ["contentHash"], unique = true)])
data class Track(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val localPath: String,
    val artworkPath: String? = null,
    val color: Long = 0xFF3C5861,
    val sizeBytes: Long = 0,
    val contentHash: String,
    val source: String = "import",
    val sourceUrl: String? = null,
    val license: String? = null,
    val attribution: String? = null,
    val licenseUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0,
    val playCount: Int = 0,
    val favorite: Boolean = false
)

@Entity(tableName = "playlists")
data class Playlist(@PrimaryKey val id: String, val name: String, val artworkPath: String? = null, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"], foreignKeys = [
    ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("trackId")])
data class PlaylistTrack(val playlistId: String, val trackId: String, val position: Int)

@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val url: String,
    val license: String,
    val licenseUrl: String,
    val attribution: String,
    val expectedSha256: String,
    val quality: String = "Original",
    val state: String = "queued",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val etag: String? = null,
    val error: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'SHA-256'") val hashAlgorithm: String = "SHA-256",
    val savedTrackId: String? = null,
    @ColumnInfo(defaultValue = "'catalog'") val provider: String = "catalog",
    val progressPercent: Float? = null
)

data class CatalogQuality(val label: String, val url: String, val sha256: String, val sizeBytes: Long, val hashAlgorithm: String = "SHA-256")
data class CatalogTrack(val id: String, val title: String, val artist: String, val album: String, val license: String, val licenseUrl: String, val attribution: String, val qualities: List<CatalogQuality>, val assetPath: String? = null, val sourcePage: String? = null)

object MusicRules {
    val allowedLicenses = setOf("CC0-1.0", "CC-BY-4.0", "CC-BY-SA-4.0", "CC-BY-3.0", "CC-BY-SA-3.0", "CC-BY-2.0", "CC-BY-SA-2.0", "PDM-1.0")
    fun formatTime(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0) / 1000)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
    fun formatSize(bytes: Long): String = if (bytes < 1024 * 1024) "%.0f KB".format(bytes / 1024.0) else "%.1f MB".format(bytes / 1048576.0)
    fun matches(track: Track, query: String): Boolean = MusicMatching.matchesQuery(query, track.title, track.artist, track.album)
    fun remoteId(url: String): String = "catalog-" + java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
}
