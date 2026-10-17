package com.vibemusic.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Short-lived media URLs stay in memory; they are never saved as library tracks. */
data class YouTubeAudioStream(val url: String, val headers: Map<String, String>, val mimeType: String? = null) {
    companion object {
        fun parse(json: String): YouTubeAudioStream {
            val data = JSONObject(json)
            require(!data.optBoolean("is_live") && data.optString("live_status") !in listOf("is_live", "is_upcoming")) { "Elige una grabación disponible" }
            val url = data.optString("url").toHttpUrlOrNull()
            require(url != null && url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.host.endsWith(".googlevideo.com")) { "YouTube no devolvió un audio reproducible" }
            require(data.optString("vcodec", "none") == "none" && data.optString("acodec").let { it.isNotBlank() && it != "none" }) { "No se encontró una pista de audio" }
            val raw = data.optJSONObject("http_headers")
            val headers = listOf("User-Agent", "Accept", "Accept-Language", "Referer", "Origin").mapNotNull { name ->
                raw?.optString(name)?.takeIf { it.isNotBlank() && it.length <= 4096 && '\r' !in it && '\n' !in it }?.let { name to it }
            }.toMap()
            val mimeType = if (data.optString("protocol").startsWith("m3u8")) "application/x-mpegURL" else null
            return YouTubeAudioStream(url.toString(), headers, mimeType)
        }
    }
}
