package pl.navilas.finder.data.osm

import pl.navilas.finder.domain.Road

fun interface OverpassBboxFetcher {
    fun fetchHighwaysInBbox(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): List<Road>
}
