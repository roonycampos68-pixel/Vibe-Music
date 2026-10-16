package com.vibemusic.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.vibemusic.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class MusicRepository(private val context: Context, val database: VibeDatabase) {
    val dao = database.music()
    val tracks = dao.observeTracks()
    val playlists = dao.observePlaylists()
    val members = dao.observeMembership()
    val downloads = dao.observeDownloads()
    val musicDir = File(context.filesDir, "music").apply { mkdirs() }
    val artDir = File(context.filesDir, "artwork").apply { mkdirs() }

    suspend fun importMusic(uri: Uri): Track = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Mi canción"
        context.contentResolver.openInputStream(uri)?.use { importStream(it, name) } ?: error("No se pudo abrir el archivo")
    }

    suspend fun importStream(input: InputStream, name: String, source: String = "import"): Track = withContext(Dispatchers.IO) {
        val tmp = File(musicDir, "${UUID.randomUUID()}.part")
        try {
            input.use { stream -> tmp.outputStream().use { out -> stream.copyTo(out) } }
            val hash = sha256(tmp)
            dao.byHash(hash)?.let { tmp.delete(); return@withContext it }
            val extension = name.substringAfterLast('.', "audio").lowercase().takeIf { it in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus") } ?: "audio"
            val target = File(musicDir, "$hash.$extension")
            check(tmp.renameTo(target)) { "No se pudo guardar la canción" }
            try {
                val track = metadata(target, hash, name.substringBeforeLast('.'), source)
                dao.insertTrack(track)
                track
            } catch (e: Exception) { target.delete(); throw e }
        } finally { tmp.delete() }
    }

    suspend fun addOriginals(): Int = withContext(Dispatchers.IO) {
        var added = 0
        for ((file, title) in listOf("midnight.wav" to "Luz de medianoche", "orbits.wav" to "Entre órbitas", "tide.wav" to "Marea lenta")) {
            val track = context.assets.open("originals/$file").use { importStream(it, "$title.wav", "original") }
            if (track.source == "original") added++
        }
        added
    }

    fun metadata(file: File, hash: String, fallback: String, source: String, remote: DownloadEntry? = null): Track {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Formato de audio no compatible")
            check(duration > 0) { "El archivo no contiene audio reproducible" }
            val embedded = retriever.embeddedPicture
            var artwork: String? = null
            var color = when (source) { "original" -> when { fallback.contains("órbitas") -> 0xFF514280; fallback.contains("Marea") -> 0xFF1B6961; else -> 0xFF6E4737 }; else -> 0xFF3C5861 }
            if (embedded != null && embedded.size <= 8 * 1024 * 1024) {
                val art = File(artDir, "$hash.jpg");art.writeBytes(embedded);artwork = art.absolutePath
                val bitmap = BitmapFactory.decodeByteArray(embedded, 0, embedded.size, BitmapFactory.Options().apply { inSampleSize = 16 })
                bitmap?.let { color = it.getPixel(it.width / 2, it.height / 2).toLong() or 0xFF000000; it.recycle() }
            }
            return Track(
                id = remote?.id ?: hash,
                title = remote?.title ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: fallback,
                artist = remote?.artist ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank) ?: if (source == "original") "Vibe Studio" else "Artista desconocido",
                album = remote?.album ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf(String::isNotBlank) ?: if (source == "original") "Vibe Originals · Vol. 1" else "Mi música",
                durationMs = duration, localPath = file.absolutePath, artworkPath = artwork, color = color,
                sizeBytes = file.length(), contentHash = hash, source = source, sourceUrl = remote?.url,
                license = remote?.license ?: if (source == "original") "CC0-1.0" else null,
                attribution = remote?.attribution ?: if (source == "original") "Vibe Studio · Composición sintetizada original" else null,
                licenseUrl = remote?.licenseUrl ?: if (source == "original") "https://creativecommons.org/publicdomain/zero/1.0/" else null
            )
        } finally { retriever.release() }
    }

    suspend fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(UUID.randomUUID().toString(), name.trim().take(100).ifBlank { "Mi playlist" })
        dao.insertPlaylist(playlist);return playlist
    }
    suspend fun addToPlaylist(playlistId: String, trackId: String) = database.withTransaction {
        val existing = dao.members(playlistId)
        dao.addMember(PlaylistTrack(playlistId, trackId, (existing.maxOfOrNull { it.position } ?: -1) + 1))
    }
    suspend fun move(playlistId: String, trackId: String, delta: Int) = database.withTransaction {
        val items = dao.members(playlistId).toMutableList()
        val index = items.indexOfFirst { it.trackId == trackId }
        val target = index + delta
        if (index >= 0 && target in items.indices) {
            val moved = items.removeAt(index);items.add(target, moved)
            items.forEachIndexed { position, entry -> dao.position(playlistId, entry.trackId, position) }
        }
    }
    suspend fun setPlaylistArt(playlist: Playlist, uri: Uri) = withContext(Dispatchers.IO) {
        val file = File(artDir, "playlist-${playlist.id}-${UUID.randomUUID()}")
        val tmp = File(artDir, "${file.name}.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } } ?: error("No se pudo abrir la imagen")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true };BitmapFactory.decodeFile(tmp.absolutePath, options)
            require(options.outWidth > 0) { "Imagen no compatible" }
            check(tmp.renameTo(file));dao.updatePlaylist(playlist.copy(artworkPath = file.absolutePath))
            playlist.artworkPath?.let { File(it).delete() }
        } finally { tmp.delete() }
    }
    suspend fun deleteTrack(track: Track) = withContext(Dispatchers.IO) {
        database.withTransaction { dao.deleteTrack(track);dao.deleteDownload(track.id) }
        File(track.localPath).delete();track.artworkPath?.let { File(it).delete() }
    }
    suspend fun deletePlaylist(playlist: Playlist) {
        dao.deletePlaylist(playlist);playlist.artworkPath?.let { File(it).delete() }
    }
    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream -> val buffer = ByteArray(65536);while (true) { val size = stream.read(buffer);if (size < 0) break;digest.update(buffer, 0, size) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
