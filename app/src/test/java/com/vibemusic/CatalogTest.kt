package com.vibemusic

import com.vibemusic.data.CatalogRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class CatalogTest {
    private val valid = """{"version":1,"tracks":[{"id":"one","title":"Original","artist":"Autor","album":"Álbum","downloadAuthorized":true,"license":"CC-BY-4.0","licenseUrl":"https://creativecommons.org/licenses/by/4.0/","attribution":"Autor · Original","qualities":[{"label":"Original","url":"https://music.example.org/a.mp3","sha256":"${"a".repeat(64)}","sizeBytes":1024}]}]}"""
    @Test fun parsesAuthorizedCatalogWithAttributionAndChecksum() {
        val track = CatalogRepository.parse(valid).single()
        assertEquals("Autor · Original", track.attribution)
        assertEquals(1024L, track.qualities.single().sizeBytes)
    }
    @Test fun rejectsMissingDownloadGrant() { assertThrows(IllegalArgumentException::class.java) { CatalogRepository.parse(valid.replace("\"downloadAuthorized\":true", "\"downloadAuthorized\":false")) } }
    @Test fun rejectsUnsupportedLicense() { assertThrows(IllegalArgumentException::class.java) { CatalogRepository.parse(valid.replace("CC-BY-4.0", "All rights reserved")) } }
    @Test fun rejectsUnencryptedAudio() { assertThrows(IllegalArgumentException::class.java) { CatalogRepository.parse(valid.replace("https://music", "http://music")) } }
    @Test fun rejectsInvalidChecksum() { assertThrows(IllegalArgumentException::class.java) { CatalogRepository.parse(valid.replace("a".repeat(64), "abcd")) } }
    @Test fun rejectsDuplicateCatalogIds() {
        val root = org.json.JSONObject(valid);val array = root.getJSONArray("tracks");array.put(array.getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { CatalogRepository.parse(root.toString()) }
    }
}
