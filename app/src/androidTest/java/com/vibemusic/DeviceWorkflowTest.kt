package com.vibemusic

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vibemusic.data.*
import com.vibemusic.playback.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class DeviceWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as VibeApplication
    private val device = UiDevice.getInstance(instrumentation)
    private fun waitUntil(timeout: Long = 20000, predicate: () -> Boolean) {
        val until = System.currentTimeMillis() + timeout
        while (!predicate()) { if (System.currentTimeMillis() > until) error("Se agotó la espera");Thread.sleep(200) }
    }
    @Test fun importMp3PersistPlaylistAndPlayWithScreenOffAndAppInBackground() = runBlocking {
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "vibe-import-${System.currentTimeMillis()}.mp3")
            put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        })!!
        resolver.openOutputStream(uri)!!.use { out -> instrumentation.context.assets.open("import-test.mp3").use { it.copyTo(out) } }
        val track = app.repository.importMusic(uri)
        resolver.delete(uri, null, null)
        assertEquals("Prueba MP3 Vibe", track.title)
        assertTrue(File(track.localPath).exists())
        val playlist = app.repository.createPlaylist("Persistencia Android")
        app.repository.addToPlaylist(playlist.id, track.id)
        app.database.music().toggleFavorite(track.id)
        val independent = VibeDatabase.open(app)
        assertTrue(independent.music().track(track.id)!!.favorite)
        assertEquals(track.id, independent.music().members(playlist.id).single().trackId)
        independent.close()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        val controller = future.get(20, TimeUnit.SECONDS)
        instrumentation.runOnMainSync { controller.setMediaItem(track.toMediaItem());controller.repeatMode = Player.REPEAT_MODE_ONE;controller.prepare();controller.play() }
        var ready = false
        waitUntil { instrumentation.runOnMainSync { ready = controller.isPlaying };ready }
        device.pressHome()
        Thread.sleep(1200)
        instrumentation.runOnMainSync { assertTrue(controller.isPlaying) }
        device.sleep();Thread.sleep(1500)
        instrumentation.runOnMainSync { assertTrue(controller.isPlaying);assertTrue(controller.currentPosition > 0) }
        device.wakeUp()
        device.executeShellCommand("cmd connectivity airplane-mode enable")
        try {
            Thread.sleep(800)
            instrumentation.runOnMainSync { controller.seekTo(0);controller.play() }
            Thread.sleep(1200)
            instrumentation.runOnMainSync { assertTrue(controller.isPlaying) }
        } finally { device.executeShellCommand("cmd connectivity airplane-mode disable") }
        instrumentation.runOnMainSync { controller.pause() }
        scenario.close()
        instrumentation.runOnMainSync { controller.release() }
        app.repository.deletePlaylist(playlist)
        app.repository.deleteTrack(app.database.music().track(track.id)!!)
        assertFalse(File(track.localPath).exists())
    }
}
