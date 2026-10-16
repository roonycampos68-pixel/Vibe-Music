package com.vibemusic

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.vibemusic.data.YouTubeSearch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class YouTubeSearchTest {
    private class Launcher(val missing: Set<String?> = emptySet(), val denied: Boolean = false) : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val attempts = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            attempts += intent
            if (denied) throw SecurityException("Not allowed")
            if (intent.`package` in missing) throw ActivityNotFoundException()
        }
    }
    @Test fun specialCharactersRemainOneSearchQueryOnTheOfficialSite() {
        val query = "Beyoncé & AC/DC #live + mix"
        val uri = YouTubeSearch.url("  $query  ")
        assertEquals("https", uri.scheme);assertEquals("www.youtube.com", uri.host)
        assertEquals("/results", uri.path);assertEquals(query, uri.getQueryParameter("search_query"))
        assertNull(uri.fragment);assertEquals(setOf("search_query"), uri.queryParameterNames)
    }
    @Test fun rejectsBlankAndOversizedSearches() {
        assertThrows(IllegalArgumentException::class.java) { YouTubeSearch.url("   ") }
        assertThrows(IllegalArgumentException::class.java) { YouTubeSearch.url("x".repeat(501)) }
    }
    @Test fun prefersOfficialYouTubeApp() {
        val launcher = Launcher()
        assertTrue(YouTubeSearch.open(launcher, "Muse Uprising"))
        val intent = launcher.attempts.single()
        assertEquals("com.google.android.youtube", intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
    @Test fun fallsBackToBrowserWhenYouTubeIsUnavailable() {
        val launcher = Launcher(setOf("com.google.android.youtube"))
        assertTrue(YouTubeSearch.open(launcher, "Adele Hello"))
        assertEquals(2, launcher.attempts.size);assertNull(launcher.attempts.last().`package`)
        assertEquals(launcher.attempts.first().data, launcher.attempts.last().data)
    }
    @Test fun reportsMissingHandlersAndSecurityFailuresWithoutCrashing() {
        assertFalse(YouTubeSearch.open(Launcher(setOf("com.google.android.youtube", null)), "song"))
        assertFalse(YouTubeSearch.open(Launcher(denied = true), "song"))
    }
}
