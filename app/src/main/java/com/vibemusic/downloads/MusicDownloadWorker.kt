package com.vibemusic.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import androidx.work.*
import androidx.room.withTransaction
import com.vibemusic.R
import com.vibemusic.VibeApplication
import com.vibemusic.data.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class MusicDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as VibeApplication
        val dao = app.database.music()
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val entry = dao.download(id) ?: return@withContext Result.failure()
        if (entry.state == "paused" || app.settings.settings.first().offlineOnly) return@withContext Result.success()
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        if (app.settings.settings.first().wifiOnly && caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            dao.downloadState(id, "queued", "Esperando una red Wi-Fi")
            return@withContext Result.retry()
        }
        val dir = File(applicationContext.filesDir, "pending").apply { mkdirs() }
        val youtubeDir = File(dir, "youtube-$id")
        var partial = File(dir, "$id.part")
        val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).followSslRedirects(false).followRedirects(false).build()
        try {
            setForeground(foreground(entry.title))
            dao.downloadState(id, "downloading")
            val hash = if (entry.provider == "youtube") {
                dao.conversionProgress(id, null, "Preparando descarga y conversión a MP3")
                partial = com.vibemusic.data.YouTubeMusic(applicationContext).download(entry.url, youtubeDir, "download-$id") { percent, stage ->
                    val current = app.settings.settings.first()
                    if (current.offlineOnly) throw IOException("Modo sin conexión activado")
                    if (current.wifiOnly && manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) throw IOException("Esperando Wi-Fi")
                    dao.conversionProgress(id, percent, stage)
                }
                MusicRepository.sha256(partial)
            } else VerifiedDownload(client).transfer(entry, partial) { bytes, total, etag -> dao.progress(id, bytes, total, etag) }
            val existing = dao.byHash(hash)
            if (existing == null) {
                val target = File(app.repository.musicDir, if (entry.provider == "youtube") "$id.mp3" else "$id.audio")
                check(partial.renameTo(target)) { "No se pudo guardar el audio" }
                try {
                    val track = app.repository.metadata(target, hash, entry.title, "download", entry)
                    val saved = app.database.withTransaction {
                        val inserted = dao.insertTrack(track)
                        val saved = if (inserted == -1L) dao.byHash(hash) ?: error("No se pudo registrar la canción") else track
                        com.vibemusic.data.PlaylistLinks.reconcile(app.database, saved, id)
                        dao.completeDownload(id, saved.id)
                        saved
                    }
                    if (saved.localPath != target.absolutePath) target.delete()
                } catch (e: Exception) {
                    // A cancellation just after committing must not erase registered audio.
                    withContext(NonCancellable) { if (dao.byHash(hash)?.localPath != target.absolutePath) target.delete() }
                    throw e
                }
            } else {
                app.database.withTransaction {
                    com.vibemusic.data.PlaylistLinks.reconcile(app.database, existing, id)
                    dao.completeDownload(id, existing.id)
                }
                partial.delete()
            }
            if (entry.provider == "youtube") youtubeDir.deleteRecursively()
            if (entry.provider == "youtube" && !app.settings.settings.first().offlineOnly) {
                dao.download(id)?.savedTrackId?.let { dao.track(it) }?.let { app.repository.cacheYouTubeArtwork(it) }
            }
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: IOException) {
            if (dao.download(id)?.state == "paused" || app.settings.settings.first().offlineOnly) return@withContext Result.success()
            if (runAttemptCount < 3) {
                dao.downloadState(id, "queued", "Transferencia interrumpida. Se reintentará automáticamente")
                Result.retry()
            } else {
                dao.downloadState(id, "failed", "No se pudo transferir el archivo. Comprueba la conexión y el espacio disponible")
                Result.failure()
            }
        }
        catch (e: Exception) {
            // A late native-process exception must not replace a pause/cancellation.
            if (dao.download(id)?.state == "paused" || app.settings.settings.first().offlineOnly) return@withContext Result.success()
            if (entry.provider == "youtube") {
                val failure = YouTubeFailure.from(e)
                if (failure.retryable && runAttemptCount < 3) {
                    dao.downloadState(id, "queued", "${failure.message}. Se reintentará automáticamente")
                    return@withContext Result.retry()
                }
                dao.downloadState(id, "failed", failure.message)
            } else dao.downloadState(id, "failed", e.message ?: "No se pudo completar la descarga")
            Result.failure()
        } finally { client.connectionPool.evictAll() }
    }
    private fun foreground(title: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("downloads", "Descargas de música", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "downloads").setContentTitle("Descargando $title").setContentText("Vibe Music · Puedes pausar desde Descargas").setSmallIcon(R.drawable.ic_notification).setOngoing(true).setProgress(0, 0, true).build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode() and 0x7fffffff, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id.hashCode() and 0x7fffffff, notification)
    }
}
