package com.vibemusic.downloads

import java.util.Locale

/** The native wrapper reports network failures as YoutubeDLException, not IOException. */
internal data class YouTubeFailure(val message: String, val retryable: Boolean = false) {
    companion object {
        fun from(error: Exception): YouTubeFailure {
            val detail = generateSequence<Throwable>(error) { it.cause }.take(8)
                .joinToString("\n") { it.message.orEmpty() }.lowercase(Locale.ROOT)
            return when {
                listOf("no space left", "not enough space", "disk full").any(detail::contains) ->
                    YouTubeFailure("No hay espacio suficiente. Libera almacenamiento y vuelve a descargar")
                listOf("private video", "video unavailable", "video has been removed", "not available in your country", "sign in to confirm", "login required", "members-only", "age-restricted").any(detail::contains) ->
                    YouTubeFailure("YouTube restringe este vídeo. Elige otro vídeo público disponible")
                listOf("http error 429", "too many requests").any(detail::contains) ->
                    YouTubeFailure("YouTube está limitando las solicitudes. Espera unos minutos antes de reintentar")
                listOf("timed out", "timeout", "connection reset", "connection aborted", "network is unreachable", "network unreachable", "temporary failure in name resolution", "unable to resolve host", "name or service not known", "http error 500", "http error 502", "http error 503", "http error 504", "remote end closed connection").any(detail::contains) ->
                    YouTubeFailure("La conexión con YouTube se interrumpió", retryable = true)
                "http error 403" in detail || "signature" in detail || "javascript challenge" in detail ->
                    YouTubeFailure("YouTube rechazó la descarga. Prueba otro vídeo; el motor puede necesitar una actualización")
                else -> YouTubeFailure("No se pudo descargar o convertir el vídeo. Prueba otro vídeo público o actualiza Vibe")
            }
        }
    }
}
