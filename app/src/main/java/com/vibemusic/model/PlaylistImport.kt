package com.vibemusic.model

import androidx.room.*
import java.text.Normalizer
import java.util.Locale

@Entity(tableName = "playlist_requests", foreignKeys = [
    ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.SET_NULL)
], indices = [Index("playlistId"), Index("trackId"), Index("downloadId")])
data class PlaylistRequest(
    @PrimaryKey val id: String,
    val playlistId: String,
    val title: String,
    val artist: String,
    val album: String,
    val spotifyUri: String?,
    val position: Int,
    val trackId: String? = null,
    val downloadId: String? = null
)

data class ImportedSong(val title: String, val artist: String, val album: String = "", val spotifyUri: String? = null)
data class ImportedPlaylist(val name: String, val songs: List<ImportedSong>, val skipped: Int = 0, val duplicates: Int = 0)
data class PlaylistImportResult(val playlists: Int, val songs: Int, val matched: Int, val skipped: Int, val duplicates: Int)

object MusicMatching {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")
    fun matchesQuery(query: String, vararg fields: String): Boolean {
        val normalized = fields.map(::normalize)
        return normalize(query).split(' ').filter(String::isNotBlank).all { word -> normalized.any { it.contains(word) } }
    }
    // Deliberately keep live/remix/version information. Never substitute another recording silently.
    fun sameRecording(title: String, artist: String, otherTitle: String, otherArtist: String): Boolean =
        normalize(title).isNotBlank() && normalize(artist).isNotBlank() &&
            normalize(title) == normalize(otherTitle) && normalize(artist) == normalize(otherArtist)
    fun localMatch(song: ImportedSong, tracks: List<Track>): Track? = tracks.filter {
        sameRecording(song.title, song.artist, it.title, it.artist)
    }.singleOrNull()
}
