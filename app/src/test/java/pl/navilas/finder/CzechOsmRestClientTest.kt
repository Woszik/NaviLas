package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.osm.CzechOsmRestClient
import pl.navilas.finder.nav.NavigationTargets
import pl.navilas.finder.domain.NavigationTargetKind

class CzechOsmRestClientTest {
    @Test
    fun envelope_detects_border_search_from_poland() {
        assertTrue(CzechOsmRestClient.envelopeMayHitCzechia(50.2723, 17.2941, 25.0))
        assertTrue(CzechOsmRestClient.envelopeMayHitCzechia(49.7020, 18.7015, 25.0))
        assertFalse(CzechOsmRestClient.envelopeMayHitCzechia(52.1, 19.4, 25.0))
    }

    @Test
    fun links_picnic_to_nearby_public_parking_and_skips_far_or_private() {
        val client = CzechOsmRestClient()
        val rest = listOf(
            element(
                type = "node",
                id = 1,
                lat = 50.0,
                lon = 17.0,
                tags = """{"tourism":"picnic_site","name":"Drakov"}""",
            ),
            element(
                type = "node",
                id = 2,
                lat = 50.01,
                lon = 17.0,
                tags = """{"amenity":"shelter","shelter_type":"picnic_shelter","name":"Daleko"}""",
            ),
        )
        val parking = listOf(
            element(
                type = "way",
                id = 10,
                lat = 50.0005,
                lon = 17.0,
                tags = """{"amenity":"parking","name":"P Drjek"}""",
            ),
            element(
                type = "way",
                id = 11,
                lat = 50.009,
                lon = 17.0,
                tags = """{"amenity":"parking","access":"private"}""",
            ),
        )
        val sites = client.buildSites(
            originLat = 50.0,
            originLon = 17.0,
            radiusKm = 25.0,
            restElements = rest,
            parkingElements = parking,
        )
        assertEquals(1, sites.size)
        assertEquals("Drakov", sites[0].name)
        assertEquals(CzechOsmRestClient.LAYER_CZ_REST, sites[0].sourceLayerId)
        assertEquals(1, sites[0].relatedObjects.size)
        assertEquals(CzechOsmRestClient.LAYER_CZ_PARKING, sites[0].relatedObjects[0].layerId)

        val (target, kind) = NavigationTargets.forCar(sites[0])
        assertEquals(NavigationTargetKind.PARKING, kind)
        assertEquals(50.0005, target.latitude, 1e-6)
        assertEquals(17.0, target.longitude, 1e-6)
    }

    @Test
    fun dedups_near_duplicate_picnic_nodes() {
        val client = CzechOsmRestClient()
        val rest = listOf(
            element("node", 1, 50.0, 17.0, """{"tourism":"picnic_site"}"""),
            element("node", 2, 50.0001, 17.0, """{"tourism":"picnic_site"}"""),
        )
        val parking = listOf(
            element("way", 10, 50.0002, 17.0, """{"amenity":"parking"}"""),
        )
        val sites = client.buildSites(50.0, 17.0, 25.0, rest, parking)
        assertEquals(1, sites.size)
    }

    private fun element(
        type: String,
        id: Long,
        lat: Double,
        lon: Double,
        tags: String,
    ): JSONObject = JSONObject(
        """
        {
          "type":"$type",
          "id":$id,
          "lat":$lat,
          "lon":$lon,
          "tags":$tags
        }
        """.trimIndent(),
    )
}
