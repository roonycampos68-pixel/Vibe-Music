package com.vibemusic.playback

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** Owns a preview player, including the case where resolution finishes in the background. */
class ForegroundAudioPreview(private val player: Player, private val lifecycle: Lifecycle) : DefaultLifecycleObserver {
    private var released = false
    init { lifecycle.addObserver(this) }
    fun prepare(url: String, mimeType: String? = null) {
        check(!released)
        player.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(mimeType).build());player.prepare()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player.play()
    }
    override fun onPause(owner: LifecycleOwner) { if (!released) player.pause() }
    override fun onStop(owner: LifecycleOwner) { if (!released) player.pause() }
    override fun onDestroy(owner: LifecycleOwner) = release()
    fun release() {
        if (released) return
        released = true;lifecycle.removeObserver(this);player.release()
    }
}
