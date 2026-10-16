package com.vibemusic

import android.app.Application
import com.vibemusic.data.MusicRepository
import com.vibemusic.data.SettingsStore
import com.vibemusic.data.VibeDatabase

class VibeApplication : Application() {
    val database by lazy { VibeDatabase.open(this) }
    val repository by lazy { MusicRepository(this, database) }
    val settings by lazy { SettingsStore(this) }
}
