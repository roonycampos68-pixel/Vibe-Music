package com.vibemusic.downloads

import android.content.Context
import androidx.work.*
import com.vibemusic.data.Settings
import com.vibemusic.data.VibeDatabase
import com.vibemusic.model.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class DownloadCoordinator(private val context: Context, private val database: VibeDatabase) {
    private val work = WorkManager.getInstance(context)
    suspend fun enqueue(track: CatalogTrack, quality: CatalogQuality, settings: Settings, requestId: String? = null): String {
        check(!settings.offlineOnly) { "Desactiva el modo sin conexión para descargar" }
        val id = MusicRules.remoteId(quality.url)
        val dao = database.music()
        val existing = dao.download(id)
        val saved = existing?.savedTrackId?.let { dao.track(it) } ?: dao.track(id) ?: if (quality.hashAlgorithm == "SHA-256") dao.byHash(quality.sha256) else null
        if (saved != null) {
            if (requestId != null) com.vibemusic.data.PlaylistLinks.link(database, requestId, saved)
            return saved.id
        }
        if (requestId != null) dao.request(requestId)?.let { dao.updateRequest(it.copy(downloadId = id)) }
        if (existing?.state in listOf("queued", "downloading")) return id
        dao.saveDownload(existing?.copy(state = "queued", error = null) ?: DownloadEntry(
            id, track.title, track.artist, track.album, quality.url, track.license, track.licenseUrl, track.attribution, quality.sha256, quality.label, totalBytes = quality.sizeBytes, hashAlgorithm = quality.hashAlgorithm
        ))
        schedule(id, settings.wifiOnly)
        return id
    }
    private fun schedule(id: String, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(workDataOf("id" to id, "wifiOnly" to wifiOnly))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("music-download").build()
        work.enqueueUniqueWork("download-$id", ExistingWorkPolicy.REPLACE, request)
    }
    suspend fun pause(id: String) {
        withContext(Dispatchers.IO) {
            database.music().downloadState(id, "paused")
            work.cancelUniqueWork("download-$id").result.get()
        }
    }
    suspend fun resume(id: String, settings: Settings) {
        check(!settings.offlineOnly) { "Desactiva el modo sin conexión para descargar" }
        database.music().downloadState(id, "queued");schedule(id, settings.wifiOnly)
    }
    suspend fun cancel(id: String) = withContext(Dispatchers.IO) {
        pause(id)
        File(context.filesDir, "pending/$id.part").delete()
        database.music().clearDownloadLinks(id)
        database.music().deleteDownload(id)
    }
    suspend fun pauseAll() = withContext(Dispatchers.IO) {
        database.music().observeDownloads().let { flow ->
            flow.first().filter { it.state in listOf("downloading", "queued") }.forEach { pause(it.id) }
        }
    }
}
