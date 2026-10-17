package com.vibemusic

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibemusic.data.YouTubeTrack
import com.vibemusic.ui.YouTubeWebSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real Android WebView + a local API fixture. No request is sent to YouTube; no media plays. */
@RunWith(AndroidJUnit4::class)
class EmbeddedPlayerTest {
    @Test fun embeddedDocumentReportsReadyAndPausesThroughTheAndroidWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        val paused = AtomicReference<String?>(null)
        val iframeReferer = AtomicReference<String?>(null)
        val selected = AtomicReference<String?>(null)
        val changed = CountDownLatch(1)
        val evaluated = CountDownLatch(1)
        lateinit var session: YouTubeWebSession
        instrumentation.runOnMainSync {
            val web = WebView(instrumentation.targetContext)
            session = YouTubeWebSession(web, { selected.set(it?.id);if (it?.id == "7Y-xGsEPnsU") changed.countDown() }, { if (!it) ready.countDown() }, { if (it != null) { error.set(it);ready.countDown() } })
            session.configure()
            val original = web.webViewClient
            web.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    val iframe = request.url.path?.startsWith("/embed/") == true
                    if (iframe) iframeReferer.set(request.requestHeaders.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value)
                    val source = if (request.url.toString() == "https://www.youtube.com/iframe_api") """
                        window.fixturePaused=false;
                        window.YT={Player:function(id,options){
                          var self=this;
                          this.url='https://www.youtube.com/watch?v='+options.videoId;
                          this.getVideoUrl=function(){return self.url;};
                          this.getVideoData=function(){return {title:'Fixture seleccionada'};};
                          this.getDuration=function(){return 120;};
                          this.pauseVideo=function(){window.fixturePaused=true;};
                          var frame=document.createElement('iframe');
                          frame.onload=function(){
                            options.events.onReady({target:self});
                            self.url='https://www.youtube.com/watch?v=7Y-xGsEPnsU';
                            options.events.onStateChange({target:self});
                          };
                          frame.src='https://www.youtube.com/embed/'+options.videoId;
                          document.getElementById(id).appendChild(frame);
                        }};
                        onYouTubeIframeAPIReady();
                    """.trimIndent() else if (iframe) "<!doctype html><html><body>Fixture local</body></html>" else ""
                    return WebResourceResponse(if (iframe) "text/html" else "application/javascript", "UTF-8", source.byteInputStream())
                }
                override fun onPageFinished(view: WebView, url: String) = original.onPageFinished(view, url)
            }
            session.preview(YouTubeTrack("dQw4w9WgXcQ", "Fixture del reproductor", "Prueba local", 0))
        }
        try {
            assertTrue("The WebView did not report the local player ready", ready.await(60, TimeUnit.SECONDS))
            assertNull(error.get())
            assertEquals(com.vibemusic.data.YouTubeWeb.BASE_URL, iframeReferer.get())
            assertTrue("The related video's ID did not reach the native download selection", changed.await(20, TimeUnit.SECONDS))
            assertEquals("7Y-xGsEPnsU", selected.get())
            instrumentation.runOnMainSync {
                session.pause();session.resume()
                session.view.evaluateJavascript("window.fixturePaused") { paused.set(it);evaluated.countDown() }
            }
            assertTrue(evaluated.await(20, TimeUnit.SECONDS))
            assertEquals("true", paused.get())
        } finally { instrumentation.runOnMainSync { session.release() } }
    }
}
