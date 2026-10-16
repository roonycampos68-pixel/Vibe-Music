package com.vibemusic

import com.vibemusic.data.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PublicSearchNetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTrust = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTrust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer();server.useHttps(serverTrust.sslSocketFactory(), false);server.start()
        client = OkHttpClient.Builder().sslSocketFactory(clientTrust.sslSocketFactory(), clientTrust.trustManager).followRedirects(false).build()
    }
    @After fun cleanup() { server.shutdown();client.connectionPool.evictAll() }
    @Test fun sendsAnActualQueryAndPaginationToThePublicProvider() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"batchcomplete\":true}"))
        CommonsMusicSource(client, server.url("/w/api.php").toString()).search("Chopín + piano", 25)
        val request = server.takeRequest()
        assertEquals("chopin piano filetype:audio", request.requestUrl!!.queryParameter("gsrsearch"))
        assertEquals("25", request.requestUrl!!.queryParameter("gsroffset"));assertEquals("6", request.requestUrl!!.queryParameter("gsrnamespace"))
        assertTrue(request.getHeader("User-Agent")!!.startsWith("VibeMusic/1.1"))
    }
    @Test fun cancellationStopsAnOutstandingSearchInsteadOfReturningStaleResults() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(Dispatchers.Default) { CommonsMusicSource(client, server.url("/w/api.php").toString()).search("piano") }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        withTimeout(3000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }
    @Test fun reportsAccessFailuresAndRejectsOversizedResponses() {
        server.enqueue(MockResponse().setResponseCode(404))
        val error = assertThrows(IllegalStateException::class.java) { runBlocking { NetworkClient.json(client, server.url("/missing")) } }
        assertTrue(error.message!!.contains("No se encontró"))
        server.enqueue(MockResponse().setBody("12345"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { NetworkClient.json(client, server.url("/large"), 4) } }
    }
    @Test fun bundledMusicAndItsCatalogAreAvailableWithoutGitHubOrInternet() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val tracks = CatalogRepository.bundled(context)
        assertEquals(3, tracks.size)
        tracks.forEach { track ->
            val bytes = context.assets.open(track.assetPath!!).use { it.readBytes() }
            assertEquals("RIFF", String(bytes.copyOfRange(0, 4)))
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(track.qualities.single().sha256, sha)
        }
    }
}
