package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.osm.MotorcycleAccessHint
import pl.navilas.finder.data.osm.OverpassRoadClient
import pl.navilas.finder.data.osm.RoadClassifier
import pl.navilas.finder.data.osm.RoadProximityAnalyzer
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.Poi
import pl.navilas.finder.domain.PoiCategory
import pl.navilas.finder.domain.PoiGeometryKind
import pl.navilas.finder.domain.Road
import pl.navilas.finder.domain.RoadAccessClass
import pl.navilas.finder.domain.RoadSuitability

class RoadAnalysisTest {
    private val analyzer = RoadProximityAnalyzer(
        overpass = OverpassRoadClient(), // unused in unit tests that pass roads explicitly
    )

    @Test
    fun poi_close_to_residential_is_excellent() {
        val road = road(
            id = "way/1",
            type = "residential",
            geometry = listOf(LatLon(52.0, 21.0), LatLon(52.0, 21.001)),
        )
        val poi = pointPoi(lat = 52.0002, lon = 21.0005)
        val assessment = analyzer.assessPoint(poi, listOf(road))
        assertEquals(RoadAccessClass.MOTO_ALLOWED, assessment.accessClass)
        assertNotNull(assessment.distanceToRoadMeters)
        assertTrue(assessment.distanceToRoadMeters!! < 50.0)
        assertEquals(RoadSuitability.EXCELLENT, assessment.roadSuitability)
    }

    @Test
    fun poi_far_from_road_is_rejected_by_distance() {
        val road = road(
            id = "way/2",
            type = "tertiary",
            geometry = listOf(LatLon(52.0, 21.0), LatLon(52.0, 21.001)),
        )
        // ~0.004° lat ≈ 440 m
        val poi = pointPoi(lat = 52.004, lon = 21.0005)
        val assessment = analyzer.assessPoint(poi, listOf(road))
        assertTrue(assessment.distanceToRoadMeters!! > 300.0)
        assertEquals(RoadSuitability.REJECTED, assessment.roadSuitability)
    }

    @Test
    fun footway_and_path_are_not_road_for_moto() {
        assertEquals(
            RoadAccessClass.NOT_ROAD,
            RoadClassifier.classify(highway = "footway"),
        )
        assertEquals(
            RoadAccessClass.NOT_ROAD,
            RoadClassifier.classify(highway = "path"),
        )
        assertEquals(
            RoadAccessClass.NOT_ROAD,
            RoadClassifier.classify(highway = "cycleway"),
        )
        assertEquals(
            RoadAccessClass.NOT_ROAD,
            RoadClassifier.classify(highway = "steps"),
        )
        val footway = road(
            id = "way/3",
            type = "footway",
            geometry = listOf(LatLon(52.0, 21.0), LatLon(52.0, 21.001)),
        )
        val assessment = analyzer.assessPoint(pointPoi(52.0001, 21.0004), listOf(footway))
        assertEquals(RoadAccessClass.NOT_ROAD, assessment.accessClass)
        assertEquals(RoadSuitability.REJECTED, assessment.roadSuitability)
    }

    @Test
    fun access_no_is_restricted() {
        assertEquals(
            RoadAccessClass.MOTO_RESTRICTED,
            RoadClassifier.classify(highway = "service", access = "no"),
        )
    }

    @Test
    fun untagged_track_is_allowed_but_access_uncertain() {
        val track = road(id = "way/t", type = "track")
        assertEquals(
            RoadAccessClass.MOTO_ALLOWED,
            RoadClassifier.classify(highway = "track"),
        )
        assertTrue(
            MotorcycleAccessHint.isLegalAccessUncertain(track, RoadAccessClass.MOTO_ALLOWED),
        )
        assertTrue(
            !MotorcycleAccessHint.isLegalAccessUncertain(
                track.copy(motorVehicle = "forestry"),
                RoadAccessClass.MOTO_RESTRICTED,
            ),
        )
        assertTrue(
            !MotorcycleAccessHint.isLegalAccessUncertain(
                road(id = "way/r", type = "residential"),
                RoadAccessClass.MOTO_ALLOWED,
            ),
        )
    }

    @Test
    fun motorcycle_no_is_restricted() {
        assertEquals(
            RoadAccessClass.MOTO_RESTRICTED,
            RoadClassifier.classify(highway = "track", motorcycle = "no"),
        )
    }

    @Test
    fun surface_and_tracktype_labels() {
        val gravel = Road(
            id = "way/s",
            type = "track",
            access = null,
            motorVehicle = "forestry",
            motorcycle = null,
            vehicle = null,
            surface = "ground",
            tracktype = "grade2",
            name = null,
        )
        assertEquals("gruntowa", RoadClassifier.surfaceLabelPl("ground"))
        assertEquals("raczej przejezdna", RoadClassifier.tracktypeLabelPl("grade2"))
        val label = RoadClassifier.describeMotorcycleRoad(gravel)
        assertTrue(label.contains("gruntowa"))
        assertTrue(label.contains("raczej przejezdna"))
        assertTrue(label.contains("leśna"))
    }

