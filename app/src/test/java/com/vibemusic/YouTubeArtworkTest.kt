package com.vibemusic

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.vibemusic.data.YouTubeArtwork
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class YouTubeArtworkTest {
    @Test fun savesAValidatedCoverAndReusesItWithoutNetworkForOfflinePlayback() = runBlocking {
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "cover-test")
        val bytes = ByteArrayOutputStream().apply { Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, this) }.toByteArray()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++;assertEquals("https://i.ytimg.com/vi/7Y-xGsEPnsU/hqdefault.jpg", chain.request().url.toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(bytes.toResponseBody()).build()
        }.build()
        try {
            val artwork = YouTubeArtwork(dir, client)
            val saved = artwork.fetch("7Y-xGsEPnsU")
            assertArrayEquals(bytes, saved.readBytes())
            assertEquals(saved, artwork.fetch("7Y-xGsEPnsU"));assertEquals(1, requests)
            assertFalse(File(dir, "youtube-7Y-xGsEPnsU.part").exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun doesNotPersistHtmlErrorsOrOversizedResponsesAsArtwork() = runBlocking {
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "bad-cover-test")
        try {
            for (bytes in listOf("<html>error</html>".toByteArray(), ByteArray(2 * 1024 * 1024 + 1))) {
                val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(bytes.toResponseBody()).build() }.build()
                try { YouTubeArtwork(dir, client).fetch("7Y-xGsEPnsU");fail("Invalid image accepted") } catch (_: IllegalArgumentException) { }
                assertTrue(dir.listFiles().orEmpty().isEmpty())
            }
        } finally { dir.deleteRecursively() }
    }
}
