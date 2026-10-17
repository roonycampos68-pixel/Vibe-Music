package com.vibemusic.data

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Bounded, atomic thumbnail storage. Artwork failure must never fail an audio download. */
class YouTubeArtwork(private val directory: File, private val client: OkHttpClient = http) {
    suspend fun fetch(id: String): File = lock.withLock {
        withContext(Dispatchers.IO) {
            val url = YouTubeWeb.thumbnail(id) // Validates the ID before deriving any file name.
            directory.mkdirs()
            val target = File(directory, "youtube-$id.jpg")
            if (target.isFile && target.length() > 0) return@withContext target
            val tmp = File(directory, "youtube-$id.part")
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "Portada no disponible" }
                    val body = response.body ?: error("Portada vacía")
                    require(body.contentLength() <= 2 * 1024 * 1024) { "Portada demasiado grande" }
                    body.byteStream().use { input -> tmp.outputStream().use { out ->
                        val buffer = ByteArray(8192);var total = 0
                        while (true) {
                            val n = input.read(buffer);if (n < 0) break
                            total += n;require(total <= 2 * 1024 * 1024) { "Portada demasiado grande" };out.write(buffer, 0, n)
                        }
                    } }
                }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tmp.absolutePath, options)
                require(options.outWidth in 1..4096 && options.outHeight in 1..4096) { "Portada no válida" }
                check(tmp.renameTo(target)) { "No se pudo guardar la portada" }
                target
            } finally { tmp.delete() }
        }
    }
    companion object {
        private val lock = Mutex()
        private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
}
