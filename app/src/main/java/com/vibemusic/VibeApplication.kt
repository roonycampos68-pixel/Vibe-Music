package com.vibemusic

import android.app.Application
import com.vibemusic.data.MusicRepository
import com.vibemusic.data.SettingsStore
import com.vibemusic.data.VibeDatabase

class VibeApplication : Application() {
    private val audioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    val onlinePlayback = com.vibemusic.playback.OnlinePlaybackState()
    val streamResolver by lazy { com.vibemusic.playback.StreamResolver(audioScope, { com.vibemusic.data.YouTubeMusic(this).resolveAudio(it.url) }) }
    val database by lazy { VibeDatabase.open(this) }
    val repository by lazy { MusicRepository(this, database) }
    val settings by lazy { SettingsStore(this) }
}
