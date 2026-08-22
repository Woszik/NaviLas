package pl.navilas.finder.data.bdl

import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.util.GeoUtils

data class ZanocujPolygon(
    val id: String,
    val name: String?,
    /** ArcGIS rings: exterior(s) and holes. Membership uses even-odd across all rings. */
    val rings: List<List<LatLon>>,
)

data class ZanocujEvaluation(
    val status: ZanocujStatus,
    val distanceToBoundaryMeters: Double?,
)

/**
 * Classifies a rest-site point against Zanocuj area polygons.
 * Never uses polygon centroids for membership.
 */
object ZanocujClassifier {
    fun evaluate(
        latitude: Double,
        longitude: Double,
        polygons: List<ZanocujPolygon>,
        config: SearchConfig = SearchConfig.DEFAULT,
    ): ZanocujEvaluation {
        if (polygons.isEmpty()) {
            return ZanocujEvaluation(ZanocujStatus.OUTSIDE_ZONE, null)
        }
        var best = ZanocujEvaluation(ZanocujStatus.OUTSIDE_ZONE, null)
        for (polygon in polygons) {
            if (polygon.rings.isEmpty()) continue
            val candidate = evaluateAgainstPolygon(latitude, longitude, polygon.rings, config)
            best = better(best, candidate)
            if (best.status == ZanocujStatus.IN_ZONE) return best
        }
        return best
    }

    fun evaluateAgainstPolygon(
        latitude: Double,
        longitude: Double,
        rings: List<List<LatLon>>,
        config: SearchConfig,
    ): ZanocujEvaluation {
        if (rings.isEmpty()) {
            return ZanocujEvaluation(ZanocujStatus.OUTSIDE_ZONE, null)
        }
        if (GeoUtils.pointInPolygonRings(latitude, longitude, rings)) {
            return ZanocujEvaluation(ZanocujStatus.IN_ZONE, 0.0)
        }
        val distance = rings.minOf { ring ->
            GeoUtils.distanceToPolygonBoundaryMeters(latitude, longitude, ring)
        }
        return if (distance <= config.zanocujNearZoneMeters) {
            ZanocujEvaluation(ZanocujStatus.NEAR_ZONE, distance)
        } else {
            ZanocujEvaluation(ZanocujStatus.OUTSIDE_ZONE, distance)
        }
    }

    fun evaluateAgainstRing(
        latitude: Double,
        longitude: Double,
        ring: List<LatLon>,
        config: SearchConfig,
    ): ZanocujEvaluation = evaluateAgainstPolygon(latitude, longitude, listOf(ring), config)

    fun better(a: ZanocujEvaluation, b: ZanocujEvaluation): ZanocujEvaluation {
        val rank = mapOf(
            ZanocujStatus.IN_ZONE to 2,
            ZanocujStatus.NEAR_ZONE to 1,
            ZanocujStatus.OUTSIDE_ZONE to 0,
        )
        val aRank = rank[a.status] ?: 0
        val bRank = rank[b.status] ?: 0
        return when {
            bRank > aRank -> b
            bRank < aRank -> a
            // Same status: prefer smaller boundary distance when both NEAR/OUTSIDE.
            else -> {
                val ad = a.distanceToBoundaryMeters
                val bd = b.distanceToBoundaryMeters
                if (ad != null && bd != null && bd < ad) b else a
            }
        }
    }
}
