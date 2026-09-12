package pl.navilas.finder.util

import pl.navilas.finder.domain.LatLon
import kotlin.math.abs

/**
 * Polyline helpers for long imported routes.
 *
 * The rest-site corridor search builds one envelope per query, so a 300–500 km route
 * must be split into shorter chunks — a single bounding box would cover hundreds of km²
 * and load far too many BDL features into memory at once.
 */
object RouteGeometry {
    /** Chunk target length used for corridor searches along imported routes (km). */
    const val DEFAULT_CHUNK_KM = 40.0

    /** Hard cap so a pathological GPX cannot allocate an unbounded envelope list. */
    const val MAX_CHUNKS = 64

    /**
     * Dense point-to-point vertices from a GPX track make corridor search slow without
     * changing the result: [CorridorGeometry.project] only needs shape, not every fix.
     * Keeps first/last vertex and drops points closer than [minSpacingKm] to the previous kept one.
     */
    fun simplify(line: List<LatLon>, minSpacingKm: Double = 0.08): List<LatLon> {
        if (line.size <= 2) return line
        val out = ArrayList<LatLon>(line.size)
        out += line.first()
        for (i in 1 until line.size - 1) {
            val last = out.last()
            if (GeoUtils.distanceKm(last.latitude, last.longitude, line[i].latitude, line[i].longitude) >=
                minSpacingKm
            ) {
                out += line[i]
            }
        }
        val tail = line.last()
        if (out.last() != tail) out += tail
        return out
    }

    /**
     * Splits [line] into consecutive chunks of roughly [chunkKm] length.
     * Consecutive chunks share the boundary vertex, so no point on the route falls
     * between two searches.
     */
    fun chunk(line: List<LatLon>, chunkKm: Double = DEFAULT_CHUNK_KM): List<List<LatLon>> {
        if (line.size < 2) return emptyList()
        require(chunkKm > 0.0) { "chunkKm must be positive" }
        val chunks = ArrayList<List<LatLon>>()
        var current = arrayListOf(line.first())
        var accumulatedKm = 0.0
        for (i in 1 until line.size) {
            val previous = line[i - 1]
            val point = line[i]
            accumulatedKm += abs(
                GeoUtils.distanceKm(
                    previous.latitude,
                    previous.longitude,
                    point.latitude,
                    point.longitude,
                ),
            )
            current += point
            val lastVertex = i == line.size - 1
            if (!lastVertex && accumulatedKm >= chunkKm) {
                chunks += current
                if (chunks.size >= MAX_CHUNKS) return chunks
                // Shared boundary: the vertex just reached opens the next chunk.
                current = arrayListOf(point)
                accumulatedKm = 0.0
            }
        }
        if (current.size >= 2) chunks += current
        return chunks
    }

    /**
     * Distance along a dense GPX track at every vertex.
     * Used to sort results by route kilometre instead of straight-line distance.
     */
    fun cumulativeKm(line: List<LatLon>): DoubleArray {
        val out = DoubleArray(line.size)
        for (i in 1 until line.size) {
            out[i] = out[i - 1] + GeoUtils.distanceKm(
                line[i - 1].latitude,
                line[i - 1].longitude,
                line[i].latitude,
                line[i].longitude,
            )
        }
        return out
    }
}
