package pl.navilas.finder.util

import pl.navilas.finder.domain.LatLon
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Asymmetric corridor around a polyline (virtual route).
 * Signed side: positive cross-track = left of travel direction, negative = right.
 */
object CorridorGeometry {
    private const val EARTH_RADIUS_KM = 6371.0

    data class Projection(
        /** Distance along the polyline from the first vertex to the nearest point (km). */
        val distanceAlongKm: Double,
        /** Absolute distance to the nearest segment (km). */
        val crossTrackKm: Double,
        /** True when the point is on the right side of travel direction (A→B). */
        val onRight: Boolean,
        val nearestLatitude: Double,
        val nearestLongitude: Double,
    )

    fun project(latitude: Double, longitude: Double, line: List<LatLon>): Projection? {
        if (line.size < 2) return null
        var bestDistM = Double.MAX_VALUE
        var bestAlongM = 0.0
        var bestOnRight = false
        var bestLat = line.first().latitude
        var bestLon = line.first().longitude
        var prefixM = 0.0

        for (i in 0 until line.size - 1) {
            val a = line[i]
            val b = line[i + 1]
            val segLenM = GeoUtils.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            val hit = projectToSegment(latitude, longitude, a, b) ?: continue
            if (hit.distanceMeters < bestDistM) {
                bestDistM = hit.distanceMeters
                bestAlongM = prefixM + hit.alongMeters
                bestOnRight = hit.onRight
                bestLat = hit.latitude
                bestLon = hit.longitude
            }
            prefixM += segLenM
        }
        if (bestDistM == Double.MAX_VALUE) return null
        return Projection(
            distanceAlongKm = bestAlongM / 1000.0,
            crossTrackKm = bestDistM / 1000.0,
            onRight = bestOnRight,
            nearestLatitude = bestLat,
            nearestLongitude = bestLon,
        )
    }

    fun isInside(
        latitude: Double,
        longitude: Double,
        line: List<LatLon>,
        leftKm: Double,
        rightKm: Double,
    ): Boolean {
        val p = project(latitude, longitude, line) ?: return false
        return if (p.onRight) {
            p.crossTrackKm <= rightKm
        } else {
            p.crossTrackKm <= leftKm
        }
    }

    fun envelope(
        line: List<LatLon>,
        leftKm: Double,
        rightKm: Double,
    ): GeoUtils.Envelope {
        require(line.isNotEmpty()) { "line must not be empty" }
        val marginKm = maxOf(leftKm, rightKm).coerceAtLeast(0.1)
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        for (p in line) {
            val env = GeoUtils.envelopeAround(p.latitude, p.longitude, marginKm)
            minLat = minOf(minLat, env.ymin)
            maxLat = maxOf(maxLat, env.ymax)
            minLon = minOf(minLon, env.xmin)
            maxLon = maxOf(maxLon, env.xmax)
        }
        return GeoUtils.Envelope(xmin = minLon, ymin = minLat, xmax = maxLon, ymax = maxLat)
    }

    fun totalLengthKm(line: List<LatLon>): Double {
        if (line.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until line.size - 1) {
            sum += GeoUtils.distanceKm(
                line[i].latitude,
                line[i].longitude,
                line[i + 1].latitude,
                line[i + 1].longitude,
            )
        }
        return sum
    }

    private data class SegmentHit(
        val distanceMeters: Double,
        val alongMeters: Double,
        val onRight: Boolean,
        val latitude: Double,
        val longitude: Double,
    )

    private fun projectToSegment(
        lat: Double,
        lon: Double,
        a: LatLon,
        b: LatLon,
    ): SegmentHit? {
        val midLat = (a.latitude + b.latitude) / 2.0
        val cosLat = cos(Math.toRadians(midLat)).coerceAtLeast(0.01)
        fun toXY(pLat: Double, pLon: Double): Pair<Double, Double> {
            val x = Math.toRadians(pLon - a.longitude) * cosLat * EARTH_RADIUS_KM * 1000.0
            val y = Math.toRadians(pLat - a.latitude) * EARTH_RADIUS_KM * 1000.0
            return x to y
        }
        val axy = 0.0 to 0.0
        val bxy = toXY(b.latitude, b.longitude)
        val pxy = toXY(lat, lon)
        val abx = bxy.first - axy.first
        val aby = bxy.second - axy.second
        val apx = pxy.first - axy.first
        val apy = pxy.second - axy.second
        val abLen2 = abx * abx + aby * aby
        if (abLen2 < 1e-6) {
            val dist = sqrt(apx * apx + apy * apy)
            return SegmentHit(
                distanceMeters = dist,
                alongMeters = 0.0,
                onRight = false,
                latitude = a.latitude,
                longitude = a.longitude,
            )
        }
        val t = ((apx * abx + apy * aby) / abLen2).coerceIn(0.0, 1.0)
        val cx = axy.first + t * abx
        val cy = axy.second + t * aby
        val dx = pxy.first - cx
        val dy = pxy.second - cy
        val dist = sqrt(dx * dx + dy * dy)
        // Cross product ab × ap: negative => right of travel direction (east-north frame).
        val cross = abx * apy - aby * apx
        val onRight = cross < 0.0
        val nearestLat = a.latitude + Math.toDegrees(cy / (EARTH_RADIUS_KM * 1000.0))
        val nearestLon = a.longitude + Math.toDegrees(cx / (EARTH_RADIUS_KM * 1000.0 * cosLat))
        val along = t * sqrt(abLen2)
        return SegmentHit(
            distanceMeters = dist,
            alongMeters = along,
            onRight = onRight,
            latitude = nearestLat,
            longitude = nearestLon,
        )
    }
}
