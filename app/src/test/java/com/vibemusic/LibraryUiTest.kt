package com.vibemusic

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.vibemusic.model.Track
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.rules.ExternalResource
import androidx.work.WorkManager
import androidx.work.Configuration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import android.content.ComponentName
import com.vibemusic.playback.PlaybackService
import org.robolectric.annotation.Config

/** Exercises Compose interactions and Room; intentionally does not assert audio playback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = VibeApplication::class)
class LibraryUiTest {
    // Robolectric does not run AndroidX Startup's provider as a real process does.
    @get:Rule(order = 0) val workManager = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<VibeApplication>()
            WorkManager.initialize(app, Configuration.Builder().build())
            // Robolectric cannot bind a real Media3 service. Exercise the app's unavailable-service
            // path here; actual playback must be verified on a device with DeviceWorkflowTest.
            Shadows.shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun createPlaylistAndAddLibraryTrackWhenAudioServiceIsUnavailable() {
        val app = ApplicationProvider.getApplicationContext<VibeApplication>()
        runBlocking { app.database.music().insertTrack(Track("ui-fixture", "Canción de prueba UI", "Artista de prueba", "Álbum de prueba", 32000, "/ui-fixture.mp3", contentHash = "ui-fixture")) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("No se pudo conectar con el reproductor. Vuelve a abrir Vibe Music.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("Tu biblioteca").performClick()
        compose.onNodeWithText("Playlists").performClick()
        compose.onNodeWithContentDescription("Crear playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Playlist desde interfaz")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Playlist desde interfaz").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Playlist desde interfaz").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Añadir canciones a playlist").performScrollTo().performClick()
        compose.onNodeWithText("Canción de prueba UI").performClick()
        compose.onNodeWithText("Listo").performClick()
        compose.waitUntil(15000) {
            runBlocking {
                val playlist = app.database.music().observePlaylists().first().first { it.name == "Playlist desde interfaz" }
                app.database.music().members(playlist.id).size == 1
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Canción de prueba UI").assertIsDisplayed()
        runBlocking {
            val playlist = app.database.music().observePlaylists().first().first { it.name == "Playlist desde interfaz" }
            assertEquals("ui-fixture", app.database.music().members(playlist.id).single().trackId)
        }
    }
}
