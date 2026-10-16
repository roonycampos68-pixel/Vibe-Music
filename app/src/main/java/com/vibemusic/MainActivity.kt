package com.vibemusic

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.vibemusic.ui.VibeApp
import com.vibemusic.ui.VibeTheme

class MainActivity : ComponentActivity() {
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VibeTheme { VibeApp() } }
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
