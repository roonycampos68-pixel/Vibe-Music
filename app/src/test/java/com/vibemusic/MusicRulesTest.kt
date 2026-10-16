package com.vibemusic

import com.vibemusic.model.*
import org.junit.Assert.*
import org.junit.Test

class MusicRulesTest {
    private val track = Track("1", "Luz de medianoche", "Vibe Studio", "Orígenes", 32000, "/music.wav", contentHash = "hash")
    @Test fun searchMatchesWordsAcrossFieldsAndIgnoresCase() {
        assertTrue(MusicRules.matches(track, "LUZ studio"))
        assertFalse(MusicRules.matches(track, "luz desconocido"))
        assertTrue(MusicRules.matches(track, "  "))
    }
    @Test fun durationAndSizeFormattingHandlesUnknownOrLongAudio() {
        assertEquals("0:00", MusicRules.formatTime(-1))
        assertEquals("65:09", MusicRules.formatTime(3909000))
        assertEquals("1.0 MB", MusicRules.formatSize(1048576))
    }
    @Test fun sameDownloadUrlHasStableUniqueIdentity() {
        assertEquals(MusicRules.remoteId("https://example.org/a.mp3"), MusicRules.remoteId("https://example.org/a.mp3"))
        assertNotEquals(MusicRules.remoteId("https://example.org/a.mp3"), MusicRules.remoteId("https://example.org/b.mp3"))
    }
}
