package pl.navilas.finder.data.osm

import pl.navilas.finder.data.cache.OsmRoadTileGrid
import pl.navilas.finder.data.cache.OsmRoadTileKey
import pl.navilas.finder.data.cache.PersistentOsmRoadTileStore
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.Road

/**
 * Fetches OSM highways with regional tile cache (Package B).
 * Missing tiles are loaded via bbox Overpass query; hits skip the network.
 */
class CachingOverpassRoadClient(
    private val delegate: OverpassBboxFetcher = OverpassRoadClient(),
    private val tileCache: PersistentOsmRoadTileStore,
) : HighwayAroundFetcher {
    override fun fetchHighwaysAround(
        points: List<LatLon>,
        radiusMeters: Double,
    ): List<Road> {
        if (points.isEmpty()) return emptyList()
        val tileKeys = OsmRoadTileGrid.tilesForPoints(
            latitudes = points.map { it.latitude },
            longitudes = points.map { it.longitude },
            marginMeters = radiusMeters,
        )
        val roads = LinkedHashMap<String, Road>()
        val missing = ArrayList<OsmRoadTileKey>()
        tileKeys.forEach { key ->
            val cached = tileCache.get(key)
            if (cached != null) {
                cached.forEach { road -> roads.putIfAbsent(road.id, road) }
            } else {
                missing += key
            }
        }
        missing.forEach { key ->
            val bbox = OsmRoadTileGrid.bbox(key)
            val fetched = delegate.fetchHighwaysInBbox(
                south = bbox.south,
                west = bbox.west,
                north = bbox.north,
                east = bbox.east,
            )
            tileCache.put(key, fetched)
            fetched.forEach { road -> roads.putIfAbsent(road.id, road) }
        }
        return roads.values.toList()
    }
}
