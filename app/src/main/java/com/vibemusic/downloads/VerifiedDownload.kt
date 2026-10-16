package com.vibemusic.downloads

import com.vibemusic.data.MusicRepository
import com.vibemusic.model.DownloadEntry
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.FileOutputStream

/** The same transfer path is used by WorkManager and tested with a trusted local TLS server. */
class VerifiedDownload(private val client: OkHttpClient) {
    suspend fun transfer(entry: DownloadEntry, partial: File, onProgress: suspend (Long, Long, String?) -> Unit): String {
        require(entry.hashAlgorithm in setOf("SHA-256", "SHA-1")) { "Algoritmo de integridad no compatible" }
        require(entry.expectedSha256.matches(Regex(if (entry.hashAlgorithm == "SHA-1") "[a-f0-9]{40}" else "[a-f0-9]{64}"))) { "La huella de integridad no es válida" }
        require(entry.url.toHttpUrl().isHttps) { "La descarga debe usar HTTPS" }
        // A process can die after the last byte, before Room records completion.
        // Verify a complete partial first instead of making an unsatisfiable Range request (HTTP 416).
        if (entry.totalBytes > 0 && partial.exists() && partial.length() == entry.totalBytes) {
            try { return verifyFile(entry, partial) } catch (e: IllegalStateException) { partial.delete() }
        }
        val offset = if (entry.etag != null && !entry.etag.startsWith("W/")) partial.length() else 0L
        if (offset == 0L) partial.delete()
        val request = Request.Builder().url(entry.url).header("User-Agent", com.vibemusic.data.NetworkClient.USER_AGENT).apply {
            if (offset > 0) { header("Range", "bytes=$offset-");header("If-Range", entry.etag!!) }
        }.build()
        client.newCall(request).execute().use { response ->
            check(response.code == 200 || response.code == 206) { "Servidor: HTTP ${response.code}" }
            val body = response.body ?: error("La fuente no devolvió audio")
            val resumed = response.code == 206 && offset > 0
            if (response.code == 206) require(resumed && response.header("Content-Range")?.startsWith("bytes $offset-") == true) { "Respuesta parcial inválida" }
            val start = if (resumed) offset else 0L
            val total = body.contentLength().let { if (it < 0) -1 else start + it }
            require(total < MAX_BYTES) { "El archivo supera 2 GB" }
            var count = start
            var updated = 0L
            body.byteStream().use { input ->
                FileOutputStream(partial, resumed).use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        currentCoroutineContext().ensureActive()
                        count += read
                        check(count < MAX_BYTES) { "El archivo supera 2 GB" }
                        output.write(buffer, 0, read)
                        if (System.currentTimeMillis() - updated > 400) { onProgress(count, total, response.header("ETag"));updated = System.currentTimeMillis() }
                    }
                    output.fd.sync()
                }
            }
            onProgress(count, count, response.header("ETag"))
        }
        currentCoroutineContext().ensureActive()
        return verifyFile(entry, partial)
    }
    private suspend fun verifyFile(entry: DownloadEntry, partial: File): String {
        val expected = java.security.MessageDigest.getInstance(entry.hashAlgorithm)
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
        partial.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive();val n = input.read(buffer);if (n < 0) break
                expected.update(buffer, 0, n);sha256.update(buffer, 0, n)
            }
        }
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        if (hex(expected.digest()) != entry.expectedSha256) {
            partial.delete();error("La verificación ${entry.hashAlgorithm} no coincide; archivo descartado")
        }
        return hex(sha256.digest())
    }

    companion object { const val MAX_BYTES = 2L * 1024 * 1024 * 1024 }
}
