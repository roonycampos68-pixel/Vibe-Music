package com.vibemusic.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.vibemusic.model.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.UUID

class PlaylistTransferRepository(private val context: Context, private val db: VibeDatabase) {
    suspend fun importFile(uri: Uri): PlaylistImportResult = withContext(Dispatchers.IO) {
        val name = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
            ?.substringBeforeLast('.') ?: "Importada de Spotify"
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream();val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive();val count = input.read(buffer);if (count < 0) break
                require(output.size() + count <= PlaylistFileParser.MAX_BYTES) { "El archivo supera 8 MB" };output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        } ?: error("No se pudo abrir el archivo de playlists")
        importPlaylists(PlaylistFileParser.parse(text, name))
    }
    suspend fun importPlaylists(playlists: List<ImportedPlaylist>): PlaylistImportResult = db.withTransaction {
        val dao = db.music();val tracks = dao.tracks();var matches = 0
        playlists.forEach { imported ->
            val playlist = Playlist(UUID.randomUUID().toString(), imported.name)
            dao.insertPlaylist(playlist)
            imported.songs.forEachIndexed { position, song ->
                val local = MusicMatching.localMatch(song, tracks)
                val request = PlaylistRequest(UUID.randomUUID().toString(), playlist.id, song.title, song.artist, song.album, song.spotifyUri, position, local?.id)
                dao.insertRequest(request)
                if (local != null) { dao.addMember(PlaylistTrack(playlist.id, local.id, position));matches++ }
            }
        }
        PlaylistImportResult(playlists.size, playlists.sumOf { it.songs.size }, matches, playlists.sumOf { it.skipped }, playlists.sumOf { it.duplicates })
    }
    suspend fun exportFile(playlist: Playlist, uri: Uri) = withContext(Dispatchers.IO) {
        val dao = db.music();val requests = dao.requests().filter { it.playlistId == playlist.id }
        val tracks = dao.tracks().associateBy { it.id }
        val rows = requests.map { r -> r.position to listOf(r.spotifyUri.orEmpty(), r.title, r.artist, r.album) } +
            dao.members(playlist.id).filter { m -> requests.none { it.trackId == m.trackId } }.mapNotNull { m -> tracks[m.trackId]?.let { m.position to listOf("", it.title, it.artist, it.album) } }
        fun quote(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
        val text = "\uFEFFTrack URI,Track Name,Artist Name(s),Album Name\r\n" + rows.sortedBy { it.first }.joinToString("\r\n") { it.second.joinToString(",", transform = ::quote) }
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } ?: error("No se pudo guardar el CSV")
    }
}

object PlaylistLinks {
    suspend fun link(db: VibeDatabase, requestId: String, track: Track) = db.withTransaction {
        val dao = db.music();val request = dao.request(requestId) ?: return@withTransaction
        dao.updateRequest(request.copy(trackId = track.id, downloadId = null))
        dao.addMember(PlaylistTrack(request.playlistId, track.id, request.position))
    }
    suspend fun reconcile(db: VibeDatabase, track: Track, downloadId: String? = null) = db.withTransaction {
        db.music().requests().filter { it.trackId == null }.filter { request ->
            (downloadId != null && request.downloadId == downloadId) || (MusicMatching.sameRecording(request.title, request.artist, track.title, track.artist) &&
                db.music().tracks().count { MusicMatching.sameRecording(request.title, request.artist, it.title, it.artist) } == 1)
        }.forEach { link(db, it.id, track) }
    }
}
