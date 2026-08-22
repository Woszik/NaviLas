package pl.navilas.finder.data.osm

import pl.navilas.finder.data.cache.RoadAssessmentCache
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.Poi
import pl.navilas.finder.domain.PoiGeometryKind
import pl.navilas.finder.domain.Road
import pl.navilas.finder.domain.RoadAccessClass
import pl.navilas.finder.domain.RoadAssessment
import pl.navilas.finder.domain.RoadSuitability
import pl.navilas.finder.domain.RoadSuitabilityThresholds
import pl.navilas.finder.util.GeoUtils

/**
 * Resolves nearest OSM road and suitability for point BDL POIs.
 * AREA POIs are skipped (no centroid-as-destination road ranking).
 */
class RoadProximityAnalyzer(
    private val overpass: HighwayAroundFetcher = OverpassRoadClient(),
    private val thresholds: RoadSuitabilityThresholds = RoadSuitabilityThresholds.DEFAULT,
    private val searchRadiusMeters: Double = OverpassRoadClient.DEFAULT_RADIUS_METERS,
    private val assessmentCache: RoadAssessmentCache? = null,
) {
    fun assessAll(pois: List<Poi>): Map<String, RoadAssessment> {
        val result = LinkedHashMap<String, RoadAssessment>()
        val pointPois = pois.filter { it.geometryKind == PoiGeometryKind.POINT }
        pois.filter { it.geometryKind == PoiGeometryKind.AREA }.forEach { area ->
            result[area.id] = RoadAssessment(
                nearestRoad = null,
                distanceToRoadMeters = null,
                accessClass = null,
                roadSuitability = null,
                skippedReason = "Obszar Zanocuj — bez rankingu drogowego (centroid nie jest punktem drogi).",
            )
        }
        if (pointPois.isEmpty()) return result

        val fromCache = LinkedHashMap<String, RoadAssessment>()
        val uncached = ArrayList<Poi>()
        pointPois.forEach { poi ->
            val cached = assessmentCache?.get(poi.id)
            if (cached != null) {
                fromCache[poi.id] = cached
            } else {
                uncached += poi
            }
        }

        if (uncached.isNotEmpty()) {
            val roads = overpass.fetchHighwaysAround(
                points = uncached.map { LatLon(it.latitude, it.longitude) },
                radiusMeters = searchRadiusMeters,
            )
            uncached.forEach { poi ->
                val assessment = assessPoint(poi, roads)
                result[poi.id] = assessment
                assessmentCache?.put(poi.id, assessment)
            }
        }

        result.putAll(fromCache)
        return result
    }

    fun assessPoint(poi: Poi, roads: List<Road>): RoadAssessment {
        if (poi.geometryKind != PoiGeometryKind.POINT) {
            return RoadAssessment(
                nearestRoad = null,
                distanceToRoadMeters = null,
                accessClass = null,
                roadSuitability = null,
                skippedReason = "Tylko POI punktowe mają ranking drogowy.",
            )
        }
        val nearest = findNearest(poi.latitude, poi.longitude, roads)
            ?: return RoadAssessment(
                nearestRoad = null,
                distanceToRoadMeters = null,
                accessClass = null,
                roadSuitability = RoadSuitability.REJECTED,
                skippedReason = "Brak drogi OSM w promieniu ${searchRadiusMeters.toInt()} m.",
            )

        val accessClass = RoadClassifier.classify(
            highway = nearest.road.type,
            access = nearest.road.access,
            motorVehicle = nearest.road.motorVehicle,
            motorcycle = nearest.road.motorcycle,
            vehicle = nearest.road.vehicle,
        )
        val suitability = suitabilityFor(accessClass, nearest.distanceMeters)
        return RoadAssessment(
            nearestRoad = nearest.road.copy(
                latitude = nearest.nearestLat,
                longitude = nearest.nearestLon,
            ),
            distanceToRoadMeters = nearest.distanceMeters,
            accessClass = accessClass,
            roadSuitability = suitability,
        )
    }

    fun suitabilityFor(accessClass: RoadAccessClass, distanceMeters: Double): RoadSuitability {
        if (accessClass == RoadAccessClass.NOT_ROAD || accessClass == RoadAccessClass.MOTO_RESTRICTED) {
            return RoadSuitability.REJECTED
        }
        return when {
            distanceMeters <= thresholds.excellentMaxMeters -> RoadSuitability.EXCELLENT
            distanceMeters <= thresholds.goodMaxMeters -> RoadSuitability.GOOD
            distanceMeters <= thresholds.weakMaxMeters -> RoadSuitability.WEAK
            else -> RoadSuitability.REJECTED
        }
    }

    data class NearestHit(
        val road: Road,
        val distanceMeters: Double,
        val nearestLat: Double,
        val nearestLon: Double,
    )

    fun findNearest(lat: Double, lon: Double, roads: List<Road>): NearestHit? {
        var bestMotorable: NearestHit? = null
        var bestAny: NearestHit? = null
        for (road in roads) {
            if (road.geometry.size < 2) continue
            val hit = GeoUtils.nearestPointOnPolylineMeters(lat, lon, road.geometry) ?: continue
            val candidate = NearestHit(
                road = road,
                distanceMeters = hit.distanceMeters,
                nearestLat = hit.latitude,
                nearestLon = hit.longitude,
            )
            if (bestAny == null || candidate.distanceMeters < bestAny.distanceMeters) {
                bestAny = candidate
            }
            val access = RoadClassifier.classify(
                highway = road.type,
                access = road.access,
                motorVehicle = road.motorVehicle,
                motorcycle = road.motorcycle,
                vehicle = road.vehicle,
            )
            // Prefer the nearest geometry that is not a pure non-road (path/footway/…).
            if (access != RoadAccessClass.NOT_ROAD) {
                if (bestMotorable == null || candidate.distanceMeters < bestMotorable.distanceMeters) {
                    bestMotorable = candidate
                }
            }
        }
        return bestMotorable ?: bestAny
    }
}
