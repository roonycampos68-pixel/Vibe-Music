package com.vibemusic

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibemusic.playback.ForegroundAudioPreview
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real Android audio decoding/clock and lifecycle; fixture is local, not a YouTube network test. */
@RunWith(AndroidJUnit4::class)
class AudioPreviewPlaybackTest {
    @Test fun playsAudioWithoutWebViewThenPausesWhenLeavingTheForeground() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        lateinit var player: ExoPlayer
        lateinit var preview: ForegroundAudioPreview
        val owner = object : LifecycleOwner { val registry = LifecycleRegistry.createUnsafe(this);override val lifecycle: Lifecycle get() = registry }
        instrumentation.runOnMainSync {
            owner.registry.currentState = Lifecycle.State.RESUMED
            player = ExoPlayer.Builder(instrumentation.targetContext).build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.countDown() }
                override fun onPlayerError(exception: PlaybackException) { error.set(exception.errorCodeName);ready.countDown() }
            })
            preview = ForegroundAudioPreview(player, owner.lifecycle)
            preview.prepare("asset:///originals/midnight.wav")
        }
        try {
            assertTrue("Audio never prepared", ready.await(60, TimeUnit.SECONDS))
            assertNull(error.get())
            var position = 0L
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (position < 1000 && System.nanoTime() < deadline && error.get() == null) {
                Thread.sleep(250)
                instrumentation.runOnMainSync { position = player.currentPosition }
            }
            assertNull(error.get());assertTrue("Audio position did not advance: $position", position >= 1000)
            instrumentation.runOnMainSync {
                assertTrue(player.duration in 31000..34000)
                owner.registry.currentState = Lifecycle.State.CREATED
                assertFalse(player.playWhenReady)
            }
        } finally { instrumentation.runOnMainSync { preview.release();preview.release() } }
    }
}
