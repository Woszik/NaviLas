package pl.navilas.finder.data.route

import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.RoutePlan
import pl.navilas.finder.domain.RouteWaypoint
import pl.navilas.finder.domain.RouteWaypointKind
import java.io.File

/**
 * Persists the imported route plan so it survives an app restart.
 *
 * Deliberately a plain JSON file rather than SharedPreferences: route geometry can hold
 * thousands of vertices. Tracks are only session state — a hand-drawn corridor is not saved.
 */
class RoutePlanStore(
    private val file: File,
) {
    fun load(): RoutePlan {
        if (!file.exists()) return RoutePlan()
        return runCatching {
            val root = JSONObject(file.readText())
            val line = root.optJSONArray(KEY_LINE)?.let { parseLine(it) } ?: emptyList()
            val waypoints = root.optJSONArray(KEY_WAYPOINTS)?.let { parseWaypoints(it) } ?: emptyList()
            RoutePlan(
                waypoints = waypoints,
                line = line,
                sourceName = root.optString(KEY_SOURCE).takeIf { it.isNotBlank() },
            )
        }.getOrElse { RoutePlan() }
    }

    fun save(plan: RoutePlan) {
        if (plan.line.isEmpty() && plan.waypoints.isEmpty()) {
            clear()
            return
        }
        runCatching {
            file.parentFile?.mkdirs()
            val root = JSONObject()
                .put(KEY_SOURCE, plan.sourceName.orEmpty())
                .put(KEY_LINE, encodeLine(plan.line))
                .put(KEY_WAYPOINTS, encodeWaypoints(plan.waypoints))
            file.writeText(root.toString())
        }
    }

    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }

    /** Flat `[lat, lon, lat, lon, …]` keeps the file small for long tracks. */
    private fun encodeLine(line: List<LatLon>): JSONArray {
        val array = JSONArray()
        line.forEach { point ->
            array.put(point.latitude)
            array.put(point.longitude)
        }
        return array
    }

    private fun parseLine(array: JSONArray): List<LatLon> {
        val points = ArrayList<LatLon>(array.length() / 2)
        var i = 0
        while (i + 1 < array.length()) {
            val lat = array.optDouble(i, Double.NaN)
            val lon = array.optDouble(i + 1, Double.NaN)
            if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                points += LatLon(lat, lon)
            }
            i += 2
        }
        return points
    }

    private fun encodeWaypoints(waypoints: List<RouteWaypoint>): JSONArray {
        val array = JSONArray()
        waypoints.forEach { waypoint ->
            array.put(
                JSONObject()
                    .put("id", waypoint.id)
                    .put("kind", waypoint.kind.name)
                    .put("name", waypoint.name)
                    .put("lat", waypoint.latitude)
                    .put("lon", waypoint.longitude),
            )
        }
        return array
    }

    private fun parseWaypoints(array: JSONArray): List<RouteWaypoint> {
        val out = ArrayList<RouteWaypoint>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val lat = item.optDouble("lat", Double.NaN)
            val lon = item.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue
            out += RouteWaypoint(
                id = item.optString("id").ifBlank { "wp-$i" },
                kind = RouteWaypointKind.entries
                    .firstOrNull { it.name == item.optString("kind") }
                    ?: RouteWaypointKind.WAYPOINT,
                name = item.optString("name").ifBlank { "Punkt ${i + 1}" },
                position = LatLon(lat, lon),
            )
        }
        // Exactly one target, and it must be the finish.
        val normalized = out.toMutableList()
        val targetIndex = normalized.indexOfLast { it.kind == RouteWaypointKind.TARGET }
        if (targetIndex == -1) {
            if (normalized.isNotEmpty()) {
                normalized[normalized.lastIndex] =
                    normalized.last().copy(kind = RouteWaypointKind.TARGET)
            }
        } else if (targetIndex != normalized.lastIndex) {
            val target = normalized.removeAt(targetIndex).copy(kind = RouteWaypointKind.TARGET)
            normalized += target
        }
        return normalized
    }

    companion object {
        const val FILE_NAME = "route_plan.json"
        private const val KEY_SOURCE = "sourceName"
        private const val KEY_LINE = "line"
        private const val KEY_WAYPOINTS = "waypoints"

        fun fromAppFilesDir(filesDir: File): RoutePlanStore =
            RoutePlanStore(File(filesDir, FILE_NAME))
    }
}
