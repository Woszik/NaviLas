package pl.navilas.finder.data.bdl

import pl.navilas.finder.domain.ForestEntryBan
import pl.navilas.finder.util.GeoUtils

/** Point-in-polygon against loaded viewport bans. Never uses centroids. */
object ForestEntryBanClassifier {
    fun containing(
        latitude: Double,
        longitude: Double,
        bans: List<ForestEntryBan>,
    ): ForestEntryBan? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        return bans.firstOrNull { ban ->
            ban.rings.isNotEmpty() && GeoUtils.pointInPolygonRings(latitude, longitude, ban.rings)
        }
    }

    fun inEnvelope(
        index: List<ForestEntryBanBounds>,
        envelope: GeoUtils.Envelope,
        centerLat: Double,
        centerLon: Double,
        limit: Int,
    ): List<ForestEntryBan> =
        index.asSequence()
            .filter { it.intersects(envelope) }
            .sortedBy {
                val dLat = it.centerLat() - centerLat
                val dLon = it.centerLon() - centerLon
                dLat * dLat + dLon * dLon
            }
            .take(limit.coerceAtLeast(1))
            .map { it.ban }
            .toList()
}
