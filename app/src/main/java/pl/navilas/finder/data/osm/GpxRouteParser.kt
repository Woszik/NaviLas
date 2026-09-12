package pl.navilas.finder.data.osm

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.RoutePlan
import pl.navilas.finder.domain.RouteWaypoint
import pl.navilas.finder.domain.RouteWaypointKind
import pl.navilas.finder.util.GeoUtils
import pl.navilas.finder.util.RouteGeometry
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

/**
 * Reads a GPX produced by an external planner (OsmAnd "Plan a route" → share track).
 *
 * Geometry comes from `<trk>/<trkseg>/<trkpt>` (OsmAnd exports the calculated route
 * as a track) and falls back to `<rte>/<rtept>` for planners that only export a route.
 * Waypoint names come from `<rtept><name>`; a track without route points gets only the
 * line, and the target is derived from the last vertex.
 *
 * SAX (not `android.util.Xml`) so the parser stays unit-testable off-device.
 */
object GpxRouteParser {
    /** NaviLas keeps routes in memory, so refuse pathological files early. */
    const val MAX_FILE_BYTES = 12 * 1024 * 1024
    private const val MAX_LINE_POINTS = 20_000
    private const val MAX_WAYPOINTS = 200
    private const val MAX_TRACKS = 20

    data class ParseResult(
        val route: RoutePlan,
        /** True when several tracks were present and only the longest was kept. */
        val multipleTracks: Boolean,
        val trackCount: Int,
    )

    sealed class Result {
        data class Success(val value: ParseResult) : Result()
        data class Failure(val message: String) : Result()
    }

    fun parse(input: InputStream, sourceName: String?): Result {
        val handler = Handler()
        return try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                isValidating = false
                // Imported files are untrusted; never resolve external references.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            factory.newSAXParser().parse(InputSource(input), handler)
            handler.finish(sourceName)
        } catch (error: Exception) {
            Result.Failure("Nie udało się odczytać GPX: ${error.message ?: "błąd XML"}.")
        }
    }

    private class Handler : DefaultHandler() {
        private val tracks = mutableListOf<List<LatLon>>()
        private val line = mutableListOf<LatLon>()
        private val waypoints = mutableListOf<RouteWaypoint>()

        private var inTrack = false
        private var inRoutePoint = false
        private var currentName: String? = null
        private var currentLat: Double? = null
        private var currentLon: Double? = null
        private val text = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes?) {
            when (qName.lowercase(Locale.US)) {
                "trk" -> inTrack = true

                "trkpt" -> {
                    val lat = attrs?.getValue("lat")?.toDoubleOrNull()
                    val lon = attrs?.getValue("lon")?.toDoubleOrNull()
                    if (lat != null && lon != null && isValid(lat, lon) && line.size < MAX_LINE_POINTS) {
                        line += LatLon(lat, lon)
                    }
                }

                "rtept" -> {
                    val lat = attrs?.getValue("lat")?.toDoubleOrNull()
                    val lon = attrs?.getValue("lon")?.toDoubleOrNull()
                    if (lat != null && lon != null && isValid(lat, lon)) {
                        inRoutePoint = true
                        currentLat = lat
                        currentLon = lon
                        currentName = null
                    }
                }

                "name" -> text.setLength(0)
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (ch != null) text.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (qName.lowercase(Locale.US)) {
                "trk" -> {
                    if (inTrack && line.size >= 2 && tracks.size < MAX_TRACKS) {
                        tracks += line.toList()
                    }
                    line.clear()
                    inTrack = false
                }

                "name" -> if (inRoutePoint) {
                    currentName = text.toString().trim().takeIf { it.isNotBlank() }
                }

                "rtept" -> {
                    val lat = currentLat
                    val lon = currentLon
                    if (lat != null && lon != null && waypoints.size < MAX_WAYPOINTS) {
                        waypoints += RouteWaypoint(
                            id = "gpx-${waypoints.size}",
                            kind = RouteWaypointKind.WAYPOINT,
                            name = currentName ?: defaultWaypointName(waypoints.size),
                            position = LatLon(lat, lon),
                        )
                    }
                    inRoutePoint = false
                    currentName = null
                    currentLat = null
                    currentLon = null
                }
            }
        }

        fun finish(sourceName: String?): Result {
            if (tracks.isEmpty() && line.size >= 2) tracks += line.toList()
            if (tracks.isEmpty()) {
                return Result.Failure("GPX nie zawiera trasy (brak śladu z co najmniej 2 punktami).")
            }
            val chosenTrack = tracks.maxByOrNull { RouteGeometry.simplify(it).size } ?: tracks.first()
            val simplified = RouteGeometry.simplify(chosenTrack)
            if (simplified.size < 2) return Result.Failure("Trasa w GPX jest zbyt krótka.")
            val route = RoutePlan(
                waypoints = normalizeWaypoints(waypoints, simplified),
                line = simplified,
                sourceName = sourceName,
            )
            return Result.Success(
                ParseResult(
                    route = route,
                    multipleTracks = tracks.size > 1,
                    trackCount = tracks.size,
                ),
            )
        }
    }

    /**
     * Route points may be missing (OsmAnd track export without `<rte>`), or may not
     * include a target. The last route point is the planner's finish, so it becomes
     * the NaviLas [RouteWaypointKind.TARGET]; the first one is the planner's start and
     * is dropped when it coincides with the track start (NaviLas neither corrects nor
     * routes to the origin).
     */
    private fun normalizeWaypoints(
        waypoints: List<RouteWaypoint>,
        line: List<LatLon>,
    ): List<RouteWaypoint> {
        if (waypoints.isEmpty()) {
            return listOf(
                RouteWaypoint(
                    id = "gpx-target",
                    kind = RouteWaypointKind.TARGET,
                    name = defaultWaypointName(0),
                    position = line.last(),
                ),
            )
        }
        var byPosition = waypoints.distinctBy {
            String.format(Locale.US, "%.6f:%.6f", it.latitude, it.longitude)
        }
        if (byPosition.size >= 2) {
            val start = line.first()
            val first = byPosition.first()
            val startDistanceKm = GeoUtils.distanceKm(
                start.latitude,
                start.longitude,
                first.latitude,
                first.longitude,
            )
            if (startDistanceKm <= ROUTE_START_MATCH_KM) byPosition = byPosition.drop(1)
        }
        return byPosition.mapIndexed { index, waypoint ->
            waypoint.copy(
                kind = if (index == byPosition.lastIndex) {
                    RouteWaypointKind.TARGET
                } else {
                    RouteWaypointKind.WAYPOINT
                },
            )
        }
    }

    /** How close the first `<rtept>` must be to the track start to count as the origin. */
    private const val ROUTE_START_MATCH_KM = 0.15

    private fun defaultWaypointName(index: Int): String = "Punkt ${index + 1}"

    private fun isValid(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}
