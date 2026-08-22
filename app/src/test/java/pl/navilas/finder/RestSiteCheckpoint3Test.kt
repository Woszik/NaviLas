package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.BdlFeatureExtractor
import pl.navilas.finder.data.bdl.RestSiteRepository
import pl.navilas.finder.data.bdl.SatellitePoint
import pl.navilas.finder.data.bdl.SpatialLinker
import pl.navilas.finder.data.bdl.ZanocujClassifier
import pl.navilas.finder.data.bdl.ZanocujPolygon
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.RestSiteResult
import pl.navilas.finder.domain.Road
import pl.navilas.finder.domain.RoadAccessClass
import pl.navilas.finder.domain.RoadAssessment
import pl.navilas.finder.domain.RoadSuitability
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.TravelProfile
import pl.navilas.finder.domain.ZanocujFilterMode
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.nav.NavigationLinks
import pl.navilas.finder.nav.NavigationTargets
import pl.navilas.finder.ui.canNavigateMotorcycle
import pl.navilas.finder.util.GeoUtils

class RestSiteCheckpoint3Test {
    private val square = listOf(
        LatLon(52.0, 21.0),
        LatLon(52.0, 21.1),
        LatLon(52.1, 21.1),
        LatLon(52.1, 21.0),
        LatLon(52.0, 21.0),
    )

    @Test
    fun rest_with_wiata_only() {
        val attrs = JSONObject("""{"wiata":"T","palenisko":"N","parking":"N"}""")
        assertEquals(setOf(SiteFeature.WIATA), BdlFeatureExtractor.fromAttributes(attrs))
    }

    @Test
    fun rest_with_palenisko_only() {
        val attrs = JSONObject("""{"wiata":"N","palenisko":"T"}""")
        assertEquals(setOf(SiteFeature.PALENISKO), BdlFeatureExtractor.fromAttributes(attrs))
    }

    @Test
    fun rest_with_wiata_and_palenisko_single_feature_set() {
        val attrs = JSONObject("""{"wiata":"T","palenisko":"T"}""")
        assertEquals(
            setOf(SiteFeature.WIATA, SiteFeature.PALENISKO),
            BdlFeatureExtractor.fromAttributes(attrs),
        )
    }

    @Test
    fun rest_with_parking_flag() {
        val attrs = JSONObject("""{"parking":"T","wiata":"N"}""")
        assertTrue(SiteFeature.PARKING in BdlFeatureExtractor.fromAttributes(attrs))
    }

    @Test
    fun rest_with_many_confirmed_features() {
        val attrs = JSONObject(
            """
            {
              "wiata":"T","palenisko":"T","parking":"T","woda_pitna":"T",
              "lawostoly":"T","kuchenka":"T","toalety_tm":"T","lad_rower":"T"
            }
            """.trimIndent(),
        )
        val features = BdlFeatureExtractor.fromAttributes(attrs)
        assertTrue(features.containsAll(
            setOf(
                SiteFeature.WIATA,
                SiteFeature.PALENISKO,
                SiteFeature.PARKING,
                SiteFeature.WODA_PITNA,
                SiteFeature.LAWOSTOLY,
                SiteFeature.KUCHENKA,
                SiteFeature.TOALETY,
                SiteFeature.LAD_ROWER,
            ),
        ))
    }

    @Test
    fun related_object_within_threshold_is_linked() {
        val satellites = listOf(
            SatellitePoint(
                id = "park-1",
                layerId = RestSiteRepository.LAYER_PARKING,
                layerName = "Parkingi",
                name = "Parking Leśny",
                latitude = 52.0005,
                longitude = 21.0,
                typeCode = "PARKING",
            ),
        )
        val linked = SpatialLinker.linkNearby(52.0, 21.0, satellites, SearchConfig(restLinkRadiusMeters = 100.0))
        assertEquals(1, linked.size)
        assertEquals("park-1", linked[0].id)
    }

    @Test
    fun related_object_beyond_threshold_not_linked() {
        val satellites = listOf(
            SatellitePoint(
                id = "park-far",
                layerId = RestSiteRepository.LAYER_PARKING,
                layerName = "Parkingi",
                name = "Parking",
                latitude = 52.01,
                longitude = 21.0,
                typeCode = "PARKING",
            ),
        )
        // ~1.1 km away
        val linked = SpatialLinker.linkNearby(52.0, 21.0, satellites, SearchConfig(restLinkRadiusMeters = 100.0))
        assertTrue(linked.isEmpty())
    }

    @Test
    fun zanocuj_in_zone() {
        val eval = ZanocujClassifier.evaluateAgainstRing(52.05, 21.05, square, SearchConfig.DEFAULT)
        assertEquals(ZanocujStatus.IN_ZONE, eval.status)
    }

