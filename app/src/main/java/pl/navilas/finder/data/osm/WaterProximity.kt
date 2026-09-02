package pl.navilas.finder.data.osm

import pl.navilas.finder.domain.OsmWaterFeature
import pl.navilas.finder.util.GeoUtils

object WaterProximity {
    fun distanceMeters(lat: Double, lon: Double, feature: OsmWaterFeature): Double {
        val geom = feature.geometry
        if (geom.isEmpty()) return Double.POSITIVE_INFINITY
        if (geom.size == 1) {
            return GeoUtils.distanceMeters(lat, lon, geom[0].latitude, geom[0].longitude)
        }
        if (feature.polygon) {
            if (GeoUtils.pointInPolygon(lat, lon, geom)) return 0.0
            return GeoUtils.distanceToPolygonBoundaryMeters(lat, lon, geom)
        }
        return GeoUtils.nearestPointOnPolylineMeters(lat, lon, geom)?.distanceMeters
            ?: Double.POSITIVE_INFINITY
    }

    fun isWithin(
        lat: Double,
        lon: Double,
        feature: OsmWaterFeature,
        radiusMeters: Double,
    ): Boolean {
        if (!envelopeHits(lat, lon, feature, radiusMeters)) return false
        return distanceMeters(lat, lon, feature) <= radiusMeters
    }

    fun anyWithin(
        lat: Double,
        lon: Double,
        features: List<OsmWaterFeature>,
        radiusMeters: Double,
    ): Boolean = features.any { isWithin(lat, lon, it, radiusMeters) }

    private fun envelopeHits(
        lat: Double,
        lon: Double,
        feature: OsmWaterFeature,
        radiusMeters: Double,
    ): Boolean {
        val padDeg = (radiusMeters / 111_000.0) * 1.2
        return lat in (feature.minLat - padDeg)..(feature.maxLat + padDeg) &&
            lon in (feature.minLon - padDeg)..(feature.maxLon + padDeg)
    }
}
