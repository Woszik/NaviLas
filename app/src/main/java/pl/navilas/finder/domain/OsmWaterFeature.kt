package pl.navilas.finder.domain

/** OSM waterway / waterbody used by the near-water filter (not BDL). */
data class OsmWaterFeature(
    val id: String,
    val polygon: Boolean,
    val geometry: List<LatLon>,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
) {
    companion object {
        fun of(id: String, polygon: Boolean, geometry: List<LatLon>): OsmWaterFeature? {
            if (geometry.isEmpty()) return null
            return OsmWaterFeature(
                id = id,
                polygon = polygon && geometry.size >= 3,
                geometry = geometry,
                minLat = geometry.minOf { it.latitude },
                minLon = geometry.minOf { it.longitude },
                maxLat = geometry.maxOf { it.latitude },
                maxLon = geometry.maxOf { it.longitude },
            )
        }
    }
}