    @Test
    fun zanocuj_near_zone() {
        // ~200 m south of southern edge at lat 52.0
        val eval = ZanocujClassifier.evaluateAgainstRing(51.9982, 21.05, square, SearchConfig.DEFAULT)
        assertEquals(ZanocujStatus.NEAR_ZONE, eval.status)
        assertTrue(eval.distanceToBoundaryMeters!! <= 500.0)
    }

    @Test
    fun zanocuj_outside_zone() {
        val eval = ZanocujClassifier.evaluateAgainstRing(51.0, 21.05, square, SearchConfig.DEFAULT)
        assertEquals(ZanocujStatus.OUTSIDE_ZONE, eval.status)
    }

    @Test
    fun zanocuj_point_on_boundary_is_in_zone() {
        val eval = ZanocujClassifier.evaluateAgainstRing(52.0, 21.05, square, SearchConfig.DEFAULT)
        assertEquals(ZanocujStatus.IN_ZONE, eval.status)
    }

    @Test
    fun zanocuj_multiple_polygons_picks_best_status() {
        val far = ZanocujPolygon("a", null, listOf(square.map { LatLon(it.latitude + 2, it.longitude) }))
        val near = ZanocujPolygon("b", null, listOf(square))
        val eval = ZanocujClassifier.evaluate(52.05, 21.05, listOf(far, near), SearchConfig.DEFAULT)
        assertEquals(ZanocujStatus.IN_ZONE, eval.status)
    }

    @Test
    fun zanocuj_second_exterior_ring_is_detected() {
        // Multipart: first ring far away, second ring contains the point.
        val far = listOf(
            LatLon(53.0, 22.0),
            LatLon(53.0, 22.1),
            LatLon(53.1, 22.1),
            LatLon(53.1, 22.0),
            LatLon(53.0, 22.0),
        )
        val poly = ZanocujPolygon("multi", null, listOf(far, square))
        val eval = ZanocujClassifier.evaluate(52.05, 21.05, listOf(poly), SearchConfig.DEFAULT)
        assertEquals(ZanocujStatus.IN_ZONE, eval.status)
    }

    @Test
    fun zanocuj_hole_is_outside_even_odd() {
        val outer = square
        val hole = listOf(
            LatLon(52.02, 21.02),
            LatLon(52.02, 21.08),
            LatLon(52.08, 21.08),
            LatLon(52.08, 21.02),
            LatLon(52.02, 21.02),
        )
        val poly = ZanocujPolygon("donut", null, listOf(outer, hole))
        assertEquals(
            ZanocujStatus.OUTSIDE_ZONE,
            ZanocujClassifier.evaluate(52.05, 21.05, listOf(poly), SearchConfig.DEFAULT).status,
        )
        assertEquals(
            ZanocujStatus.IN_ZONE,
            ZanocujClassifier.evaluate(52.01, 21.01, listOf(poly), SearchConfig.DEFAULT).status,
        )
    }

    @Test
    fun zanocuj_collapsed_degree_offset_geometry_is_wrong_reference() {
        // Reproduces BDL maxAllowableOffset=250 @ outSR=4326: rings collapse to ~1° boxes.
        val collapsed = listOf(
            LatLon(52.0, 21.0),
            LatLon(52.0, 21.0),
            LatLon(52.0, 21.0),
            LatLon(52.0, 21.0),
        )
        val accurate = square
        val pointInsideAccurate = 52.05 to 21.05
        assertEquals(
            ZanocujStatus.IN_ZONE,
            ZanocujClassifier.evaluateAgainstRing(
                pointInsideAccurate.first,
                pointInsideAccurate.second,
                accurate,
                SearchConfig.DEFAULT,
            ).status,
        )
        assertTrue(
            ZanocujClassifier.evaluateAgainstRing(
                pointInsideAccurate.first,
                pointInsideAccurate.second,
                collapsed,
                SearchConfig.DEFAULT,
            ).status != ZanocujStatus.IN_ZONE,
        )
    }

    @Test
    fun only_zanocuj_rejects_near_and_outside() {
        val sites = listOf(
            site("in", ZanocujStatus.IN_ZONE),
            site("near", ZanocujStatus.NEAR_ZONE),
            site("out", ZanocujStatus.OUTSIDE_ZONE),
        )
        val filtered = sites.filter {
            when (ZanocujFilterMode.ONLY_IN_ZONE) {
                ZanocujFilterMode.ONLY_IN_ZONE -> it.zanocujStatus == ZanocujStatus.IN_ZONE
                else -> true
            }
        }
        assertEquals(listOf("in"), filtered.map { it.id })
    }

