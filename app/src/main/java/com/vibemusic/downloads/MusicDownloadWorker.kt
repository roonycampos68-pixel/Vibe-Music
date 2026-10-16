package com.vibemusic.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.vibemusic.R
import com.vibemusic.VibeApplication
import com.vibemusic.data.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
        if (inputData.getBoolean("wifiOnly", true) && caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            dao.downloadState(id, "queued", "Esperando una red Wi-Fi")
            return@withContext Result.retry()
        }
        val dir = File(applicationContext.filesDir, "pending").apply { mkdirs() }
        val partial = File(dir, "$id.part")
        val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).followSslRedirects(false).followRedirects(false).build()
        try {
            setForeground(foreground(entry.title))
            dao.downloadState(id, "downloading")
            VerifiedDownload(client).transfer(entry, partial) { bytes, total, etag -> dao.progress(id, bytes, total, etag) }
            val hash = entry.expectedSha256
            val existing = dao.byHash(hash)
            if (existing == null) {
                val target = File(app.repository.musicDir, "$id.audio")
                check(partial.renameTo(target)) { "No se pudo guardar el audio" }
                try {
                    val track = app.repository.metadata(target, hash, entry.title, "download", entry)
                    val inserted = dao.insertTrack(track)
                    if (inserted == -1L && dao.byHash(hash)?.localPath != target.absolutePath) target.delete()
                } catch (e: Exception) { target.delete(); throw e }
            } else partial.delete()
            dao.downloadState(id, "completed")
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: IOException) {
            if (runAttemptCount < 3) {
                dao.downloadState(id, "queued", "Sin conexión. Se reintentará automáticamente")
                Result.retry()
            } else {
                dao.downloadState(id, "failed", "No se pudo conectar. Puedes reintentar la descarga")
                Result.failure()
            }
        }
        catch (e: Exception) {
            dao.downloadState(id, "failed", e.message ?: "No se pudo completar la descarga")
            Result.failure()
        } finally { client.connectionPool.evictAll() }
    }
    private fun foreground(title: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("downloads", "Descargas de música", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "downloads").setContentTitle("Descargando $title").setContentText("Vibe Music · Puedes pausar desde Descargas").setSmallIcon(R.drawable.ic_notification).setOngoing(true).setProgress(0, 0, true).build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(42, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(42, notification)
    }
}
