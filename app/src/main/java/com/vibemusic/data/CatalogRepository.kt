package com.vibemusic.data

import com.vibemusic.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Only explicit download grants with a public license, attribution and checksum are accepted. */
class CatalogRepository {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).followRedirects(false).build()
    suspend fun load(url: String): List<CatalogTrack> = withContext(Dispatchers.IO) {
        require(url.toHttpUrl().isHttps) { "El catálogo debe usar HTTPS" }
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "No se pudo cargar el catálogo (HTTP ${response.code})" }
            val body = response.body ?: error("El catálogo está vacío")
            require(body.contentLength() <= 2 * 1024 * 1024) { "El catálogo es demasiado grande" }
            val bytes = body.byteStream().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (out.size() <= 2 * 1024 * 1024) { val count = input.read(buffer);if (count < 0) break;out.write(buffer, 0, count) }
                out.toByteArray()
            }
            require(bytes.size <= 2 * 1024 * 1024) { "El catálogo es demasiado grande" }
            parse(String(bytes, Charsets.UTF_8))
        }
    }
    companion object {
        fun parse(json: String): List<CatalogTrack> {
            val root = JSONObject(json)
            require(root.getInt("version") == 1) { "Versión de catálogo no compatible" }
            val tracks = root.getJSONArray("tracks")
            require(tracks.length() <= 1000) { "El catálogo supera 1000 canciones" }
            return (0 until tracks.length()).map { i ->
                val item = tracks.getJSONObject(i)
                require(item.getBoolean("downloadAuthorized")) { "La fuente no autoriza la descarga" }
                val license = item.getString("license")
                require(license in MusicRules.allowedLicenses) { "Licencia no compatible: $license" }
                val licenseUrl = item.getString("licenseUrl")
                require(licenseUrl.toHttpUrl().isHttps) { "La licencia debe tener una URL HTTPS" }
                val attribution = item.getString("attribution").also { require(it.isNotBlank()) { "Falta la atribución" } }
                val qualities = item.getJSONArray("qualities")
                require(qualities.length() in 1..8) { "Faltan calidades de audio" }
                CatalogTrack(item.getString("id"), item.getString("title"), item.getString("artist"), item.getString("album"), license, licenseUrl, attribution,
                    (0 until qualities.length()).map { index ->
                        val quality = qualities.getJSONObject(index)
                        val audio = quality.getString("url")
                        require(audio.toHttpUrl().isHttps) { "El audio debe usar HTTPS" }
                        val hash = quality.getString("sha256").lowercase()
                        require(hash.matches(Regex("[a-f0-9]{64}"))) { "Falta una huella SHA-256 válida" }
                        CatalogQuality(quality.getString("label"), audio, hash, quality.optLong("sizeBytes", -1))
                    })
            }.also { result -> require(result.map { it.id }.distinct().size == result.size) { "Identificadores de catálogo duplicados" } }
        }
    }
}
