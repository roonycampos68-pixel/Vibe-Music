package com.vibemusic

import com.vibemusic.data.PlaylistFileParser
import com.vibemusic.model.MusicMatching
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PlaylistFileParserTest {
    @Test fun importsExportifyWithBomQuotedCommasAndEscapedQuotes() {
        val text = "\uFEFFTrack URI,Track Name,Artist Name(s),Album Name\r\nspotify:track:0123456789012345678901,\"Una, canción\",\"Autor; Otra autora\",\"Álbum \"\"uno\"\"\"\r\n"
        val result = PlaylistFileParser.parse(text, "Mi lista").single()
        assertEquals("Mi lista", result.name)
        assertEquals("Una, canción", result.songs.single().title)
        assertEquals("Álbum \"uno\"", result.songs.single().album)
        assertEquals("spotify:track:0123456789012345678901", result.songs.single().spotifyUri)
    }
    @Test fun acceptsSemicolonAndMultilineQuotedFields() {
        val result = PlaylistFileParser.parse("Track Name;Artist Name(s);Album Name\n\"Primera\nSegunda\";Autor;Álbum", "Lista").single()
        assertEquals("Primera\nSegunda", result.songs.single().title)
    }
    @Test fun importsOfficialSpotifyAccountDataAndPreservesOrder() {
        val text = """{"playlists":[{"name":"Viaje","items":[{"track":{"trackName":"Uno","artistName":"Artista","albumName":"Disco","trackUri":"spotify:track:0123456789012345678901"}},{"track":null},{"track":{"trackName":"Dos","artistName":"Otra","albumName":"Dos"}}]}]}"""
        val list = PlaylistFileParser.parse(text, "Archivo").single()
        assertEquals(listOf("Uno", "Dos"), list.songs.map { it.title });assertEquals(1, list.skipped)
    }
    @Test fun importsCompleteWebApiJsonAndRejectsIncompletePagination() {
        val complete = """{"name":"Lista","tracks":{"next":null,"items":[{"track":{"name":"Uno","artists":[{"name":"Autor"}],"album":{"name":"Disco"}}}]}}"""
        assertEquals("Autor", PlaylistFileParser.parse(complete, "").single().songs.single().artist)
        assertThrows(IllegalArgumentException::class.java) { PlaylistFileParser.parse(complete.replace("\"next\":null", "\"next\":\"https://api.spotify.com/page2\""), "") }
    }
    @Test fun reportsDuplicatesAndRowsWithoutMetadata() {
        val text = "Track Name,Artist Name(s)\nUno,Autor\nUno,Autor\n,Autor"
        val list = PlaylistFileParser.parse(text, "Lista").single()
        assertEquals(1, list.songs.size);assertEquals(1, list.duplicates);assertEquals(1, list.skipped)
    }
    @Test fun rejectsEmptyMalformedAndUnsupportedFiles() {
        listOf("", "Spotify no es un archivo CSV", "Track Name,Artist Name(s)\n\"sin cerrar,Autor", "Track Name,Artist Name(s)\n,Autor").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { PlaylistFileParser.parse(text, "Lista") }
        }
    }
    @Test fun splitsMultiplePlaylistsAndUnderstandsSpanishHeaders() {
        val text = "Playlist,Título,Artista\nUno,Primera,Autor\nDos,Segunda,Autora"
        assertEquals(listOf("Uno", "Dos"), PlaylistFileParser.parse(text, "").map { it.name })
    }
    @Test fun acceptsSpotifyTrackLinksButDoesNotStoreArbitraryLinks() {
        val text = "Track Name,Artist,Track URL\nUno,Autor,https://open.spotify.com/track/0123456789012345678901?si=public\nDos,Autor,javascript:alert(1)"
        val songs = PlaylistFileParser.parse(text, "").single().songs
        assertEquals("spotify:track:0123456789012345678901", songs.first().spotifyUri);assertNull(songs.last().spotifyUri)
    }
    @Test fun normalizationHandlesAccentsAndCaseWithoutReplacingLiveVersions() {
        assertTrue(MusicMatching.matchesQuery("cancion AUTOR", "Canción", "Autor"))
        assertTrue(MusicMatching.sameRecording("Canción", "El Autor", "cancion", "EL AUTOR"))
        assertFalse(MusicMatching.sameRecording("Canción (Live)", "Autor", "Canción", "Autor"))
    }
}
