package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.BdlAmenityStopRules
import pl.navilas.finder.data.bdl.BdlFeatureExtractor
import pl.navilas.finder.data.bdl.RestSiteRepository
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.nav.NavigationTargets
import pl.navilas.finder.util.GeoUtils

class AmenityStopResultTest {
    @Test
    fun bare_parking_and_stop_qualify_as_standalone() {
        assertTrue(
            BdlAmenityStopRules.qualifiesAsStandalone(
                JSONObject("""{"wiata":"N","palenisko":"N","lawostoly":"N","parking":"N"}"""),
            ),
        )
        assertTrue(BdlAmenityStopRules.qualifiesAsStandalone(JSONObject("""{"wiata":"T"}""")))
        assertTrue(BdlAmenityStopRules.qualifiesAsStandalone(JSONObject("""{"palenisko":"T"}""")))
        assertTrue(BdlAmenityStopRules.qualifiesAsStandalone(JSONObject("""{"lawostoly":"T"}""")))
    }

    @Test
    fun potempowe_like_stop_extracts_amenities() {
        val attrs = JSONObject(
            """
            {
              "nzw_ob":"Miejsce postoju pojazdów Uroczysko Potempowe",
              "wiata":"T","palenisko":"T","lawostoly":"T","parking":"N"
            }
            """.trimIndent(),
        )
        assertTrue(BdlAmenityStopRules.qualifiesAsStandalone(attrs))
        val features = BdlFeatureExtractor.fromAttributes(attrs)
        assertTrue(features.contains(SiteFeature.WIATA))
        assertTrue(features.contains(SiteFeature.PALENISKO))
        assertTrue(features.contains(SiteFeature.LAWOSTOLY))
    }

    @Test
    fun amenity_stop_within_100m_of_layer15_is_considered_covered() {
        val rest = primary(lat = 50.6300, lon = 18.7840, layer = RestSiteRepository.LAYER_REST)
        val stopLat = 50.6302
        val stopLon = 18.7841
        val dist = GeoUtils.distanceMeters(rest.latitude, rest.longitude, stopLat, stopLon)
        assertTrue(dist <= SearchConfig.DEFAULT.restLinkRadiusMeters)
    }

    @Test
    fun amenity_stop_1km_from_layer15_is_not_covered() {
        val rest = primary(lat = 50.6300, lon = 18.7840, layer = RestSiteRepository.LAYER_REST)
        // ~1 km north
        val stopLat = 50.6390
        val stopLon = 18.7840
        val dist = GeoUtils.distanceMeters(rest.latitude, rest.longitude, stopLat, stopLon)
        assertTrue(dist > SearchConfig.DEFAULT.restLinkRadiusMeters)
    }

    @Test
    fun car_nav_to_layer19_primary_goes_to_site_coords() {
        val site = primary(
            lat = 50.63057,
            lon = 18.78476,
            layer = RestSiteRepository.LAYER_STOP,
            name = "Miejsce postoju pojazdów Uroczysko Potempowe",
        )
        val (target, kind) = NavigationTargets.forCar(site)
        assertEquals(NavigationTargetKind.REST_SITE, kind)
        assertEquals(50.63057, target.latitude, 1e-6)
        assertEquals(18.78476, target.longitude, 1e-6)
    }

    @Test
    fun car_nav_to_layer17_primary_is_parking_kind() {
        val site = primary(
            lat = 52.0,
            lon = 21.0,
            layer = RestSiteRepository.LAYER_PARKING,
            name = "Parking leśny test",
        )
        val (_, kind) = NavigationTargets.forCar(site)
        assertEquals(NavigationTargetKind.PARKING, kind)
    }

    private fun primary(
        lat: Double,
        lon: Double,
        layer: Int,
        name: String = "Test",
    ): RestSite = RestSite(
        id = "bdl:$layer:objectid:1",
        name = name,
        latitude = lat,
        longitude = lon,
        description = null,
        sourceLayerId = layer,
        sourceLayerName = when (layer) {
            RestSiteRepository.LAYER_REST -> RestSiteRepository.LAYER_NAME_REST
            RestSiteRepository.LAYER_PARKING -> RestSiteRepository.LAYER_NAME_PARKING
            else -> RestSiteRepository.LAYER_NAME_STOP
        },
        features = setOf(SiteFeature.WIATA),
        relatedObjects = emptyList(),
        zanocujStatus = ZanocujStatus.OUTSIDE_ZONE,
    )
}
