package pl.navilas.finder.domain

import pl.navilas.finder.util.GeoUtils

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

@Suppress("UNUSED_PARAMETER")
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
    val inViewport = filteredSites.filter { site ->
        site.latitude >= envelope.ymin &&
            site.latitude <= envelope.ymax &&
            site.longitude >= envelope.xmin &&
            site.longitude <= envelope.xmax
    }
    return inViewport to emptyList()
}
