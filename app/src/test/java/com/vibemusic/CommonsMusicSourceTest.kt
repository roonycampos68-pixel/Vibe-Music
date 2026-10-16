package com.vibemusic

import com.vibemusic.data.CommonsMusicSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.math.BigInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class CommonsMusicSourceTest {
    private val hash = "abcdef0123456789abcdef0123456789abcdef01"
    private fun fixture(): JSONObject = JSONObject("""{"continue":{"gsroffset":25},"query":{"pages":[{"pageid":123,"title":"File:Nocturne.ogg","imageinfo":[{"mime":"audio/ogg","url":"https://upload.wikimedia.org/wikipedia/commons/a/ab/Nocturne.ogg","size":2048,"sha1":"${BigInteger(hash, 16).toString(36)}","extmetadata":{"ObjectName":{"value":"Nocturne"},"Artist":{"value":"<a href='https://commons.wikimedia.org/'>Artista original</a>"},"License":{"value":"cc-by-sa-4.0"},"LicenseShortName":{"value":"CC BY-SA 4.0"}}}]}]}}""")
    private fun info(root: JSONObject) = root.getJSONObject("query").getJSONArray("pages").getJSONObject(0).getJSONArray("imageinfo").getJSONObject(0)
    @Test fun extractsPublicAudioLicenseAttributionAndAuthoritativeChecksum() {
        val result = CommonsMusicSource.parse(fixture().toString());val track = result.tracks.single()
        assertEquals("Artista original", track.artist);assertEquals("CC-BY-SA-4.0", track.license)
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", track.licenseUrl)
        assertEquals(hash, track.qualities.single().sha256);assertEquals("SHA-1", track.qualities.single().hashAlgorithm)
        assertEquals(25, result.nextOffset);assertTrue(track.attribution.contains("commons.wikimedia.org"))
    }
    @Test fun excludesMissingLicensesRestrictedLicensesAndNonAudio() {
        val variants = listOf(fixture().also { info(it).getJSONObject("extmetadata").remove("License") }, fixture().also { info(it).getJSONObject("extmetadata").getJSONObject("License").put("value", "cc-by-nc-4.0") }, fixture().also { info(it).put("mime", "video/webm") })
        variants.forEach { assertTrue(CommonsMusicSource.parse(it.toString()).tracks.isEmpty()) }
    }
    @Test fun rejectsCleartextForeignHostsAndMissingChecksums() {
        val variants = listOf(fixture().also { info(it).put("url", "http://upload.wikimedia.org/a.ogg") }, fixture().also { info(it).put("url", "https://unverified.example/a.ogg") }, fixture().also { info(it).remove("sha1") })
        variants.forEach { assertTrue(CommonsMusicSource.parse(it.toString()).tracks.isEmpty()) }
    }
    @Test fun handlesEmptyResultsAndApiErrors() {
        assertTrue(CommonsMusicSource.parse("{\"batchcomplete\":true}").tracks.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { CommonsMusicSource.parse("{\"error\":{\"code\":\"ratelimited\"}}") }
        assertThrows(IllegalArgumentException::class.java) { CommonsMusicSource.parse("<html>not json</html>") }
    }
    @Test fun supportsHexAndBase36DigestsWithoutGuessingMalformedValues() {
        assertEquals(hash, CommonsMusicSource.sha1Hex(hash));assertEquals(hash, CommonsMusicSource.sha1Hex(BigInteger(hash, 16).toString(36)))
        assertNull(CommonsMusicSource.sha1Hex("not a checksum"));assertNull(CommonsMusicSource.sha1Hex("z".repeat(31)))
    }
    @Test fun acceptsExplicitPublicDomainAndKeepsUnknownCopyrightUnlisted() {
        assertEquals("PDM-1.0", CommonsMusicSource.licenseCode("pd-old", "Public domain"))
        assertNull(CommonsMusicSource.licenseCode("unknown", "Public domain"))
    }
    @Test fun acceptsLegacyOggOnlyWhenMediaWikiIdentifiesItAsAudio() {
        val audio = fixture().also { info(it).put("mime", "application/ogg").put("mediatype", "AUDIO") }
        assertEquals(1, CommonsMusicSource.parse(audio.toString()).tracks.size)
        info(audio).put("mediatype", "VIDEO")
        assertTrue(CommonsMusicSource.parse(audio.toString()).tracks.isEmpty())
        info(audio).remove("mediatype")
        assertTrue(CommonsMusicSource.parse(audio.toString()).tracks.isEmpty())
    }
}
