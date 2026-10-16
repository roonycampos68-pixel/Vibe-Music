package com.vibemusic.data

import com.vibemusic.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Reads metadata exported by the user; never reads Spotify audio or private endpoints. */
object PlaylistFileParser {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_SONGS = 10000
    fun parse(text: String, fallbackName: String): List<ImportedPlaylist> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "El archivo de playlists supera 8 MB" }
        val clean = text.removePrefix("\uFEFF").trim()
        require(clean.isNotEmpty()) { "El archivo está vacío" }
        val lists = if (clean.startsWith('{') || clean.startsWith('[')) json(clean, fallbackName) else csv(clean, fallbackName)
        require(lists.isNotEmpty() && lists.any { it.songs.isNotEmpty() }) { "No se encontraron canciones. Usa un CSV de Exportify o un JSON de playlists de Spotify" }
        require(lists.sumOf { it.songs.size } <= MAX_SONGS) { "Importa como máximo 10000 canciones por archivo" }
        return lists.filter { it.songs.isNotEmpty() }
    }
    private fun normalized(name: String) = MusicMatching.normalize(name)
    private fun csv(text: String, name: String): List<ImportedPlaylist> {
        val header = text.lineSequence().first()
        val separator = if (header.count { it == ';' } > header.count { it == ',' }) ';' else ','
        val rows = rows(text, separator)
        val columns = rows.first().map(::normalized)
        fun column(vararg aliases: String): Int = columns.indexOfFirst { it in aliases.map(::normalized) }
        val title = column("Track Name", "TrackName", "Song", "Title", "Título", "Nombre de la canción", "Canción")
        val artist = column("Artist Name(s)", "Artist Name", "Artist", "Artists", "Artista", "Artistas")
        val album = column("Album Name", "Album", "Álbum")
        val uri = column("Track URI", "Track URL", "Spotify URI", "Spotify URL", "URL")
        val playlist = column("Playlist Name", "Playlist", "Nombre de playlist")
        require(title >= 0 && artist >= 0) { "El CSV necesita columnas Track Name y Artist Name(s), o Título y Artista" }
        val groups = rows.drop(1).filter { row -> row.any(String::isNotBlank) }.groupBy { row -> row.getOrNull(playlist)?.trim().takeUnless { it.isNullOrBlank() } ?: name }
        return groups.map { (group, data) ->
            var skipped = 0
            val songs = data.mapNotNull { row ->
                val t = row.getOrNull(title).orEmpty().trim();val a = row.getOrNull(artist).orEmpty().trim()
                if (t.isBlank() || a.isBlank()) { skipped++;null } else ImportedSong(t.take(1000), a.take(1000), row.getOrNull(album).orEmpty().take(1000), spotifyUri(row.getOrNull(uri)))
            }
            deduplicate(group, songs, skipped)
        }
    }
    internal fun rows(text: String, separator: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>();var row = mutableListOf<String>();val field = StringBuilder();var quoted = false;var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"');i++ }
                c == '"' -> { require(quoted || field.isEmpty()) { "CSV con comillas inválidas" };quoted = !quoted }
                c == separator && !quoted -> { row.add(field.toString());field.clear() }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString());field.clear();rows.add(row);row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        require(!quoted) { "El CSV tiene un campo entre comillas sin cerrar" }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString());rows.add(row) }
        require(rows.size <= MAX_SONGS + 101) { "El archivo tiene demasiadas filas" }
        return rows
    }
    private fun json(text: String, fallback: String): List<ImportedPlaylist> {
        val root = if (text.startsWith('[')) JSONArray(text) else JSONObject(text).let { obj ->
            obj.optJSONArray("playlists") ?: JSONArray().put(obj)
        }
        return (0 until root.length()).map { index ->
            val obj = root.getJSONObject(index)
            val trackContainer = obj.optJSONObject("tracks")
            require(trackContainer?.optString("next").let { it.isNullOrBlank() || it == "null" }) { "El JSON está paginado e incompleto. Exporta la playlist completa como CSV" }
            val items = obj.optJSONArray("items") ?: obj.optJSONArray("tracks") ?: trackContainer?.optJSONArray("items") ?: error("No se encontraron canciones en el JSON")
            require(items.length() <= MAX_SONGS) { "La playlist supera 10000 canciones" }
            var skipped = 0
            val songs = (0 until items.length()).mapNotNull { j ->
                val item = items.optJSONObject(j)
                val t = item?.optJSONObject("track") ?: item?.takeUnless { it.has("track") }
                if (t == null) { skipped++;null } else {
                    val title = t.optString("trackName").ifBlank { t.optString("name").ifBlank { t.optString("title") } }
                    val artists = t.optJSONArray("artists")
                    val artist = t.optString("artistName").ifBlank { t.optString("artist").ifBlank {
                        if (artists != null) (0 until artists.length()).map { artists.getJSONObject(it).optString("name") }.joinToString("; ") else ""
                    } }
                    val album = t.optString("albumName").ifBlank { t.optJSONObject("album")?.optString("name") ?: "" }
                    if (title.isBlank() || artist.isBlank()) { skipped++;null } else ImportedSong(title.take(1000), artist.take(1000), album.take(1000), spotifyUri(t.optString("trackUri").ifBlank { t.optString("uri") }))
                }
            }
            deduplicate(obj.optString("name").ifBlank { fallback }, songs, skipped)
        }
    }
    private fun spotifyUri(value: String?): String? {
        val input = value?.trim().orEmpty()
        val id = Regex("^(?:spotify:track:|https://open\\.spotify\\.com/(?:intl-[a-z]+/)?track/)([A-Za-z0-9]{22})(?:\\?.*)?$").matchEntire(input)?.groupValues?.get(1)
        return id?.let { "spotify:track:$it" }
    }
    private fun deduplicate(name: String, songs: List<ImportedSong>, skipped: Int): ImportedPlaylist {
        val distinct = songs.distinctBy { it.spotifyUri ?: (normalized(it.title) + "\u0000" + normalized(it.artist)) }
        return ImportedPlaylist(name.trim().take(100).ifBlank { "Importada de Spotify" }, distinct, skipped, songs.size - distinct.size)
    }
}
