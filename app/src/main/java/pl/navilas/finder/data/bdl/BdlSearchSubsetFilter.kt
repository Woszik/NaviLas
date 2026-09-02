package pl.navilas.finder.data.bdl

import pl.navilas.finder.data.cache.BdlSearchSessionCache
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.util.GeoUtils

data class BdlSearchContext(
    val originLat: Double,
    val originLon: Double,
    val radiusKm: Double,
    val offlineVersion: Long,
    val bundle: RestSearchBundle,
) {
    fun roundedOrigin(): Pair<Double, Double> = Pair(
        BdlSearchSessionCache.roundCoord(originLat),
        BdlSearchSessionCache.roundCoord(originLon),
    )
}

/**
 * Reuses a previous (larger-radius) BDL result when origin and data source match.
 */
object BdlSearchSubsetFilter {
    fun canReuse(
        context: BdlSearchContext?,
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        offlineVersion: Long,
    ): Boolean {
        if (context == null || context.bundle.sites.isEmpty()) return false
        if (offlineVersion != context.offlineVersion) return false
        if (radiusKm > context.radiusKm) return false
        val (ctxLat, ctxLon) = context.roundedOrigin()
        val lat = BdlSearchSessionCache.roundCoord(latitude)
        val lon = BdlSearchSessionCache.roundCoord(longitude)
        return lat == ctxLat && lon == ctxLon
    }

    fun subset(
        context: BdlSearchContext,
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        config: SearchConfig = SearchConfig.DEFAULT,
    ): RestSearchBundle {
        val sites = context.bundle.sites.filter { site ->
            GeoUtils.distanceKm(latitude, longitude, site.latitude, site.longitude) <= radiusKm
        }
        val vehicleSites = context.bundle.vehicleSites.filter { site ->
            GeoUtils.distanceKm(latitude, longitude, site.latitude, site.longitude) <= radiusKm
        }
        val marginKm = config.zanocujNearZoneMeters / 1000.0
        val polygons = context.bundle.zanocujPolygons.filter { polygon ->
            polygon.rings.flatten().any { point ->
                GeoUtils.distanceKm(latitude, longitude, point.latitude, point.longitude) <=
                    radiusKm + marginKm
            }
        }
        return RestSearchBundle(sites = sites, zanocujPolygons = polygons, vehicleSites = vehicleSites)
    }
}
