package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.ForestEntryBanBounds
import pl.navilas.finder.data.bdl.ForestEntryBanCatalog
import pl.navilas.finder.data.bdl.ForestEntryBanClassifier
import pl.navilas.finder.data.bdl.ForestEntryBanLoader
import pl.navilas.finder.data.bdl.ForestEntryBanStore
import pl.navilas.finder.domain.ForestEntryBan
import pl.navilas.finder.domain.ForestEntryBanReason
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.MapTrackingCamera
import pl.navilas.finder.domain.MapTrackingMode
import pl.navilas.finder.util.GeoUtils

class MapTrackingAndEntryBanTest {
    @Test
    fun pause_play_keeps_stored_frame_start_from_off_captures_camera() {
        assertFalse(MapTrackingCamera.shouldCaptureLiveCamera(MapTrackingMode.TRACKING))
        assertFalse(MapTrackingCamera.shouldCaptureLiveCamera(MapTrackingMode.PAUSED))
        assertTrue(MapTrackingCamera.shouldCaptureLiveCamera(MapTrackingMode.OFF))
        assertEquals(13.0, MapTrackingCamera.zoomForStart(11.2), 0.0)
        assertEquals(16.4, MapTrackingCamera.zoomForStart(16.4), 0.0)
    }

    @Test
    fun catalog_uses_detail_layers_not_litter_moisture() {
        assertEquals(listOf(6, 7, 2), ForestEntryBanCatalog.QUERY_LAYER_IDS)
        assertEquals(ForestEntryBanReason.OTHER, ForestEntryBanCatalog.reasonFor(6, null))
        assertEquals(ForestEntryBanReason.PESTICIDE, ForestEntryBanCatalog.reasonFor(7, null))
        assertEquals(ForestEntryBanReason.FIRE, ForestEntryBanCatalog.reasonFor(2, "zagrożenie pożarowe"))
        assertEquals(
            ForestEntryBanReason.FIRE,
            ForestEntryBanCatalog.reasonFromKod("zagrożenie pożarowe"),
        )
    }

    @Test
    fun loader_maps_feature_and_formats_dates() {
        val feature = JSONObject(
            """
            {
              "attributes": {
                "objectid": 3817030,
                "nazwa_nadl": "Białowieża                    ",
                "lesnictwo": "Stoczek",
                "kod_oddzialu": "552A",
                "kod": "inne przyczyny",
                "data": "2025-03-13 07:52:07",
                "data_koncowa": "2026-12-31 00:00:00"
              },
              "geometry": {
                "rings": [[
                  [23.80, 52.70],
                  [23.81, 52.70],
                  [23.81, 52.71],
                  [23.80, 52.71],
                  [23.80, 52.70]
                ]]
              }
            }
            """.trimIndent(),
        )
        val ban = ForestEntryBanLoader(BdlArcGisClientUnused).mapFeature(feature, 6)!!
        assertEquals("ban:6:3817030", ban.id)
        assertEquals(ForestEntryBanReason.OTHER, ban.reason)
        assertEquals("Białowieża", ban.forestDistrict)
        assertEquals("Stoczek", ban.forestry)
        assertEquals("552A", ban.compartment)
        assertEquals("13.03.2025", ban.validFrom)
        assertEquals("31.12.2026", ban.validUntil)
        assertTrue(ban.summaryPl().contains("Nadleśnictwo Białowieża"))
        assertTrue(ban.summaryPl().contains("do 31.12.2026"))
    }

    @Test
    fun classifier_uses_rings_not_centroid() {
        val square = listOf(
            LatLon(52.70, 23.80),
            LatLon(52.70, 23.81),
            LatLon(52.71, 23.81),
            LatLon(52.71, 23.80),
            LatLon(52.70, 23.80),
        )
        val ban = ForestEntryBan(
            id = "ban:6:1",
            reason = ForestEntryBanReason.OTHER,
            forestDistrict = "Test",
            forestry = null,
            compartment = null,
            validFrom = null,
            validUntil = null,
            rings = listOf(square),
        )
        assertEquals(ban, ForestEntryBanClassifier.containing(52.705, 23.805, listOf(ban)))
        assertNull(ForestEntryBanClassifier.containing(52.0, 21.0, listOf(ban)))
    }

    @Test
    fun date_and_name_cleanup() {
        assertEquals("31.12.2026", ForestEntryBanLoader.formatBanDate("2026-12-31 00:00:00"))
        assertEquals("Białowieża", ForestEntryBanLoader.clean("Białowieża                    "))
        assertNull(ForestEntryBanLoader.clean("null"))
        assertNull(ForestEntryBanLoader.clean("  "))
    }

    @Test
    fun store_roundtrip_and_delete() {
        val dir = createTempDir(prefix = "navilas-entry-bans")
        val store = ForestEntryBanStore(dir)
        val ban = sampleBan("ban:6:1", 52.70, 23.80)
        store.saveAll(listOf(ban), downloadedAt = 1_700_000_000_000L)

        assertTrue(store.isReady())
        assertEquals(1_700_000_000_000L, store.downloadedAt())
        assertEquals(1, store.count())
        val loaded = store.loadAll()
        assertEquals(1, loaded.size)
        assertEquals(ban.id, loaded[0].id)
        assertEquals(ban.forestDistrict, loaded[0].forestDistrict)
        assertEquals(ban.rings[0].size, loaded[0].rings[0].size)

        store.deleteAll()
        assertFalse(store.isReady())
        assertEquals(0, store.count())
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun classifier_filters_index_by_envelope() {
        val near = sampleBan("ban:6:near", 52.70, 23.80)
        val far = sampleBan("ban:6:far", 50.0, 19.0)
        val index = listOf(near, far).map { ForestEntryBanBounds.from(it)!! }
        val hits = ForestEntryBanClassifier.inEnvelope(
            index = index,
            envelope = GeoUtils.Envelope(
                xmin = 23.79,
                ymin = 52.69,
                xmax = 23.82,
                ymax = 52.72,
            ),
            centerLat = 52.705,
            centerLon = 23.805,
            limit = 80,
        )
        assertEquals(listOf(near.id), hits.map { it.id })
    }
}

private fun sampleBan(id: String, lat: Double, lon: Double): ForestEntryBan {
    val square = listOf(
        LatLon(lat, lon),
        LatLon(lat, lon + 0.01),
        LatLon(lat + 0.01, lon + 0.01),
        LatLon(lat + 0.01, lon),
        LatLon(lat, lon),
    )
    return ForestEntryBan(
        id = id,
        reason = ForestEntryBanReason.OTHER,
        forestDistrict = "Test",
        forestry = "Stoczek",
        compartment = "1",
        validFrom = "13.03.2025",
        validUntil = "31.12.2026",
        rings = listOf(square),
    )
}

/** Constructor requires a client; mapping tests never call the network. */
private val BdlArcGisClientUnused = pl.navilas.finder.data.bdl.BdlArcGisClient(
    baseUrl = ForestEntryBanCatalog.BASE_URL,
)
