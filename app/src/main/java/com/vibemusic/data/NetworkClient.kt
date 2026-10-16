package com.vibemusic.data

import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {
    const val USER_AGENT = "VibeMusic/1.1 (Android; https://github.com/roonycampos68-pixel/Vibe-Music)"
    fun create(): OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun response(client: OkHttpClient, request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(error)) }
            override fun onResponse(call: Call, response: Response) { continuation.resume(response) { response.close() } }
        })
    }
    suspend fun json(client: OkHttpClient, url: HttpUrl, maxBytes: Int = 2 * 1024 * 1024): String = withContext(Dispatchers.IO) {
        response(client, Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).use { response ->
            check(response.isSuccessful) { when (response.code) {
                401, 403 -> "La fuente no permite el acceso público (HTTP ${response.code})"
                404 -> "No se encontró el catálogo. Puede ser privado o haber cambiado de dirección"
                429 -> "La fuente recibió demasiadas búsquedas. Espera un momento y reintenta"
                else -> "La fuente devolvió HTTP ${response.code}"
            } }
            val body = response.body ?: error("La fuente devolvió una respuesta vacía")
            require(body.contentLength() <= maxBytes) { "La respuesta es demasiado grande" }
            body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream();val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer);if (n < 0) break
                    require(output.size() + n <= maxBytes) { "La respuesta es demasiado grande" }
                    output.write(buffer, 0, n)
                }
                output.toString("UTF-8").removePrefix("\uFEFF")
            }
        }
    }
}
