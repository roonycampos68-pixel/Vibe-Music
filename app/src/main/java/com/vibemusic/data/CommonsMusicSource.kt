package com.vibemusic.data

import androidx.core.text.HtmlCompat
import com.vibemusic.model.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.math.BigInteger

data class PublicMusicPage(val tracks: List<CatalogTrack>, val nextOffset: Int?)

/** Wikimedia's public API provides explicit file licenses, direct audio URLs and authoritative SHA-1. */
class CommonsMusicSource(private val client: OkHttpClient = NetworkClient.create(), private val endpoint: String = "https://commons.wikimedia.org/w/api.php") {
    suspend fun search(query: String, offset: Int = 0): PublicMusicPage {
        val words = MusicMatching.normalize(query.take(200)).ifBlank { "music" }
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
            .addQueryParameter("generator", "search").addQueryParameter("gsrnamespace", "6").addQueryParameter("gsrlimit", "25")
            .addQueryParameter("gsrsearch", "$words filetype:audio").addQueryParameter("gsroffset", offset.coerceAtLeast(0).toString())
            .addQueryParameter("prop", "imageinfo").addQueryParameter("iiprop", "url|size|sha1|extmetadata|mime|mediatype").build()
        require(url.isHttps) { "La búsqueda requiere HTTPS" }
        return parse(NetworkClient.json(client, url))
    }
    companion object {
        fun parse(json: String): PublicMusicPage {
            val root = try { JSONObject(json) } catch (e: org.json.JSONException) { throw IllegalArgumentException("La fuente no devolvió una respuesta JSON válida", e) }
            require(!root.has("error")) { "La fuente no pudo procesar la búsqueda. Prueba con otro nombre" }
            val pages = root.optJSONObject("query")?.optJSONArray("pages")
            val tracks = mutableListOf<CatalogTrack>()
            if (pages != null) for (i in 0 until pages.length()) {
                val page = pages.optJSONObject(i) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val metadata = info.optJSONObject("extmetadata") ?: continue
                fun field(name: String) = metadata.optJSONObject(name)?.optString("value").orEmpty()
                val mime = info.optString("mime")
                val audioMime = mime in setOf("audio/mpeg", "audio/ogg", "audio/flac", "audio/x-flac", "audio/wav", "audio/x-wav", "audio/mp4", "audio/aac", "audio/opus")
                // MediaWiki can label older Ogg audio as application/ogg; it also
                // uses that MIME for video, so require explicit AUDIO metadata.
                if (!audioMime && !(mime == "application/ogg" && info.optString("mediatype") == "AUDIO")) continue
                val license = licenseCode(field("License"), field("LicenseShortName")) ?: continue
                val url = runCatching { info.getString("url").toHttpUrl() }.getOrNull() ?: continue
                if (!url.isHttps || url.host != "upload.wikimedia.org") continue
                val hash = sha1Hex(info.optString("sha1")) ?: continue
                val size = info.optLong("size", -1)
                if (size <= 0 || size >= 2L * 1024 * 1024 * 1024) continue
                val rawTitle = page.optString("title").removePrefix("File:").replace('_', ' ')
                val title = plain(field("ObjectName")).ifBlank { rawTitle.substringBeforeLast('.') }.take(300)
                if (title.isBlank()) continue
                val artist = plain(field("Artist")).ifBlank { plain(field("Attribution")) }.ifBlank { "Autor indicado en la fuente" }.take(500)
                val sourcePage = "https://commons.wikimedia.org/".toHttpUrl().newBuilder().addPathSegment("wiki").addPathSegment(page.optString("title").replace(' ', '_')).build().toString()
                val licenseUrl = licenseUrl(license)
                val attribution = "$title — $artist · $license · Fuente: $sourcePage"
                tracks.add(CatalogTrack("commons-${page.optLong("pageid")}", title, artist, "Wikimedia Commons", license, licenseUrl, attribution,
                    listOf(CatalogQuality("Original", url.toString(), hash, size, "SHA-1")), sourcePage = sourcePage))
            }
            val next = root.optJSONObject("continue")?.optInt("gsroffset", -1)?.takeIf { it >= 0 }
            return PublicMusicPage(tracks.distinctBy { it.id }, next)
        }
        private fun plain(html: String): String = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
        fun licenseCode(id: String, shortName: String): String? = when (id.lowercase()) {
            "cc-zero", "cc0" -> "CC0-1.0"
            "cc-by-4.0" -> "CC-BY-4.0"
            "cc-by-sa-4.0" -> "CC-BY-SA-4.0"
            "cc-by-3.0" -> "CC-BY-3.0"
            "cc-by-sa-3.0" -> "CC-BY-SA-3.0"
            "cc-by-2.0" -> "CC-BY-2.0"
            "cc-by-sa-2.0" -> "CC-BY-SA-2.0"
            else -> if (id.startsWith("pd") && shortName.equals("Public domain", true)) "PDM-1.0" else null
        }
        private fun licenseUrl(code: String): String = when (code) {
            "CC0-1.0" -> "https://creativecommons.org/publicdomain/zero/1.0/"
            "PDM-1.0" -> "https://creativecommons.org/publicdomain/mark/1.0/"
            else -> "https://creativecommons.org/licenses/" + code.removePrefix("CC-").lowercase().replace(Regex("-(?=[0-9])"), "/") + "/"
        }
        fun sha1Hex(value: String): String? = runCatching {
            if (value.matches(Regex("[a-fA-F0-9]{40}"))) value.lowercase() else {
                require(value.matches(Regex("[a-z0-9]{1,31}")))
                val number = BigInteger(value, 36);require(number.bitLength() <= 160)
                number.toString(16).padStart(40, '0')
            }
        }.getOrNull()
    }
}
