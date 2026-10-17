package com.vibemusic.data

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

data class YouTubeTrack(val id: String, val title: String, val artist: String, val durationMs: Long) {
    val url: String get() = YouTubeSearch.videoUrl(id).toString()
    val downloadId: String get() = "youtube-$id"
}
data class YouTubePage(val tracks: List<YouTubeTrack>, val nextOffset: Int?)

internal object ConversionWorkspace {
    fun prepare(base: File): File {
        check(base.isDirectory || base.mkdirs()) { "No hay espacio para preparar la descarga" }
        val previous = base.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("attempt-") }.maxByOrNull { it.lastModified() }
        val next = File(base, "attempt-${UUID.randomUUID()}")
        check(next.mkdir()) { "No se pudo preparar la conversión" }
        previous?.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("audio.") && !it.name.contains(".mp3") }.forEach {
            check(it.renameTo(File(next, it.name))) { "No se pudo recuperar el archivo parcial" }
        }
        // Each conversion has a fresh output path. A child encoder finishing after
        // cancellation cannot overwrite the next attempt's MP3.
        base.listFiles().orEmpty().filter { it.isDirectory && it != next }.forEach { it.deleteRecursively() }
        return next
    }
}

object YouTubeMetadata {
    fun parse(json: String, offset: Int = 0): YouTubePage {
        val root = JSONObject(json)
        val entries = root.optJSONArray("entries")
        require(entries != null || root.has("id")) { "YouTube no devolvió resultados válidos" }
        val items = if (entries == null) listOf(root) else (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
        val tracks = items.mapNotNull { entry ->
            val id = entry.optString("id")
            if (!id.matches(Regex("[a-zA-Z0-9_-]{11}"))) return@mapNotNull null
            if (entry.optBoolean("is_live") || entry.optString("live_status") in listOf("is_live", "is_upcoming")) return@mapNotNull null
            if (entry.optString("availability") in listOf("private", "premium_only", "subscriber_only", "needs_auth")) return@mapNotNull null
            val title = entry.optString("title").trim().take(500)
            if (title.isBlank() || title == "[Private video]" || title == "[Deleted video]") return@mapNotNull null
            val artist = entry.optString("channel").ifBlank { entry.optString("uploader") }.ifBlank { "YouTube" }.take(500)
            YouTubeTrack(id, title, artist, (entry.optDouble("duration", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0).times(1000).toLong())
        }.distinctBy { it.id }
        return YouTubePage(tracks, (offset + 20).takeIf { entries != null && entries.length() >= 20 && it < 100 })
    }
}

object YouTubeCommands {
    fun preview(url: String): YoutubeDLRequest {
        val id = YouTubeSearch.videoId(url) ?: error("Selecciona un vídeo público válido de YouTube")
        return base(YouTubeSearch.videoUrl(id).toString()).apply {
            addOption("--no-playlist");addOption("--skip-download");addOption("--dump-single-json")
            addOption("--format", "bestaudio[ext=m4a]/bestaudio")
            addOption("--match-filter", "!is_live")
        }
    }
    fun search(query: String, offset: Int): YoutubeDLRequest {
        require(query.isNotBlank() && query.length <= 500)
        require(offset in 0..80 && offset % 20 == 0)
        val video = YouTubeSearch.videoId(query.trim())
        val target = video?.let { YouTubeSearch.videoUrl(it).toString() } ?: "ytsearch100:${query.trim()}"
        return base(target).apply {
            addOption("--skip-download");addOption("--dump-single-json")
            if (video == null) { addOption("--flat-playlist");addOption("--playlist-start", offset + 1);addOption("--playlist-end", offset + 20) }
            else addOption("--no-playlist")
        }
    }
    fun download(url: String, directory: File): YoutubeDLRequest {
        val id = YouTubeSearch.videoId(url) ?: error("Selecciona un vídeo público válido de YouTube")
        return base(YouTubeSearch.videoUrl(id).toString()).apply {
            addOption("--no-playlist");addOption("--continue");addOption("--newline")
            addOption("--format", "bestaudio/best");addOption("--extract-audio")
            addOption("--audio-format", "mp3");addOption("--audio-quality", "0")
            addOption("--max-filesize", "256M");addOption("--match-filter", "!is_live")
            addOption("--output", File(directory, "audio.%(ext)s").absolutePath)
        }
    }
    private fun base(target: String) = YoutubeDLRequest(target).apply {
        addOption("--ignore-config");addOption("--no-warnings")
        addOption("--socket-timeout", 20);addOption("--retries", 2);addOption("--fragment-retries", 2)
    }
}

/** Python/yt-dlp and FFmpeg run locally, with argv lists rather than a shell or converter website. */
class YouTubeMusic(private val context: Context) {
    suspend fun resolveAudio(url: String): YouTubeAudioStream {
        initialize()
        val processId = "preview-${UUID.randomUUID()}"
        try {
            val result = runInterruptible(Dispatchers.IO) { YoutubeDL.execute(YouTubeCommands.preview(url), processId) }
            return YouTubeAudioStream.parse(result.out)
        } finally { YoutubeDL.destroyProcessById(processId) }
    }
    private suspend fun initialize() = withContext(Dispatchers.IO) {
        YoutubeDL.init(context)
        FFmpeg.init(context)
        installBundledEngine(context)
    }
    suspend fun search(query: String, offset: Int = 0): YouTubePage {
        initialize()
        val processId = "search-${UUID.randomUUID()}"
        try {
            val result = runInterruptible(Dispatchers.IO) { YoutubeDL.execute(YouTubeCommands.search(query, offset), processId) }
            return YouTubeMetadata.parse(result.out, offset)
        } finally { YoutubeDL.destroyProcessById(processId) }
    }
    suspend fun download(url: String, directory: File, processId: String, onProgress: suspend (Float, String) -> Unit): File = downloadSlot.withPermit {
        initialize()
        val attempt = ConversionWorkspace.prepare(directory)
        val mp3 = File(attempt, "audio.mp3")
        // An interrupted conversion is restarted; the original download and .part remain resumable.
        mp3.delete()
        var complete = false
        val callbackFailure = AtomicReference<Exception?>(null)
        try {
            runInterruptible(Dispatchers.IO) {
                try {
                    YoutubeDL.execute(YouTubeCommands.download(url, attempt), processId) { progress, _, line ->
                        try {
                            runBlocking { onProgress(progress.coerceIn(0f, 100f), if (line.contains("ExtractAudio") || line.contains("ffmpeg")) "Convirtiendo a MP3" else "Descargando audio") }
                        } catch (e: Exception) {
                            // The callback runs on the library's stdout thread. Stop the
                            // process and propagate on the caller instead of killing that thread.
                            callbackFailure.compareAndSet(null, e);YoutubeDL.destroyProcessById(processId)
                        }
                    }
                } catch (e: Exception) { throw callbackFailure.get() ?: e }
                callbackFailure.get()?.let { throw it }
            }
            currentCoroutineContext().ensureActive()
            check(mp3.isFile && mp3.length() > 0) { "No se pudo generar el MP3. Reintenta o prueba otro vídeo público" }
            complete = true
            return@withPermit mp3
        } finally { YoutubeDL.destroyProcessById(processId);if (!complete) mp3.delete() }
    }
    companion object {
        private val downloadSlot = Semaphore(1)
        const val ENGINE_VERSION = "2026.08.19"
        const val ENGINE_SHA256 = "1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6"
        @Synchronized private fun installBundledEngine(context: Context) {
            val prefs = context.getSharedPreferences("youtube-engine", Context.MODE_PRIVATE)
            val target = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}")
            if (prefs.getString("bundled-sha256", null) == ENGINE_SHA256 && target.isFile) return
            val temp = File(target.parentFile, "yt-dlp.verified")
            try {
                context.resources.openRawResource(com.vibemusic.R.raw.ytdlp).use { input -> temp.outputStream().use { input.copyTo(it) } }
                check(MusicRepository.sha256(temp) == ENGINE_SHA256) { "El motor de descarga no pasó su verificación" }
                check(temp.renameTo(target)) { "No se pudo preparar el motor de descarga" }
                prefs.edit().putString("bundled-sha256", ENGINE_SHA256).apply()
            } finally { temp.delete() }
        }
    }
}
