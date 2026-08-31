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
}
