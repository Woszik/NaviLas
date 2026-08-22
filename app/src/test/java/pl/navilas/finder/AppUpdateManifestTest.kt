package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.update.AppUpdateLogic
import pl.navilas.finder.update.AppUpdateManifest

class AppUpdateManifestTest {
    private fun sampleJson(
        versionCode: Int = 3,
        minVersionCode: Int = 0,
    ): String = """
        {
          "versionCode": $versionCode,
          "versionName": "0.5.1",
          "apkUrl": "https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.1/navilas-0.5.1.apk",
          "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
          "releaseNotes": "Poprawki mapy",
          "minVersionCode": $minVersionCode,
          "minAndroidSdk": 26,
          "publishedAt": "2026-08-22T16:00:00Z"
        }
    """.trimIndent()

    @Test
    fun parse_validManifest() {
        val manifest = AppUpdateManifest.parse(sampleJson())
        assertEquals(3, manifest.versionCode)
        assertEquals("0.5.1", manifest.versionName)
        assertTrue(manifest.apkUrl.contains("navilas-0.5.1.apk"))
        assertEquals(64, manifest.sha256.length)
        assertEquals("Poprawki mapy", manifest.releaseNotes)
    }

    @Test
    fun evaluateOffer_newerVersion_returnsOffer() {
        val manifest = AppUpdateManifest.parse(sampleJson(versionCode = 5))
        val offer = AppUpdateLogic.evaluateOffer(manifest, currentVersionCode = 2, dismissedVersionCode = null)
        assertNotNull(offer)
        assertEquals(5, offer!!.versionCode)
        assertFalse(offer.mandatory)
    }

    @Test
    fun evaluateOffer_sameVersion_returnsNull() {
        val manifest = AppUpdateManifest.parse(sampleJson(versionCode = 2))
        assertNull(AppUpdateLogic.evaluateOffer(manifest, 2, null))
    }

    @Test
    fun evaluateOffer_dismissedNonMandatory_returnsNull() {
        val manifest = AppUpdateManifest.parse(sampleJson(versionCode = 5))
        assertNull(AppUpdateLogic.evaluateOffer(manifest, 2, dismissedVersionCode = 5))
    }

    @Test
    fun evaluateOffer_mandatoryIgnoresDismissed() {
        val manifest = AppUpdateManifest.parse(sampleJson(versionCode = 5, minVersionCode = 4))
        assertTrue(manifest.isMandatory(currentVersionCode = 2))
        val offer = AppUpdateLogic.evaluateOffer(manifest, 2, dismissedVersionCode = 5)
        assertNotNull(offer)
        assertTrue(offer!!.mandatory)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_invalidSha256_throws() {
        val json = JSONObject(sampleJson()).put("sha256", "not-a-hash")
        AppUpdateManifest.parse(json)
    }
}
