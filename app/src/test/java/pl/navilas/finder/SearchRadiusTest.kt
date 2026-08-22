package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.BdlMapper
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.util.GeoUtils
import org.json.JSONObject

class SearchRadiusTest {
    @Test
    fun presets_include_required_values_and_default_25() {
        assertEquals(listOf(5.0, 10.0, 25.0, 50.0, 100.0), SearchConfig.SEARCH_RADIUS_PRESETS_KM)
        assertEquals(25.0, SearchConfig.DEFAULT_SEARCH_RADIUS_KM, 0.0)
        assertEquals(25.0, SearchConfig.DEFAULT.searchRadiusKm, 0.0)
        assertEquals(100.0, SearchConfig.MAX_SEARCH_RADIUS_KM, 0.0)
    }

    @Test
    fun radius_filter_rejects_30km_when_limit_is_25() {
        val poi = pointAt(lat = 52.0 + (30.0 / 111.0), lon = 21.0)
        assertFalse(GeoUtils.isWithinRadiusKm(52.0, 21.0, poi, 25.0))
        assertFalse(GeoUtils.isWithinRadiusKm(52.0, 21.0, poi, 5.0))
        assertFalse(GeoUtils.isWithinRadiusKm(52.0, 21.0, poi, 10.0))
    }

    @Test
    fun radius_filter_accepts_30km_when_limit_is_50_or_100() {
        val poi = pointAt(lat = 52.0 + (30.0 / 111.0), lon = 21.0)
        assertTrue(GeoUtils.isWithinRadiusKm(52.0, 21.0, poi, 50.0))
        assertTrue(GeoUtils.isWithinRadiusKm(52.0, 21.0, poi, 100.0))
    }

    @Test
    fun each_preset_radius_is_applied_consistently_by_haversine_filter() {
        val userLat = 52.0
        val userLon = 21.0
        for (radius in SearchConfig.SEARCH_RADIUS_PRESETS_KM) {
            val inside = pointAt(lat = userLat + ((radius * 0.5) / 111.0), lon = userLon)
            val outside = pointAt(lat = userLat + ((radius * 1.2) / 111.0), lon = userLon)
            assertTrue(
                "expected inside for ${radius}km",
                GeoUtils.isWithinRadiusKm(userLat, userLon, inside, radius),
            )
            assertFalse(
                "expected outside for ${radius}km",
                GeoUtils.isWithinRadiusKm(userLat, userLon, outside, radius),
            )
            val envelope = GeoUtils.envelopeAround(userLat, userLon, radius)
            assertTrue(inside.longitude in envelope.xmin..envelope.xmax)
            assertTrue(inside.latitude in envelope.ymin..envelope.ymax)
        }
    }

    @Test
    fun search_config_rejects_radius_above_max() {
        try {
            SearchConfig(searchRadiusKm = 150.0)
            assertTrue("expected require failure", false)
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private fun pointAt(lat: Double, lon: Double) = BdlMapper.mapRestFeature(
        JSONObject(
            """
            {
              "attributes": {
                "objectid": 1,
                "foreign_key": "r-$lat",
                "tur_rec_pnt_id": 1,
                "nzw_ob": "Test",
                "wiata": "T",
                "palenisko": "N"
              },
              "geometry": { "x": $lon, "y": $lat }
            }
            """.trimIndent(),
        ),
    )!!
}
