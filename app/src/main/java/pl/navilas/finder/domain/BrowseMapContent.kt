package pl.navilas.finder.domain

import pl.navilas.finder.util.GeoUtils

internal const val BROWSE_SITE_VIEWPORT_MIN_ZOOM = 7.0
internal const val BROWSE_CLUSTER_MAX_ZOOM = 10.0
internal const val BROWSE_SITE_VIEWPORT_MAX_POINTS = 1_500

internal fun isUsableBrowseViewport(
    west: Double,
    south: Double,
    east: Double,
    north: Double,
): Boolean {
    if (!west.isFinite() || !south.isFinite() || !east.isFinite() || !north.isFinite()) {
        return false
    }
    if (east <= west || north <= south) return false
    return (east - west) >= 1e-4 && (north - south) >= 1e-4
}

internal fun selectBrowseContent(
    sites: List<RestSite>,
    envelope: GeoUtils.Envelope,
    centerLat: Double,
    centerLon: Double,
    zoom: Double,
    matchingIds: Set<String>?,
): Pair<List<RestSite>, List<BrowseMapCluster>> {
    val filteredSites = if (matchingIds == null) {
        sites
    } else {
        sites.filter { it.id in matchingIds }
    }
    val ranked = filteredSites.sortedBy { site ->
        val dLat = site.latitude - centerLat
        val dLon = site.longitude - centerLon
        dLat * dLat + dLon * dLon
    }
    val candidates = if (zoom < BROWSE_SITE_VIEWPORT_MIN_ZOOM) {
        ranked
    } else {
        val inViewport = ranked.filter { site ->
            site.latitude >= envelope.ymin &&
                site.latitude <= envelope.ymax &&
                site.longitude >= envelope.xmin &&
                site.longitude <= envelope.xmax
        }
        val visible = if (inViewport.isEmpty() && matchingIds == null) {
            ranked
        } else {
            inViewport
        }
        visible.take(BROWSE_SITE_VIEWPORT_MAX_POINTS)
    }
    if (zoom >= BROWSE_CLUSTER_MAX_ZOOM) return candidates to emptyList()
    val cellDegrees = when {
        zoom < 6.0 -> 1.0
        zoom < 8.0 -> 0.35
        else -> 0.12
    }
    val clusters = candidates
        .groupBy { site ->
            (site.latitude / cellDegrees).toInt() to
                (site.longitude / cellDegrees).toInt()
        }
        .map { (cell, grouped) ->
            BrowseMapCluster(
                id = "${cell.first}:${cell.second}",
                latitude = grouped.map(RestSite::latitude).average(),
                longitude = grouped.map(RestSite::longitude).average(),
                count = grouped.size,
            )
        }
    return emptyList<RestSite>() to clusters
}
