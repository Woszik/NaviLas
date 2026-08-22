package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.navilas.finder.data.cache.RoadAssessmentCache
import pl.navilas.finder.data.osm.RoadProximityAnalyzer
import pl.navilas.finder.domain.Poi
import pl.navilas.finder.domain.PoiCategory
import pl.navilas.finder.domain.PoiGeometryKind
import pl.navilas.finder.domain.RoadAccessClass
import pl.navilas.finder.domain.RoadAssessment
import pl.navilas.finder.domain.RoadSuitability

class RoadAssessmentCacheTest {
    @Test
    fun assessAll_returns_cached_assessment_without_network() {
        val cache = RoadAssessmentCache()
        val poi = pointPoi("site-a", 52.0, 21.0)
        val cached = RoadAssessment(
            nearestRoad = null,
            distanceToRoadMeters = 5.0,
            accessClass = RoadAccessClass.MOTO_ALLOWED,
            roadSuitability = RoadSuitability.EXCELLENT,
        )
        cache.put(poi.id, cached)
        val analyzer = RoadProximityAnalyzer(assessmentCache = cache)
        val result = analyzer.assessAll(listOf(poi))
        assertEquals(cached, result.getValue(poi.id))
    }

    private fun pointPoi(id: String, lat: Double, lon: Double) = Poi(
        id = id,
        categories = setOf(PoiCategory.REST),
        name = "Test",
        latitude = lat,
        longitude = lon,
        description = null,
        source = "test",
        geometryKind = PoiGeometryKind.POINT,
    )
}
