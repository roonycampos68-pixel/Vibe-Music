package com.vibemusic.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import com.vibemusic.data.YouTubeSearch
import com.vibemusic.data.YouTubeTrack
import com.vibemusic.data.YouTubeWeb
import org.json.JSONObject

/** One visible WebView, without a JavaScript bridge, file access or external intent launches. */
class YouTubeWebSession(
    val view: WebView,
    private val onSelection: (YouTubeTrack?) -> Unit,
    private val onBusy: (Boolean) -> Unit,
    private val onError: (String?) -> Unit,
    private val onPageMode: () -> Unit = {}
) {
    private var page = false
    private var selected: YouTubeTrack? = null
    private var released = false
    private var suspended = false

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        view.setBackgroundColor(android.graphics.Color.BLACK)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // Subframes can load YouTube's advertising and media over HTTPS.
                val allowed = if (request.isForMainFrame) YouTubeWeb.allowsPage(url) else request.url.scheme in listOf("https", "about")
                if (!allowed && request.isForMainFrame) onError("Este enlace sale de YouTube. Vuelve al vídeo para continuar en Vibe.")
                return !allowed
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                onBusy(true);onError(null)
                if (!page && url != YouTubeWeb.BASE_URL && YouTubeWeb.allowsPage(url)) {
                    page = true;onPageMode()
                }
                if (page) updateSelection(url)
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                // YouTube also navigates using history.pushState, without a new document.
                if (page) updateSelection(url)
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (page && url == view.url) { updateSelection(url);updateTitle(view.title);onBusy(false) }
                if (suspended) pauseMedia()
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { onBusy(false);onError("No se pudo cargar YouTube. Comprueba tu conexión y vuelve a intentarlo.") }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) { onBusy(false);onError("YouTube no está disponible (${response.statusCode}). Vuelve a intentarlo.") }
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) { if (page) updateTitle(title) }
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                // Only our local document can report player status. No native API is exposed to JS.
                if (page || message.sourceId() != YouTubeWeb.BASE_URL || !message.message().startsWith(YouTubeWeb.EVENT_PREFIX)) return false
                when (val event = message.message().removePrefix(YouTubeWeb.EVENT_PREFIX)) {
                    "ready" -> { onBusy(false);onError(null);if (suspended) pauseMedia() }
                    "network" -> { onBusy(false);onError("No se pudo cargar el reproductor. Comprueba tu conexión o prueba «Ver página aquí».") }
                    else -> if (event.startsWith("video:")) updateEmbeddedSelection(event.removePrefix("video:"))
                        else event.removePrefix("error:").toIntOrNull()?.let { onBusy(false);onError(YouTubeWeb.playerError(it)) }
                }
                return true
            }
        }
    }

    fun preview(track: YouTubeTrack) {
        check(!released)
        page = false;selected = track;onSelection(track);onError(null);onBusy(true)
        view.loadDataWithBaseURL(YouTubeWeb.BASE_URL, YouTubeWeb.html(track.id), "text/html", "UTF-8", null)
    }

    fun showPage(url: String) {
        check(!released)
        require(YouTubeWeb.allowsPage(url))
        pauseMedia();page = true;onPageMode();onError(null);onBusy(true);updateSelection(url)
        view.loadUrl(url, mapOf("Referer" to YouTubeWeb.BASE_URL))
    }

    private fun updateSelection(url: String) {
        val id = YouTubeSearch.videoId(url)
        if (id == null) { selected = null;onSelection(null) }
        else if (id != selected?.id) {
            selected = YouTubeTrack(id, "Vídeo de YouTube", "YouTube", 0)
            onSelection(selected)
        }
    }

    private fun updateEmbeddedSelection(value: String) {
        val data = runCatching { require(value.length <= 4096);JSONObject(value) }.getOrNull()
        val id = data?.optString("url")?.let(YouTubeSearch::videoId)
        if (id == null) { selected = null;onSelection(null);return }
        if (id == selected?.id) return // Preserve the richer title/artist from native search.
        val seconds = data.optDouble("duration", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0
        selected = YouTubeTrack(id, data.optString("title").trim().take(300).ifBlank { "Vídeo de YouTube" }, "YouTube", (seconds * 1000).toLong())
        onSelection(selected)
    }

    private fun updateTitle(title: String?) {
        val track = selected ?: return
        if (YouTubeSearch.videoId(view.url.orEmpty()) != track.id) return
        val clean = title?.removeSuffix(" - YouTube")?.trim()?.take(300)
        if (!clean.isNullOrBlank() && clean !in listOf("YouTube", "YouTube Music")) {
            selected = track.copy(title = clean);onSelection(selected)
        }
    }

    fun reload() { if (!released) { onBusy(true);onError(null);view.reload() } }
    fun back(): Boolean {
        if (page && !released && view.canGoBack()) {
            // The embed document may be in the history: close rather than render it as a web page.
            val history = view.copyBackForwardList()
            if (history.getItemAtIndex(history.currentIndex - 1)?.url == YouTubeWeb.BASE_URL) return false
            view.goBack();return true
        }
        return false
    }
    private fun pauseMedia() {
        if (!released) view.evaluateJavascript("if(window.vibePause){window.vibePause();}document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null)
    }
    fun pause() { if (!released) { suspended = true;pauseMedia();view.onPause() } }
    fun resume() { if (!released) { suspended = false;view.onResume() } }
    fun release() {
        if (released) return
        pause();released = true
        view.stopLoading();view.webChromeClient = null;view.webViewClient = WebViewClient()
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.loadUrl("about:blank");view.removeAllViews();view.destroy()
    }
}
