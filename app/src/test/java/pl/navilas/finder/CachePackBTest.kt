package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.BdlSearchContext
import pl.navilas.finder.data.bdl.BdlSearchSubsetFilter
import pl.navilas.finder.data.bdl.RestSearchBundle
import pl.navilas.finder.data.bdl.ZanocujPolygon
import pl.navilas.finder.data.cache.OsmRoadTileCache
import pl.navilas.finder.data.cache.OsmRoadTileGrid
import pl.navilas.finder.data.cache.OsmRoadTileKey
import pl.navilas.finder.data.osm.CachingOverpassRoadClient
import pl.navilas.finder.data.osm.OverpassBboxFetcher
import pl.navilas.finder.data.osm.OverpassRoadClient
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.Road
import pl.navilas.finder.domain.ZanocujStatus

class CachePackBTest {
    @Test
    fun tile_grid_key_and_bbox() {
        val key = OsmRoadTileGrid.key(52.12, 21.08)
        assertEquals(1042, key.latIndex)
        assertEquals(421, key.lonIndex)
        val bbox = OsmRoadTileGrid.bbox(key)
        assertEquals(52.10, bbox.south, 0.0001)
        assertEquals(21.05, bbox.west, 0.0001)
        assertEquals(52.15, bbox.north, 0.0001)
        assertEquals(21.10, bbox.east, 0.0001)
    }

    @Test
    fun tile_cache_evicts_by_byte_budget() {
        val cache = OsmRoadTileCache(maxEntries = 10, maxBytes = 500L)
        val roads = listOf(
            Road(
                id = "way/1",
                type = "service",
                access = null,
                motorVehicle = null,
                motorcycle = null,
                vehicle = null,
                name = null,
                geometry = List(20) { LatLon(52.0 + it * 0.001, 21.0) },
            ),
        )
        cache.put(OsmRoadTileKey(1, 1), roads)
        cache.put(OsmRoadTileKey(1, 2), roads)
        assertTrue(cache.size() <= 1)
    }

    @Test
    fun caching_overpass_skips_network_on_tile_hit() {
        var bboxCalls = 0
        val delegate = OverpassBboxFetcher { _, _, _, _ ->
            bboxCalls++
            listOf(sampleRoad("way/cached"))
        }
        val tileCache = OsmRoadTileCache()
        val client = CachingOverpassRoadClient(delegate = delegate, tileCache = tileCache)
        val point = LatLon(52.12, 21.08)
        client.fetchHighwaysAround(listOf(point), radiusMeters = 400.0)
        client.fetchHighwaysAround(listOf(point), radiusMeters = 400.0)
        assertEquals(1, bboxCalls)
    }

    @Test
    fun overpass_bbox_query_format() {
        val q = OverpassRoadClient().buildBboxQuery(52.0, 21.0, 52.05, 21.05)
        assertTrue(q.contains("way[\"highway\"](52.000000,21.000000,52.050000,21.050000)"))
        assertTrue(q.contains("out tags geom"))
    }

    @Test
    fun subset_reuses_smaller_radius_same_origin() {
        val near = restSite("near", 52.0, 21.0)
        val far = restSite("far", 52.4, 21.0)
        val context = BdlSearchContext(
            originLat = 52.0,
            originLon = 21.0,
            radiusKm = 50.0,
            offlineVersion = 0L,
            bundle = RestSearchBundle(
                sites = listOf(near, far),
                zanocujPolygons = emptyList(),
            ),
        )
        assertTrue(
            BdlSearchSubsetFilter.canReuse(context, 52.0, 21.0, radiusKm = 25.0, offlineVersion = 0L),
        )
        val subset = BdlSearchSubsetFilter.subset(context, 52.0, 21.0, radiusKm = 25.0)
        assertEquals(1, subset.sites.size)
        assertEquals("near", subset.sites.first().id)
    }

    @Test
    fun subset_rejected_when_radius_grows() {
        val context = BdlSearchContext(
            originLat = 52.0,
            originLon = 21.0,
            radiusKm = 25.0,
            offlineVersion = 0L,
            bundle = RestSearchBundle(sites = listOf(restSite("a", 52.0, 21.0)), zanocujPolygons = emptyList()),
        )
        assertFalse(
            BdlSearchSubsetFilter.canReuse(context, 52.0, 21.0, radiusKm = 50.0, offlineVersion = 0L),
        )
    }

    @Test
    fun subset_rejected_when_offline_version_changes() {
        val context = BdlSearchContext(
            originLat = 52.0,
            originLon = 21.0,
            radiusKm = 50.0,
            offlineVersion = 100L,
            bundle = RestSearchBundle(sites = listOf(restSite("a", 52.0, 21.0)), zanocujPolygons = emptyList()),
        )
        assertFalse(
            BdlSearchSubsetFilter.canReuse(context, 52.0, 21.0, radiusKm = 25.0, offlineVersion = 200L),
        )
    }

    @Test
    fun subset_filters_zanocuj_polygons_by_radius() {
        val polygon = ZanocujPolygon(
            id = "zone-1",
            name = "Strefa",
            rings = listOf(
                listOf(
                    LatLon(52.0, 21.0),
                    LatLon(52.01, 21.0),
                    LatLon(52.01, 21.01),
                    LatLon(52.0, 21.01),
                    LatLon(52.0, 21.0),
                ),
            ),
        )
        val farPolygon = ZanocujPolygon(
            id = "zone-far",
            name = "Daleko",
            rings = listOf(
                listOf(
                    LatLon(53.0, 22.0),
                    LatLon(53.01, 22.0),
                    LatLon(53.01, 22.01),
                    LatLon(53.0, 22.01),
                    LatLon(53.0, 22.0),
                ),
            ),
        )
        val context = BdlSearchContext(
            originLat = 52.0,
            originLon = 21.0,
            radiusKm = 100.0,
            offlineVersion = 0L,
            bundle = RestSearchBundle(
                sites = emptyList(),
                zanocujPolygons = listOf(polygon, farPolygon),
            ),
        )
        val subset = BdlSearchSubsetFilter.subset(context, 52.0, 21.0, radiusKm = 10.0)
        assertEquals(1, subset.zanocujPolygons.size)
        assertEquals("zone-1", subset.zanocujPolygons.first().id)
    }

    private fun restSite(id: String, lat: Double, lon: Double) = RestSite(
        id = id,
        name = id,
        latitude = lat,
        longitude = lon,
        description = null,
        sourceLayerId = 15,
        sourceLayerName = "test",
        features = emptySet(),
        relatedObjects = emptyList(),
        zanocujStatus = ZanocujStatus.OUTSIDE_ZONE,
        distanceToZanocujBoundaryMeters = null,
    )

    private fun sampleRoad(id: String) = Road(
        id = id,
        type = "service",
        access = null,
        motorVehicle = null,
        motorcycle = null,
        vehicle = null,
        name = null,
        geometry = listOf(LatLon(52.12, 21.08), LatLon(52.121, 21.081)),
    )
}
