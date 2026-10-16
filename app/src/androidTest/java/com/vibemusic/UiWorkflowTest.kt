package com.vibemusic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class UiWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun originalAudioLibraryAndPlaylistCanBeUsedFromTheInterface() {
        compose.onNodeWithText("Añadir a mi biblioteca").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Vibe Originals · Vol. 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tu biblioteca").performClick()
        compose.onNodeWithContentDescription("Crear playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Noche tranquila")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Noche tranquila").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Noche tranquila").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Añadir canciones a playlist").performClick()
        compose.onNodeWithText("Luz de medianoche").performClick()
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithText("Luz de medianoche").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reproducir colección").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Pausar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Buscar").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("medianoche")
        compose.onAllNodesWithText("Luz de medianoche").assertCountEquals(2)
    }
}
