package pl.navilas.finder.nav

import pl.navilas.finder.domain.RoutePlan
import java.util.Locale

/**
 * Writes the NaviLas route plan back to GPX for the external planner (OsmAnd).
 *
 * Only `<rte>/<rtept>` waypoints are authoritative — NaviLas corrects routes through
 * them. The imported geometry is optional (`<trk>`) so the user can see the previous
 * route shape after the round trip.
 */
object RouteGpxWriter {
    fun write(
        plan: RoutePlan,
        name: String,
        includeTrack: Boolean = false,
    ): String {
        val safeName = xmlEscape(name.ifBlank { "NaviLas trasa" })
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<gpx version=\"1.1\" creator=\"NaviLas\" ")
        builder.append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        builder.append("xmlns:osmand=\"https://osmand.net\">\n")
        builder.append("  <metadata>\n")
        builder.append("    <name>$safeName</name>\n")
        builder.append("  </metadata>\n")

        val ordered = orderedWaypoints(plan)
        if (ordered.isNotEmpty()) {
            builder.append("  <rte>\n")
            builder.append("    <name>$safeName</name>\n")
            ordered.forEach { waypoint ->
                builder.append(
                    "    <rtept lat=\"${fmt(waypoint.latitude)}\" " +
                        "lon=\"${fmt(waypoint.longitude)}\">\n",
                )
                builder.append("      <name>${xmlEscape(waypoint.name)}</name>\n")
                builder.append("      <type>${waypoint.kind.name}</type>\n")
                builder.append("    </rtept>\n")
            }
            builder.append("  </rte>\n")
        }

        if (includeTrack && plan.line.size >= 2) {
            builder.append("  <trk>\n")
            builder.append("    <name>$safeName</name>\n")
            builder.append("    <trkseg>\n")
            plan.line.forEach { point ->
                builder.append(
                    "      <trkpt lat=\"${fmt(point.latitude)}\" lon=\"${fmt(point.longitude)}\" />\n",
                )
            }
            builder.append("    </trkseg>\n")
            builder.append("  </trk>\n")
        }

        builder.append("</gpx>\n")
        return builder.toString()
    }

    /** Target closes the route; intermediate waypoints keep the user's order. */
    fun orderedWaypoints(plan: RoutePlan): List<pl.navilas.finder.domain.RouteWaypoint> {
        val via = plan.viaPoints
        val target = plan.target
        return if (target == null) via else via + target
    }

    fun suggestedFileName(plan: RoutePlan): String {
        val base = plan.sourceName?.let { NavigationLinks.gpxFileBaseName(it) } ?: "navilas-trasa"
        return "navilas-$base.gpx"
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
