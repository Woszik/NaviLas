package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.osm.OsmWaterClassifier
import pl.navilas.finder.data.osm.OverpassWaterClient
import pl.navilas.finder.data.osm.WaterProximity
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.OsmWaterFeature

class OsmWaterFilterTest {
    @Test
    fun classifier_accepts_rivers_lakes_ponds() {
        assertTrue(accept(waterway = "river"))
        assertTrue(accept(waterway = "stream"))
        assertTrue(accept(waterway = "canal"))
        assertTrue(accept(natural = "water"))
        assertTrue(accept(water = "lake"))
        assertTrue(accept(water = "pond"))
        assertTrue(accept(water = "reservoir"))
        assertTrue(accept(landuse = "reservoir"))
    }

    @Test
    fun classifier_rejects_ditch_spring_pool_intermittent() {
        assertFalse(accept(waterway = "ditch"))
        assertFalse(accept(waterway = "drain"))
        assertFalse(accept(natural = "spring"))
        assertFalse(accept(natural = "wetland"))
        assertFalse(accept(leisure = "swimming_pool"))
        assertFalse(accept(waterway = "river", intermittent = "yes"))
        assertFalse(accept(natural = "water", water = "swimming_pool"))
    }

    @Test
    fun proximity_polyline_uses_nearest_point() {
        val river = OsmWaterFeature.of(
            "way/1",
            polygon = false,
            geometry = listOf(LatLon(52.0, 21.0), LatLon(52.0, 21.01)),
        )!!
        assertTrue(WaterProximity.isWithin(52.001, 21.0, river, 250.0))
        assertFalse(WaterProximity.isWithin(52.01, 21.0, river, 250.0))
    }

    @Test
    fun proximity_polygon_inside_is_zero() {
        val lake = OsmWaterFeature.of(
            "way/2",
            polygon = true,
            geometry = listOf(
                LatLon(52.0, 21.0),
                LatLon(52.0, 21.01),
                LatLon(52.01, 21.01),
                LatLon(52.01, 21.0),
                LatLon(52.0, 21.0),
            ),
        )!!
        assertEquals(0.0, WaterProximity.distanceMeters(52.005, 21.005, lake), 0.01)
        assertTrue(WaterProximity.isWithin(52.02, 21.005, lake, 1_200.0))
        assertFalse(WaterProximity.isWithin(52.05, 21.005, lake, 250.0))
    }

    @Test
    fun overpass_parse_keeps_river_drops_ditch_and_spring() {
        val json = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 1,
                  "tags": { "waterway": "river" },
                  "geometry": [
                    { "lat": 52.0, "lon": 21.0 },
                    { "lat": 52.0, "lon": 21.01 }
                  ]
                },
                {
                  "type": "way",
                  "id": 2,
                  "tags": { "waterway": "ditch" },
                  "geometry": [
                    { "lat": 52.0, "lon": 21.0 },
                    { "lat": 52.001, "lon": 21.0 }
                  ]
                },
                {
                  "type": "node",
                  "id": 3,
                  "lat": 52.0,
                  "lon": 21.0,
                  "tags": { "natural": "spring" }
                }
              ]
            }
        """.trimIndent()
        val features = OverpassWaterClient().parseElements(json)
        assertEquals(1, features.size)
        assertEquals("way/1", features[0].id)
        assertFalse(features[0].polygon)
    }

    private fun accept(
        waterway: String? = null,
        natural: String? = null,
        water: String? = null,
        landuse: String? = null,
        leisure: String? = null,
        intermittent: String? = null,
        seasonal: String? = null,
    ): Boolean = OsmWaterClassifier.accept(
        waterway,
        natural,
        water,
        landuse,
        leisure,
        intermittent,
        seasonal,
    )
}
