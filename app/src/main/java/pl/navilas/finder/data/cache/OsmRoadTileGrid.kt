package pl.navilas.finder.data.cache

import kotlin.math.floor

data class OsmRoadTileKey(
    val latIndex: Int,
    val lonIndex: Int,
)

data class OsmRoadTileBbox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

/**
 * ~5 km grid for regional OSM highway cache (Package B).
 */
object OsmRoadTileGrid {
    const val TILE_SIZE_DEG = 0.05

    fun key(latitude: Double, longitude: Double): OsmRoadTileKey = OsmRoadTileKey(
        latIndex = tileIndex(latitude),
        lonIndex = tileIndex(longitude),
    )

    fun tilesForPoint(
        latitude: Double,
        longitude: Double,
        marginMeters: Double,
    ): Set<OsmRoadTileKey> {
        val marginDeg = marginMeters / 111_000.0
        val base = key(latitude, longitude)
        val keys = linkedSetOf(base)
        val latInTile = latitude - base.latIndex * TILE_SIZE_DEG
        val lonInTile = longitude - base.lonIndex * TILE_SIZE_DEG
        if (latInTile < marginDeg) addRow(keys, base.latIndex - 1, base.lonIndex, marginDeg, lonInTile)
        if (TILE_SIZE_DEG - latInTile < marginDeg) addRow(keys, base.latIndex + 1, base.lonIndex, marginDeg, lonInTile)
        if (lonInTile < marginDeg) addColumn(keys, base.latIndex, base.lonIndex - 1, marginDeg, latInTile)
        if (TILE_SIZE_DEG - lonInTile < marginDeg) addColumn(keys, base.latIndex, base.lonIndex + 1, marginDeg, latInTile)
        if (latInTile < marginDeg && lonInTile < marginDeg) {
            keys += OsmRoadTileKey(base.latIndex - 1, base.lonIndex - 1)
        }
        if (latInTile < marginDeg && TILE_SIZE_DEG - lonInTile < marginDeg) {
            keys += OsmRoadTileKey(base.latIndex - 1, base.lonIndex + 1)
        }
        if (TILE_SIZE_DEG - latInTile < marginDeg && lonInTile < marginDeg) {
            keys += OsmRoadTileKey(base.latIndex + 1, base.lonIndex - 1)
        }
        if (TILE_SIZE_DEG - latInTile < marginDeg && TILE_SIZE_DEG - lonInTile < marginDeg) {
            keys += OsmRoadTileKey(base.latIndex + 1, base.lonIndex + 1)
        }
        return keys
    }

    fun tilesForPoints(
        latitudes: List<Double>,
        longitudes: List<Double>,
        marginMeters: Double,
    ): Set<OsmRoadTileKey> {
        require(latitudes.size == longitudes.size)
        val keys = linkedSetOf<OsmRoadTileKey>()
        latitudes.indices.forEach { i ->
            keys += tilesForPoint(latitudes[i], longitudes[i], marginMeters)
        }
        return keys
    }

    fun bbox(key: OsmRoadTileKey): OsmRoadTileBbox = OsmRoadTileBbox(
        south = key.latIndex * TILE_SIZE_DEG,
        west = key.lonIndex * TILE_SIZE_DEG,
        north = (key.latIndex + 1) * TILE_SIZE_DEG,
        east = (key.lonIndex + 1) * TILE_SIZE_DEG,
    )

    private fun tileIndex(coordinate: Double): Int = floor(coordinate / TILE_SIZE_DEG).toInt()

    private fun addRow(
        keys: MutableSet<OsmRoadTileKey>,
        latIndex: Int,
        lonIndex: Int,
        marginDeg: Double,
        lonInTile: Double,
    ) {
        keys += OsmRoadTileKey(latIndex, lonIndex)
        if (lonInTile < marginDeg) keys += OsmRoadTileKey(latIndex, lonIndex - 1)
        if (TILE_SIZE_DEG - lonInTile < marginDeg) keys += OsmRoadTileKey(latIndex, lonIndex + 1)
    }

    private fun addColumn(
        keys: MutableSet<OsmRoadTileKey>,
        latIndex: Int,
        lonIndex: Int,
        marginDeg: Double,
        latInTile: Double,
    ) {
        keys += OsmRoadTileKey(latIndex, lonIndex)
        if (latInTile < marginDeg) keys += OsmRoadTileKey(latIndex - 1, lonIndex)
        if (TILE_SIZE_DEG - latInTile < marginDeg) keys += OsmRoadTileKey(latIndex + 1, lonIndex)
    }
}
