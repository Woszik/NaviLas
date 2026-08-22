package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.RestSearchBundle
import pl.navilas.finder.data.cache.BdlSearchSessionCache
import pl.navilas.finder.data.cache.LruTtlCache
import pl.navilas.finder.data.cache.RoadAssessmentCache
import pl.navilas.finder.data.osm.GeocodedPlace
import pl.navilas.finder.data.osm.PersistentLocalityGeocodeStore
import pl.navilas.finder.domain.RoadAssessment
import pl.navilas.finder.domain.RoadSuitability
import java.io.File

class CachePackATest {
    private val emptyBundle = RestSearchBundle(sites = emptyList(), zanocujPolygons = emptyList())

    @Test
    fun lru_evicts_oldest_when_full() {
        val cache = LruTtlCache<String, Int>(maxEntries = 2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a")
        cache.put("c", 3)
        assertNull(cache.get("b"))
        assertEquals(1, cache.get("a"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun lru_ttl_expires_entries() {
        var now = 0L
        val cache = LruTtlCache<String, Int>(maxEntries = 5, ttlMs = 1000L, nowMs = { now })
        cache.put("x", 42)
        now = 1500L
        assertNull(cache.get("x"))
    }

    @Test
    fun bdl_session_cache_rounds_coords_and_respects_ttl() {
        var now = 0L
        val cache = BdlSearchSessionCache(ttlMs = 1000L, nowMs = { now })
        val key = BdlSearchSessionCache.key(52.123456, 21.987654, 25.0, offlineDataVersion = 0L)
        assertEquals(52.123, key.lat, 0.0001)
        assertEquals(21.988, key.lon, 0.0001)
        cache.put(key, emptyBundle)
        assertNotNull(cache.get(key))
        now = 2000L
        assertNull(cache.get(key))
    }

    @Test
    fun bdl_session_cache_different_offline_version() {
        val cache = BdlSearchSessionCache()
        val online = BdlSearchSessionCache.key(52.0, 21.0, 25.0, offlineDataVersion = 0L)
        val offline = BdlSearchSessionCache.key(52.0, 21.0, 25.0, offlineDataVersion = 123L)
        cache.put(online, emptyBundle)
        assertNull(cache.get(offline))
    }

    @Test
    fun road_assessment_cache_hit_and_ttl() {
        var now = 0L
        val cache = RoadAssessmentCache(ttlMs = 1000L, nowMs = { now })
        val assessment = RoadAssessment(
            nearestRoad = null,
            distanceToRoadMeters = 10.0,
            accessClass = null,
            roadSuitability = RoadSuitability.GOOD,
        )
        cache.put("site-1", assessment)
        assertEquals(assessment, cache.get("site-1"))
        now = 2000L
        assertNull(cache.get("site-1"))
    }

    @Test
    fun persistent_locality_store_survives_restart() {
        val dir = File.createTempFile("navilas-cache", "").apply {
            delete()
            mkdirs()
        }
        val file = File(dir, PersistentLocalityGeocodeStore.FILE_NAME)
        val krakow = GeocodedPlace(50.06, 19.94, "Kraków, Polska")
        PersistentLocalityGeocodeStore(file).put("Kraków", krakow)
        val reloaded = PersistentLocalityGeocodeStore(file)
        assertEquals(krakow, reloaded.get("Kraków"))
        assertTrue(file.exists())
        file.delete()
        dir.delete()
    }
}
