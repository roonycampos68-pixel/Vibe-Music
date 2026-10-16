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
    suspend fun transfer(entry: DownloadEntry, partial: File, onProgress: suspend (Long, Long, String?) -> Unit) {
        require(entry.url.toHttpUrl().isHttps) { "La descarga debe usar HTTPS" }
        val offset = if (entry.etag != null && !entry.etag.startsWith("W/")) partial.length() else 0L
        if (offset == 0L) partial.delete()
        val request = Request.Builder().url(entry.url).apply {
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
        if (MusicRepository.sha256(partial) != entry.expectedSha256) {
            partial.delete()
            error("La verificación SHA-256 no coincide; archivo descartado")
        }
    }
    companion object { const val MAX_BYTES = 2L * 1024 * 1024 * 1024 }
}
