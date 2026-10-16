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
import androidx.work.testing.WorkManagerTestInitHelper
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
            // AndroidX caches the first Application in its factory, while Robolectric
            // creates a new Application per test. Reset it before the Activity launches
            // so the UI and assertions use the same Room database in every test.
            org.robolectric.util.ReflectionHelpers.setStaticField(androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
            WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
            // Robolectric cannot bind a real Media3 service. Exercise the app's unavailable-service
            // path here; actual playback must be verified on a device with DeviceWorkflowTest.
            Shadows.shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun createPlaylistAndAddLibraryTrackWhenAudioServiceIsUnavailable() {
        val app = compose.activity.application as VibeApplication
        runBlocking { app.database.music().insertTrack(Track("ui-fixture", "Canción de prueba UI", "Artista de prueba", "Álbum de prueba", 32000, "/ui-fixture.mp3", contentHash = "ui-fixture")) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("No se pudo conectar con el reproductor. Vuelve a abrir Vibe Music.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("Tu biblioteca").performClick()
        compose.onNodeWithText("Playlists").performClick()
        compose.onNodeWithContentDescription("Crear playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Playlist desde interfaz")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(15000) { runBlocking { app.database.music().observePlaylists().first().any { it.name == "Playlist desde interfaz" } } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Playlist desde interfaz"))
        compose.onNodeWithText("Playlist desde interfaz").performClick()
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
    @Test fun importsSpotifyCsvThroughTheDocumentPickerAndDisplaysUnresolvedSongs() {
        val app = compose.activity.application as VibeApplication
        org.junit.Assert.assertSame(app.database, androidx.lifecycle.ViewModelProvider(compose.activity)[com.vibemusic.ui.MusicViewModel::class.java].repo.database)
        compose.waitUntil(10000) { compose.onAllNodesWithText("No se pudo conectar con el reproductor. Vuelve a abrir Vibe Music.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("Tu biblioteca").performClick()
        compose.onNodeWithText("Importar playlist de Spotify").performScrollTo().performClick()
        compose.onNodeWithText("Seleccionar CSV o JSON").performClick()
        val csv = java.io.File(app.cacheDir, "spotify.csv").apply { writeText("Playlist,Track Name,Artist Name(s)\nDesde Spotify,Mi tema,Mi artista") }
        compose.runOnIdle {
            val activity = Shadows.shadowOf(compose.activity)
            val started = activity.nextStartedActivityForResult
            assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
            org.junit.Assert.assertTrue(compose.activity.activityResultRegistry.dispatchResult(started.requestCode, android.app.Activity.RESULT_OK, android.content.Intent().setData(android.net.Uri.fromFile(csv))))
        }
        compose.waitUntil(15000) { runBlocking { app.database.music().requests().any { it.title == "Mi tema" } } }
        // Import summaries are transient messages and may cover the list on a small screen.
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        }
        compose.onNodeWithText("Playlists").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Desde Spotify"))
        compose.onNodeWithText("Desde Spotify").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("1. Mi tema"))
        compose.onNodeWithText("1. Mi tema").assertIsDisplayed()
        compose.onNodeWithContentDescription("Buscar audio para Mi tema").assertExists()
        runBlocking { assertEquals(null, app.database.music().requests().single().trackId) }
    }

    @Test fun downloadableSourceFindsBundledAudioEvenOfflineAndYouTubeIsDisabled() {
        val app = compose.activity.application as VibeApplication
        runBlocking { app.settings.offlineOnly(true) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("No se pudo conectar con el reproductor. Vuelve a abrir Vibe Music.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("Buscar").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("El modo sin conexión está activo. YouTube necesita internet.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("Muse")
        compose.onNodeWithText("Buscar en YouTube").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("Descargables").performScrollTo().performClick()
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.vibemusic.ui.MusicViewModel::class.java]
        compose.waitUntil(10000) { compose.onRoot().fetchSemanticsNode();vm.catalog.value.any { it.title == "Luz de medianoche" } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Luz de medianoche"))
        compose.onNodeWithText("Luz de medianoche").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNode(hasSetTextAction()).performTextInput("LUZ")
        compose.mainClock.advanceTimeBy(1000)
        compose.waitUntil(10000) { compose.onRoot().fetchSemanticsNode();vm.catalog.value.map { it.title } == listOf("Luz de medianoche") }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Luz de medianoche"))
        compose.onNodeWithContentDescription("Guardar Luz de medianoche").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNode(hasSetTextAction()).performTextReplacement("inexistente-1234")
        compose.mainClock.advanceTimeBy(1000)
        compose.waitUntil(10000) { compose.onRoot().fetchSemanticsNode();vm.catalog.value.isEmpty() && !vm.catalogLoading.value }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("No encontramos audio descargable"))
        compose.onNodeWithText("No encontramos audio descargable").assertIsDisplayed()
        compose.onNodeWithText("Importar música").assertExists()
    }

    @Test fun youtubeIsTheDefaultSearchAndItsExternalFallbackDoesNotCreateDownloads() {
        val app = compose.activity.application as VibeApplication
        runBlocking { app.settings.offlineOnly(false) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("No se pudo conectar con el reproductor. Vuelve a abrir Vibe Music.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("Buscar").performClick()
        compose.onNodeWithText("Buscar en YouTube").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Muse Uprising")
        compose.onNodeWithText("Abrir búsqueda en YouTube").performScrollTo().performClick()
        compose.runOnIdle {
            val launched = Shadows.shadowOf(compose.activity).nextStartedActivity
            assertEquals("com.google.android.youtube", launched.`package`)
            assertEquals("Muse Uprising", launched.data!!.getQueryParameter("search_query"))
        }
        runBlocking { org.junit.Assert.assertTrue(app.database.music().observeDownloads().first().isEmpty()) }
        org.junit.Assert.assertTrue(WorkManager.getInstance(app).getWorkInfosByTag("music-download").get().isEmpty())
    }

}
