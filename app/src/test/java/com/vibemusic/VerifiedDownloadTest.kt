package com.vibemusic

import com.vibemusic.downloads.VerifiedDownload
import com.vibemusic.model.DownloadEntry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class VerifiedDownloadTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var partial: File
    private val audio = "authorized-original-audio-bytes"
    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer();server.useHttps(serverCerts.sslSocketFactory(), false);server.start()
        client = OkHttpClient.Builder().sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager).followRedirects(false).build()
        partial = folder.newFile("audio.part")
    }
    @After fun cleanup() { server.shutdown();client.connectionPool.evictAll() }
    private fun entry(etag: String? = null) = DownloadEntry("one", "Original", "Autor", "Álbum", server.url("/audio.wav").toString(), "CC0-1.0", "https://creativecommons.org/publicdomain/zero/1.0/", "Autor", MessageDigest.getInstance("SHA-256").digest(audio.toByteArray()).joinToString("") { "%02x".format(it) }, etag = etag)
    @Test fun savesVerifiedBytesAndReportsProgress() = runBlocking {
        server.enqueue(MockResponse().setBody(audio).setHeader("ETag", "\"v1\""))
        var received = 0L
        VerifiedDownload(client).transfer(entry(), partial) { count, _, _ -> received = count }
        assertEquals(audio, partial.readText());assertEquals(audio.length.toLong(), received)
    }
    @Test fun resumesWithRangeAndIfRangeWhenServerSupportsIt() = runBlocking {
        partial.writeText(audio.take(10))
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 10-${audio.length-1}/${audio.length}").setBody(audio.drop(10)))
        VerifiedDownload(client).transfer(entry("\"v1\""), partial) { _, _, _ -> }
        val request = server.takeRequest()
        assertEquals("bytes=10-", request.getHeader("Range"));assertEquals("\"v1\"", request.getHeader("If-Range"));assertEquals(audio, partial.readText())
    }
    @Test fun restartsCleanlyWhenServerIgnoresRange() = runBlocking {
        partial.writeText("stale-prefix")
        server.enqueue(MockResponse().setBody(audio))
        VerifiedDownload(client).transfer(entry("\"old\""), partial) { _, _, _ -> }
        assertEquals(audio, partial.readText())
    }
    @Test fun discardsBytesWhenIntegrityCheckFails() {
        server.enqueue(MockResponse().setBody("tampered"))
        assertThrows(IllegalStateException::class.java) { runBlocking { VerifiedDownload(client).transfer(entry(), partial) { _, _, _ -> } } }
        assertFalse(partial.exists())
    }
    @Test fun rejectsIncorrectPartialResponse() {
        partial.writeText(audio.take(10))
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-1/2").setBody("ab"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { VerifiedDownload(client).transfer(entry("\"v1\""), partial) { _, _, _ -> } } }
    }
    @Test fun rejectsCleartextTransport() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { VerifiedDownload(client).transfer(entry().copy(url = "http://localhost/audio"), partial) { _, _, _ -> } } }
    }
}
