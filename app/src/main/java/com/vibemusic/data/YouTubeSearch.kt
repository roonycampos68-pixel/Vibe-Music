package com.vibemusic.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens YouTube's own search and player; Vibe does not scrape or download its media. */
object YouTubeSearch {
    fun videoUrl(id: String): Uri {
        require(id.matches(Regex("[a-zA-Z0-9_-]{11}"))) { "El vídeo no es válido" }
        return Uri.Builder().scheme("https").authority("www.youtube.com").path("/watch").appendQueryParameter("v", id).build()
    }
    fun videoId(value: String): String? = runCatching {
        val uri = Uri.parse(value)
        if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return@runCatching null
        val id = when (uri.host) {
            "youtu.be" -> uri.pathSegments.singleOrNull()
            "www.youtube.com", "youtube.com", "m.youtube.com", "music.youtube.com" -> when {
                uri.path == "/watch" -> uri.getQueryParameter("v")
                uri.pathSegments.size == 2 && uri.pathSegments.first() in listOf("shorts", "embed", "live") -> uri.pathSegments.last()
                else -> null
            }
            else -> null
        }
        id?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{11}")) }
    }.getOrNull()
    fun url(query: String): Uri {
        val term = query.trim()
        require(term.isNotEmpty()) { "Escribe una canción o un artista" }
        require(term.length <= 500) { "La búsqueda es demasiado larga" }
        return Uri.Builder().scheme("https").authority("www.youtube.com").path("/results")
            .appendQueryParameter("search_query", term).build()
    }

    fun open(context: Context, query: String): Boolean {
        return openUri(context, url(query))
    }
    fun watch(context: Context, id: String): Boolean = openUri(context, videoUrl(id))
    private fun openUri(context: Context, uri: Uri): Boolean {
        fun launch(packageName: String?): Boolean = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                if (packageName != null) setPackage(packageName)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: ActivityNotFoundException) { false }
          catch (_: SecurityException) { false }
        return launch("com.google.android.youtube") || launch(null)
    }
}
