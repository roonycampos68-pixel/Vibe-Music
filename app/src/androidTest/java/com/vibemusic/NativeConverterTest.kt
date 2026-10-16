package com.vibemusic

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the actual Android Python + yt-dlp + FFmpeg binaries, without Internet or a UI. */
@RunWith(AndroidJUnit4::class)
class NativeConverterTest {
    @Test fun convertsOriginalWavToMp3AndImportsVerifiedPlayableMetadata() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as VibeApplication
        withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "native-converter-test").apply { mkdirs() }
            val source = File(dir, "source.wav")
            val mp3 = File(dir, "converted.mp3")
            try {
                app.assets.open("originals/midnight.wav").use { input -> source.outputStream().use { input.copyTo(it) } }
                YoutubeDL.init(app);FFmpeg.init(app)
                val request = YoutubeDLRequest(Uri.fromFile(source).toString()).apply {
                    // File URLs are enabled only in this offline instrumented fixture.
                    addOption("--enable-file-urls");addOption("--ignore-config")
                    addOption("--extract-audio");addOption("--audio-format", "mp3")
                    addOption("--output", File(dir, "converted.%(ext)s").absolutePath)
                }
                val result = YoutubeDL.execute(request, "native-converter-test")
                assertEquals(0, result.exitCode);assertTrue(mp3.isFile && mp3.length() > 0)
                val track = mp3.inputStream().use { app.repository.importStream(it, "Prueba de conversión.mp3") }
                try {
                    assertTrue(track.durationMs in 31000..34000)
                    assertEquals(64, track.contentHash.length)
                    assertTrue(File(track.localPath).isFile)
                    assertEquals(track.id, mp3.inputStream().use { app.repository.importStream(it, "Duplicado.mp3") }.id)
                } finally { app.repository.deleteTrack(track) }
            } finally { YoutubeDL.destroyProcessById("native-converter-test");dir.deleteRecursively() }
        }
    }
}
