package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.navilas.finder.data.osm.GeocodedPlace
import pl.navilas.finder.data.osm.LocalityGeocodeCache

class LocalityGeocodeCacheTest {
    private val krakow = GeocodedPlace(50.06, 19.94, "Kraków, Polska")

    @Test
    fun cache_hit_avoids_repeat_lookup_key() {
        var now = 1_000L
        val cache = LocalityGeocodeCache(nowMs = { now })
        cache.put("Kraków", krakow)
        assertEquals(krakow, cache.get("kraków"))
        assertEquals(krakow, cache.get("  Kraków  "))
    }

    @Test
    fun cache_expires_after_ttl() {
        var now = 0L
        val cache = LocalityGeocodeCache(
            ttlMs = 1000L,
            nowMs = { now },
        )
        cache.put("Kraków", krakow)
        now = 1500L
        assertNull(cache.get("Kraków"))
    }

    @Test
    fun normalize_key_polish_case() {
        val cache = LocalityGeocodeCache()
        assertEquals("łódź", cache.normalizeKey("Łódź"))
    }
}
