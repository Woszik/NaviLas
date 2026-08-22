package pl.navilas.finder.util

import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.Poi
import pl.navilas.finder.domain.PoiGeometryKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle (haversine) distance in kilometres. */
    fun distanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Helper distance used for ranking / client radius filter.
     * - POINT: distance to the point
     * - AREA: minimum distance to centroid or any ring vertex (centroid alone is not treated as destination)
     */
    fun distanceToPoiKm(
        userLat: Double,
        userLon: Double,
        poi: Poi,
    ): Double {
        return when (poi.geometryKind) {
            PoiGeometryKind.POINT -> distanceKm(userLat, userLon, poi.latitude, poi.longitude)
            PoiGeometryKind.AREA -> {
                val samples = buildList {
                    add(LatLon(poi.latitude, poi.longitude))
                    poi.areaRings.forEach { ring -> addAll(ring) }
                }
                samples.minOf { sample ->
                    distanceKm(userLat, userLon, sample.latitude, sample.longitude)
                }
            }
        }
    }

    fun isWithinRadiusKm(
        userLat: Double,
        userLon: Double,
        poi: Poi,
        radiusKm: Double,
    ): Boolean = distanceToPoiKm(userLat, userLon, poi) <= radiusKm

    /** Axis-aligned envelope approximating a circle of [radiusKm] around a WGS84 point. */
    fun envelopeAround(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): Envelope {
        val deltaLat = radiusKm / 111.0
        val cosLat = cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        val deltaLon = radiusKm / (111.0 * cosLat)
        return Envelope(
            xmin = longitude - deltaLon,
            ymin = latitude - deltaLat,
            xmax = longitude + deltaLon,
            ymax = latitude + deltaLat,
        )
    }

    /**
     * Simple polygon centroid from ArcGIS rings (outer ring only).
     * Coordinates are [lon, lat] pairs.
     * For AREA POIs this is a presentation/helper value only.
     */
    fun ringCentroid(ring: List<DoubleArray>): Pair<Double, Double>? {
        if (ring.size < 3) return null
        var area = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in 0 until ring.size - 1) {
            val x0 = ring[i][0]
            val y0 = ring[i][1]
            val x1 = ring[i + 1][0]
            val y1 = ring[i + 1][1]
            val cross = x0 * y1 - x1 * y0
            area += cross
            cx += (x0 + x1) * cross
            cy += (y0 + y1) * cross
        }
        area *= 0.5
        if (kotlin.math.abs(area) < 1e-12) {
            val avgLon = ring.dropLast(1).map { it[0] }.average()
            val avgLat = ring.dropLast(1).map { it[1] }.average()
            return avgLon to avgLat
        }
        cx /= (6.0 * area)
        cy /= (6.0 * area)
        return cx to cy
    }

    data class Envelope(
        val xmin: Double,
        val ymin: Double,
        val xmax: Double,
        val ymax: Double,
    )

    data class NearestOnLine(
        val distanceMeters: Double,
        val latitude: Double,
        val longitude: Double,
    )

    /**
     * Approximate nearest point on a polyline using local equirectangular projection.
     * Adequate for short OSM segments (tens–hundreds of meters).
     */
    fun nearestPointOnPolylineMeters(
        lat: Double,
        lon: Double,
        line: List<LatLon>,
    ): NearestOnLine? {
        if (line.size < 2) return null
        var bestDist = Double.MAX_VALUE
        var bestLat = line.first().latitude
        var bestLon = line.first().longitude
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        fun toXY(p: LatLon): Pair<Double, Double> {
            val x = Math.toRadians(p.longitude - lon) * cosLat * EARTH_RADIUS_KM * 1000.0
            val y = Math.toRadians(p.latitude - lat) * EARTH_RADIUS_KM * 1000.0
            return x to y
        }
        val origin = 0.0 to 0.0
        for (i in 0 until line.size - 1) {
            val a = toXY(line[i])
            val b = toXY(line[i + 1])
            val (px, py, t) = projectPointToSegment(origin, a, b)
            val dist = sqrt(px * px + py * py)
            if (dist < bestDist) {
                bestDist = dist
                // Convert projected meters back to lat/lon
                bestLat = lat + Math.toDegrees(py / (EARTH_RADIUS_KM * 1000.0))
                bestLon = lon + Math.toDegrees(px / (EARTH_RADIUS_KM * 1000.0 * cosLat))
                // Clamp t usage already in projection; keep continuity with endpoints
                if (t <= 0.0) {
                    bestLat = line[i].latitude
                    bestLon = line[i].longitude
                } else if (t >= 1.0) {
                    bestLat = line[i + 1].latitude
                    bestLon = line[i + 1].longitude
                }
            }
        }
        return NearestOnLine(distanceMeters = bestDist, latitude = bestLat, longitude = bestLon)
    }

    private fun projectPointToSegment(
        p: Pair<Double, Double>,
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
    ): Triple<Double, Double, Double> {
        val abx = b.first - a.first
        val aby = b.second - a.second
        val apx = p.first - a.first
        val apy = p.second - a.second
        val abLen2 = abx * abx + aby * aby
        if (abLen2 < 1e-9) {
            return Triple(a.first - p.first, a.second - p.second, 0.0)
        }
        val t = ((apx * abx + apy * aby) / abLen2).coerceIn(0.0, 1.0)
        val cx = a.first + t * abx
        val cy = a.second + t * aby
        return Triple(cx - p.first, cy - p.second, t)
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        distanceKm(lat1, lon1, lat2, lon2) * 1000.0

    /**
     * Ray-casting point-in-polygon. Points exactly on an edge count as inside (IN_ZONE).
     * Ring vertices are [LatLon] in WGS84; ring may be open or closed.
     */
    fun pointInPolygon(lat: Double, lon: Double, ring: List<LatLon>): Boolean {
        if (ring.size < 3) return false
        if (pointOnRingBoundary(lat, lon, ring)) return true
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val yi = ring[i].latitude
            val xi = ring[i].longitude
            val yj = ring[j].latitude
            val xj = ring[j].longitude
            val intersect = ((yi > lat) != (yj > lat)) &&
                (lon < (xj - xi) * (lat - yi) / (yj - yi + 1e-15) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * Point in ArcGIS multi-ring polygon (exteriors + holes) via even-odd fill rule.
     * A point inside an exterior but also inside a hole is outside.
     */
    fun pointInPolygonRings(lat: Double, lon: Double, rings: List<List<LatLon>>): Boolean {
        if (rings.isEmpty()) return false
        var inside = false
        for (ring in rings) {
            if (pointInPolygon(lat, lon, ring)) {
                inside = !inside
            }
        }
        return inside
    }

    fun pointOnRingBoundary(lat: Double, lon: Double, ring: List<LatLon>, epsMeters: Double = 1.0): Boolean {
        if (ring.size < 2) return false
        val closed = if (ring.first() == ring.last()) ring else ring + ring.first()
        for (i in 0 until closed.size - 1) {
            val hit = nearestPointOnPolylineMeters(lat, lon, listOf(closed[i], closed[i + 1]))
            if (hit != null && hit.distanceMeters <= epsMeters) return true
        }
        return false
    }

    /** Minimum distance in metres from a point to a polygon outer ring boundary. */
    fun distanceToPolygonBoundaryMeters(lat: Double, lon: Double, ring: List<LatLon>): Double {
        if (ring.size < 2) return Double.POSITIVE_INFINITY
        val closed = if (ring.first() == ring.last()) ring else ring + ring.first()
        return nearestPointOnPolylineMeters(lat, lon, closed)?.distanceMeters
            ?: Double.POSITIVE_INFINITY
    }
}