    @Test
    fun parse_ways_reads_surface_and_tracktype() {
        val payload = """
            {"elements":[{"type":"way","id":9,"tags":{"highway":"track","surface":"gravel","tracktype":"grade3"},
              "geometry":[{"lat":52.0,"lon":21.0},{"lat":52.001,"lon":21.001}]}]}
        """.trimIndent()
        val roads = OverpassRoadClient().parseWays(payload)
        assertEquals(1, roads.size)
        assertEquals("gravel", roads[0].surface)
        assertEquals("grade3", roads[0].tracktype)
    }

    @Test
    fun missing_access_tags_on_residential_is_allowed() {
        assertEquals(
            RoadAccessClass.MOTO_ALLOWED,
            RoadClassifier.classify(highway = "residential"),
        )
    }

    @Test
    fun no_road_found_yields_rejected() {
        val assessment = analyzer.assessPoint(pointPoi(52.0, 21.0), emptyList())
        assertNull(assessment.nearestRoad)
        assertEquals(RoadSuitability.REJECTED, assessment.roadSuitability)
        assertNotNull(assessment.skippedReason)
    }

    @Test
    fun area_poi_skips_road_ranking() {
        val area = Poi(
            id = "camp-1",
            categories = setOf(PoiCategory.CAMP),
            name = "Zanocuj",
            latitude = 52.0,
            longitude = 21.0,
            description = null,
            source = "test",
            geometryKind = PoiGeometryKind.AREA,
            areaRings = listOf(
                listOf(
                    LatLon(52.0, 21.0),
                    LatLon(52.01, 21.0),
                    LatLon(52.01, 21.01),
                    LatLon(52.0, 21.01),
                    LatLon(52.0, 21.0),
                ),
            ),
        )
        val map = analyzer.assessAll(listOf(area))
        val assessment = map.getValue(area.id)
        assertNull(assessment.nearestRoad)
        assertNull(assessment.roadSuitability)
        assertTrue(assessment.skippedReason!!.contains("Obszar"))
    }

    @Test
    fun assess_all_reports_progress_for_each_poi() {
        val areas = listOf("a", "b").map { id ->
            Poi(
                id = id,
                categories = setOf(PoiCategory.CAMP),
                name = id,
                latitude = 52.0,
                longitude = 21.0,
                description = null,
                source = "test",
                geometryKind = PoiGeometryKind.AREA,
                areaRings = emptyList(),
            )
        }
        val progress = mutableListOf<Pair<Int, Int>>()

        analyzer.assessAll(areas) { completed, total ->
            progress += completed to total
        }

        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test
    fun prefers_motorable_road_over_nearby_path() {
        val path = road(
            id = "way/path",
            type = "path",
            geometry = listOf(LatLon(52.0, 21.0), LatLon(52.0, 21.001)),
        )
        val service = road(
            id = "way/service",
            type = "service",
            geometry = listOf(LatLon(52.0003, 21.0), LatLon(52.0003, 21.001)),
        )
        val poi = pointPoi(lat = 52.00005, lon = 21.0004)
        val assessment = analyzer.assessPoint(poi, listOf(path, service))
        assertEquals("way/service", assessment.nearestRoad?.id)
        assertEquals(RoadAccessClass.MOTO_ALLOWED, assessment.accessClass)
        assertEquals(RoadSuitability.EXCELLENT, assessment.roadSuitability)
    }

    @Test
    fun overpass_query_uses_around_per_point() {
        val client = OverpassRoadClient()
        val q = client.buildAroundQuery(
            listOf(LatLon(52.202265, 21.181408), LatLon(52.21, 21.19)),
            radiusMeters = 400.0,
        )
        assertTrue(q.contains("way(around:400,52.202265,21.181408)[highway]"))
        assertTrue(q.contains("out tags geom"))
    }

    @Test
    fun parse_overpass_way_payload() {
        val json = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 27548154,
                  "tags": {
                    "highway": "residential",
                    "name": "Mieczysława Pożaryskiego"
                  },
                  "geometry": [
                    {"lat": 52.202, "lon": 21.181},
                    {"lat": 52.203, "lon": 21.182}
                  ]
                }
              ]
            }
        """.trimIndent()
        val roads = OverpassRoadClient().parseWays(json)
        assertEquals(1, roads.size)
        assertEquals("way/27548154", roads[0].id)
        assertEquals("residential", roads[0].type)
        assertEquals("Mieczysława Pożaryskiego", roads[0].name)
    }

    private fun pointPoi(lat: Double, lon: Double) = Poi(
        id = "poi-$lat-$lon",
        categories = setOf(PoiCategory.REST),
        name = "Wiata test",
        latitude = lat,
        longitude = lon,
        description = null,
        source = "test",
        geometryKind = PoiGeometryKind.POINT,
    )

    private fun road(
        id: String,
        type: String,
        geometry: List<LatLon> = listOf(LatLon(52.0, 21.0), LatLon(52.0, 21.001)),
    ) = Road(
        id = id,
        type = type,
        access = null,
        motorVehicle = null,
        motorcycle = null,
        vehicle = null,
        name = null,
        geometry = geometry,
    )
}