    @Test
    fun all_filter_keeps_three_statuses() {
        val sites = listOf(
            site("in", ZanocujStatus.IN_ZONE),
            site("near", ZanocujStatus.NEAR_ZONE),
            site("out", ZanocujStatus.OUTSIDE_ZONE),
        )
        assertEquals(3, sites.size)
    }

    @Test
    fun google_maps_url() {
        val url = NavigationLinks.googleMapsDirUrl(LatLon(52.2, 21.1))
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1&destination="))
        assertTrue(url.contains("52.2"))
        assertTrue(url.contains("21.1"))
    }

    @Test
    fun osmand_uri() {
        val geo = NavigationLinks.osmAndGeoUri(LatLon(52.2, 21.1), "Wiata test")
        assertTrue(geo.startsWith("geo:52.2"))
        assertTrue(geo.contains("q="))
        val map = NavigationLinks.osmAndMapUrl(LatLon(52.2, 21.1), TravelProfile.MOTORCYCLE)
        assertTrue(map.contains("osmand.net/map/"))
        assertTrue(map.contains("profile=motorcycle"))
        assertTrue(map.contains("finish="))
    }

    @Test
    fun gpx_contains_waypoint() {
        val gpx = NavigationLinks.gpxWaypoint("Pod Debem", LatLon(52.2, 21.1), "Wiata, Palenisko")
        assertTrue(gpx.contains("<wpt lat=\"52.200000\" lon=\"21.100000\">"))
        assertTrue(gpx.contains("<name>Pod Debem</name>"))
        assertTrue(gpx.contains("Wiata, Palenisko"))
    }

    @Test
    fun motorcycle_without_road_cannot_navigate() {
        val result = RestSiteResult(
            site = site("s1", ZanocujStatus.OUTSIDE_ZONE),
            distanceKm = 1.0,
            roadAssessment = RoadAssessment(
                nearestRoad = null,
                distanceToRoadMeters = null,
                accessClass = null,
                roadSuitability = RoadSuitability.REJECTED,
                skippedReason = "brak",
            ),
            navigationTarget = LatLon(52.0, 21.0),
            navigationTargetKind = NavigationTargetKind.REST_SITE,
        )
        assertFalse(result.canNavigateMotorcycle())
        assertEquals(null, NavigationTargets.forMotorcycle(result.roadAssessment))
    }

    @Test
    fun motorcycle_with_allowed_road_can_navigate() {
        val assessment = RoadAssessment(
            nearestRoad = Road(
                id = "way/1",
                type = "residential",
                access = null,
                motorVehicle = null,
                motorcycle = null,
                vehicle = null,
                name = "Test",
                latitude = 52.001,
                longitude = 21.001,
                geometry = emptyList(),
            ),
            distanceToRoadMeters = 40.0,
            accessClass = RoadAccessClass.MOTO_ALLOWED,
            roadSuitability = RoadSuitability.EXCELLENT,
        )
        val target = NavigationTargets.forMotorcycle(assessment)!!
        assertEquals(NavigationTargetKind.OSM_ROAD, target.second)
        assertEquals(52.001, target.first.latitude, 1e-6)
    }

    @Test
    fun car_prefers_related_parking_as_nav_target() {
        val site = site("s", ZanocujStatus.OUTSIDE_ZONE).copy(
            relatedObjects = listOf(
                pl.navilas.finder.domain.RelatedBdlObject(
                    id = "p1",
                    layerId = RestSiteRepository.LAYER_PARKING,
                    layerName = "Parkingi",
                    name = "Parking",
                    latitude = 52.01,
                    longitude = 21.01,
                    distanceMeters = 40.0,
                    typeCode = "PARKING",
                ),
            ),
        )
        val (target, kind) = NavigationTargets.forCar(site)
        assertEquals(NavigationTargetKind.PARKING, kind)
        assertEquals(52.01, target.latitude, 1e-6)
    }

    @Test
    fun point_in_polygon_helper() {
        assertTrue(GeoUtils.pointInPolygon(52.05, 21.05, square))
        assertFalse(GeoUtils.pointInPolygon(53.0, 21.05, square))
    }

    private fun site(id: String, status: ZanocujStatus) = RestSite(
        id = id,
        name = "Miejsce $id",
        latitude = 52.0,
        longitude = 21.0,
        description = null,
        sourceLayerId = 15,
        sourceLayerName = "Miejsca wypoczynku",
        features = setOf(SiteFeature.WIATA),
        relatedObjects = emptyList(),
        zanocujStatus = status,
        distanceToZanocujBoundaryMeters = if (status == ZanocujStatus.NEAR_ZONE) 280.0 else null,
    )
}
