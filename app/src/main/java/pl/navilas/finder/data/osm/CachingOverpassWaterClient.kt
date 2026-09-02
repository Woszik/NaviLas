package pl.navilas.finder.data.osm

import pl.navilas.finder.data.cache.OsmRoadTileGrid
import pl.navilas.finder.data.cache.OsmRoadTileKey
import pl.navilas.finder.data.cache.PersistentOsmWaterTileStore
import pl.navilas.finder.domain.OsmWaterFeature
import pl.navilas.finder.domain.RestSite

/**
 * OSM water around rest sites, using the same ~5 km tile grid as highway cache.
 */
class CachingOverpassWaterClient(
    private val delegate: OverpassWaterClient = OverpassWaterClient(),
    private val tileCache: PersistentOsmWaterTileStore,
) {
    fun featuresAround(sites: List<RestSite>, radiusMeters: Double): List<OsmWaterFeature> {
        if (sites.isEmpty()) return emptyList()
        val keys = OsmRoadTileGrid.tilesForPoints(
            latitudes = sites.map { it.latitude },
            longitudes = sites.map { it.longitude },
            marginMeters = radiusMeters,
        )
        return featuresForTiles(keys)
    }

    fun siteIdsNearWater(sites: List<RestSite>, radiusMeters: Double): Set<String> {
        if (sites.isEmpty()) return emptySet()
        val features = featuresAround(sites, radiusMeters)
        if (features.isEmpty()) return emptySet()
        return sites.mapNotNull { site ->
            if (WaterProximity.anyWithin(site.latitude, site.longitude, features, radiusMeters)) {
                site.id
            } else {
                null
            }
        }.toHashSet()
    }

    private fun featuresForTiles(keys: Set<OsmRoadTileKey>): List<OsmWaterFeature> {
        val out = LinkedHashMap<String, OsmWaterFeature>()
        keys.forEach { key ->
            val cached = tileCache.get(key)
            if (cached != null) {
                cached.forEach { out.putIfAbsent(it.id, it) }
            } else {
                val bbox = OsmRoadTileGrid.bbox(key)
                val fetched = delegate.fetchWaterInBbox(
                    south = bbox.south,
                    west = bbox.west,
                    north = bbox.north,
                    east = bbox.east,
                )
                tileCache.put(key, fetched)
                fetched.forEach { out.putIfAbsent(it.id, it) }
            }
        }
        return out.values.toList()
    }
}
