package pl.navilas.finder.data.bdl

import pl.navilas.finder.domain.BdlOverlayPoint
import pl.navilas.finder.util.GeoUtils
import java.util.Locale

/**
 * Nearby overlay points for the details dialog only — never drawn on the map.
 * Source is the offline overlay index (same files as Obiekty BDL).
 */
object NearbyOverlayObjects {
    const val DETAILS_RADIUS_METERS = 200.0

    data class Group(
        val name: String,
        val count: Int,
        val minMeters: Double,
        val maxMeters: Double,
    ) {
        fun linePl(): String {
            val label = displayBdlName(name)
            return if (count <= 1) {
                "$label · ${metersLabel(minMeters)}"
            } else {
                "$label ×$count · ${metersLabel(minMeters)}–${metersLabel(maxMeters)}"
            }
        }
    }

    fun groupedWithin(
        points: List<BdlOverlayPoint>,
        latitude: Double,
        longitude: Double,
        excludeId: String,
        radiusMeters: Double = DETAILS_RADIUS_METERS,
    ): List<Group> {
        if (points.isEmpty() || !latitude.isFinite() || !longitude.isFinite()) return emptyList()
        val hits = ArrayList<Pair<BdlOverlayPoint, Double>>()
        for (point in points) {
            if (point.id == excludeId) continue
            val meters = GeoUtils.distanceMeters(
                latitude,
                longitude,
                point.latitude,
                point.longitude,
            )
            if (meters <= radiusMeters) {
                hits += point to meters
            }
        }
        if (hits.isEmpty()) return emptyList()
        return hits
            .groupBy { it.first.name.trim() }
            .map { (name, items) ->
                val distances = items.map { it.second }
                Group(
                    name = name.ifBlank { items.first().first.group.labelPl },
                    count = items.size,
                    minMeters = distances.min(),
                    maxMeters = distances.max(),
                )
            }
            .sortedBy { it.minMeters }
    }

    fun displayBdlName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val letters = trimmed.filter { it.isLetter() }
        if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) {
            val locale = Locale("pl", "PL")
            return trimmed.lowercase(locale).split(Regex("\\s+")).joinToString(" ") { word ->
                if (word == "św." || word == "sw.") {
                    word
                } else {
                    word.replaceFirstChar { it.titlecase(locale) }
                }
            }
        }
        return trimmed
    }

    private fun metersLabel(meters: Double): String =
        "${meters.toInt()} m"
}
