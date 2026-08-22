package pl.navilas.finder.data.osm

import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.Road

fun interface HighwayAroundFetcher {
    fun fetchHighwaysAround(
        points: List<LatLon>,
        radiusMeters: Double,
    ): List<Road>
}
