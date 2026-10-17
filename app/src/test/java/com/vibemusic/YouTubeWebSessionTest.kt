package com.vibemusic

import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.vibemusic.data.YouTubeSearch
import com.vibemusic.data.YouTubeTrack
import com.vibemusic.data.YouTubeWeb
import com.vibemusic.ui.YouTubeWebSession
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Verifies native routing and WebView configuration; does not execute YouTube or play media. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class YouTubeWebSessionTest {
    private lateinit var web: WebView
    private lateinit var session: YouTubeWebSession
    private var selected: YouTubeTrack? = null
    private var busy = false
    private var error: String? = null
    private val track = YouTubeTrack("dQw4w9WgXcQ", "Prueba", "Artista", 32000)
    @Before fun setup() {
        web = WebView(ApplicationProvider.getApplicationContext())
        session = YouTubeWebSession(web, { selected = it }, { busy = it }, { error = it })
        session.configure()
    }
    private fun request(url: String, main: Boolean = true) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }
    @Test fun officialEmbedHasAnAppOriginAndKeepsPlaybackUnderUserControl() {
        session.preview(track)
        val loaded = Shadows.shadowOf(web).lastLoadDataWithBaseURL
        assertEquals(YouTubeWeb.BASE_URL, loaded.baseUrl)
        assertTrue(loaded.data.contains("videoId:'${track.id}'"))
        assertTrue(loaded.data.contains("origin:'${YouTubeWeb.ORIGIN}'"))
        assertTrue(loaded.data.contains("autoplay:0"))
        assertTrue(web.settings.mediaPlaybackRequiresUserGesture)
        assertEquals(track, selected);assertTrue(busy)
        assertThrows(IllegalArgumentException::class.java) { YouTubeWeb.html("');alert(1)") }
        assertThrows(IllegalArgumentException::class.java) { YouTubeWeb.thumbnail("../../local") }
    }
    @Test fun webContentCannotNavigateToFilesExternalAppsOrUnrelatedSites() {
        assertFalse(web.settings.allowFileAccess);assertFalse(web.settings.allowContentAccess)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.settings.mixedContentMode)
        val client = web.webViewClient
        listOf("file:///data/private", "content://com.vibemusic/private", "intent://watch?v=dQw4w9WgXcQ", "https://youtube.com.evil.test/watch", "https://www.youtube.com@evil.test/", "https://www.youtube.com:444/watch", "http://www.youtube.com/watch").forEach {
            assertTrue(it, client.shouldOverrideUrlLoading(web, request(it)))
        }
        assertFalse(client.shouldOverrideUrlLoading(web, request(track.url)))
        assertFalse(client.shouldOverrideUrlLoading(web, request("https://consent.youtube.com/m")))
        assertFalse(client.shouldOverrideUrlLoading(web, request("https://ads.example.test/frame", main = false)))
        assertTrue(client.shouldOverrideUrlLoading(web, request("file:///private", main = false)))
    }
    @Test fun pageSearchHasNoDownloadSelectionUntilAVideoIsOpenedAndTracksSpaNavigation() {
        session.preview(track)
        session.showPage(YouTubeSearch.url("Muse & Beyoncé").toString())
        assertNull(selected)
        assertEquals(YouTubeWeb.BASE_URL, Shadows.shadowOf(web).lastAdditionalHttpHeaders["Referer"])
        val next = YouTubeSearch.videoUrl("7Y-xGsEPnsU").toString()
        web.loadUrl(next)
        web.webViewClient.doUpdateVisitedHistory(web, next, false)
        web.webChromeClient!!.onReceivedTitle(web, "Nueva canción - YouTube")
        assertEquals("7Y-xGsEPnsU", selected!!.id);assertEquals("Nueva canción", selected!!.title)
        // Late completion of the previous watch page must not select the previous recording.
        web.webViewClient.onPageFinished(web, track.url)
        assertEquals("7Y-xGsEPnsU", selected!!.id)
        val search = YouTubeSearch.url("Otra canción").toString()
        web.loadUrl(search);web.webViewClient.doUpdateVisitedHistory(web, search, false)
        assertNull(selected)
    }
    @Test fun handlesEmbedRestrictionsAndIgnoresMessagesFromOtherPages() {
        session.preview(track)
        val chrome = web.webChromeClient!!
        chrome.onConsoleMessage(ConsoleMessage("${YouTubeWeb.EVENT_PREFIX}ready", "https://untrusted.test/", 1, ConsoleMessage.MessageLevel.LOG))
        assertTrue(busy)
        chrome.onConsoleMessage(ConsoleMessage("${YouTubeWeb.EVENT_PREFIX}error:150", YouTubeWeb.BASE_URL, 1, ConsoleMessage.MessageLevel.LOG))
        assertFalse(busy);assertTrue(error!!.contains("Ver página aquí"))
        session.reload();assertTrue(busy);assertNull(error)
        chrome.onConsoleMessage(ConsoleMessage("${YouTubeWeb.EVENT_PREFIX}ready", YouTubeWeb.BASE_URL, 1, ConsoleMessage.MessageLevel.LOG))
        assertFalse(busy)
    }
    @Test fun navigationFromTheEmbedToAnotherYouTubeVideoUpdatesTheDownloadSelection() {
        var pageMode = false
        session = YouTubeWebSession(web, { selected = it }, { busy = it }, { error = it }, { pageMode = true })
        session.configure();session.preview(track)
        val next = YouTubeSearch.videoUrl("7Y-xGsEPnsU").toString()
        web.loadUrl(next);web.webViewClient.onPageStarted(web, next, null)
        assertTrue(pageMode)
        assertEquals("7Y-xGsEPnsU", selected!!.id)
        assertNotEquals(track.downloadId, selected!!.downloadId)
    }
    @Test fun selectingARelatedVideoInsideTheIframeChangesTheDownloadWithoutTopLevelNavigation() {
        session.preview(track)
        val event = "${YouTubeWeb.EVENT_PREFIX}video:" + org.json.JSONObject().put("url", YouTubeSearch.videoUrl("7Y-xGsEPnsU")).put("title", "Otro vídeo").put("duration", 120.5)
        web.webChromeClient!!.onConsoleMessage(ConsoleMessage(event, YouTubeWeb.BASE_URL, 1, ConsoleMessage.MessageLevel.LOG))
        assertEquals("7Y-xGsEPnsU", selected!!.id);assertEquals("Otro vídeo", selected!!.title);assertEquals(120500, selected!!.durationMs)
        web.webChromeClient!!.onConsoleMessage(ConsoleMessage("${YouTubeWeb.EVENT_PREFIX}video:{\"url\":\"file:///private\"}", YouTubeWeb.BASE_URL, 1, ConsoleMessage.MessageLevel.LOG))
        assertNull(selected)
    }
    @Test fun pausesWhenLeavingAndDestroysTheWebViewWhenClosed() {
        session.preview(track)
        val parent = FrameLayout(web.context).apply { addView(web) }
        val shadow = Shadows.shadowOf(web)
        session.pause()
        assertTrue(shadow.wasOnPauseCalled());assertTrue(shadow.lastEvaluatedJavascript.contains("pause"))
        session.resume();assertTrue(shadow.wasOnResumeCalled())
        session.release();assertTrue(shadow.wasDestroyCalled());assertEquals(0, parent.childCount)
        session.release() // Idempotent even when lifecycle and composition both leave.
        assertThrows(IllegalStateException::class.java) { session.preview(track) }
    }
}
