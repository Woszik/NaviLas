package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.osm.GpxRouteParser
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.RoutePlan
import pl.navilas.finder.domain.RouteWaypoint
import pl.navilas.finder.domain.RouteWaypointKind
import pl.navilas.finder.nav.RouteGpxWriter
import pl.navilas.finder.util.RouteGeometry
import java.io.ByteArrayInputStream

class RouteGpxWriterTest {
    private val plan = RoutePlan(
        waypoints = listOf(
            RouteWaypoint("a", RouteWaypointKind.WAYPOINT, "Postój & kawa", LatLon(50.0, 19.5)),
            RouteWaypoint("b", RouteWaypointKind.TARGET, "Meta", LatLon(50.0, 20.0)),
        ),
        line = listOf(LatLon(50.0, 19.0), LatLon(50.0, 20.0)),
        sourceName = "OsmAnd.gpx",
    )

    @Test
    fun writes_route_points_in_order_with_target_last() {
        val gpx = RouteGpxWriter.write(plan, "Trasa", includeTrack = false)
        val via = gpx.indexOf("<name>Postój &amp; kawa</name>")
        val target = gpx.indexOf("<name>Meta</name>")
        assertTrue(via in 1 until target)
        assertTrue(gpx.contains("lat=\"50.000000\""))
        assertTrue(gpx.contains("<type>TARGET</type>"))
        assertTrue(!gpx.contains("<trk>"))
    }

    @Test
    fun includes_track_when_requested() {
        val gpx = RouteGpxWriter.write(plan, "Trasa", includeTrack = true)
        assertTrue(gpx.contains("<trk>"))
        assertEquals(2, Regex("<trkpt ").findAll(gpx).count())
    }

    @Test
    fun escaped_names_survive_a_round_trip_through_the_parser() {
        val escaped = plan.copy(
            waypoints = listOf(
                RouteWaypoint("a", RouteWaypointKind.WAYPOINT, "Kawa & <herbata>", LatLon(50.0, 19.5)),
                RouteWaypoint("b", RouteWaypointKind.TARGET, "\"Meta\"", LatLon(50.0, 20.0)),
            ),
        )
        val gpx = RouteGpxWriter.write(escaped, "Trasa & powrót", includeTrack = true)
        val parsed = GpxRouteParser.parse(ByteArrayInputStream(gpx.toByteArray()), null)
        val route = (parsed as GpxRouteParser.Result.Success).value.route
        assertEquals(
            escaped.waypoints.map { it.name },
            route.waypoints.map { it.name },
        )
    }

    @Test
    fun round_trip_does_not_duplicate_the_origin() {
        // NaviLas waypoints never include the track origin, so exporting and importing back
        // must return exactly the same correction points.
        val gpx = RouteGpxWriter.write(plan, "Trasa", includeTrack = true)
        val parsed = GpxRouteParser.parse(ByteArrayInputStream(gpx.toByteArray()), null)
        val route = (parsed as GpxRouteParser.Result.Success).value.route
        assertEquals(plan.line, route.line)
        assertEquals(plan.waypoints.map { it.name }, route.waypoints.map { it.name })
        assertEquals(plan.waypoints.map { it.kind }, route.waypoints.map { it.kind })
        assertEquals(plan.waypoints.map { it.position }, route.waypoints.map { it.position })
    }
}

class RouteGeometryTest {
    private val straight = listOf(
        LatLon(50.0, 19.0),
        LatLon(50.0, 19.5),
        LatLon(50.0, 20.0),
        LatLon(50.0, 20.5),
        LatLon(50.0, 21.0),
    )

    @Test
    fun chunk_covers_the_whole_line_without_gaps() {
        val chunks = RouteGeometry.chunk(straight, chunkKm = 40.0)
        assertTrue(chunks.size >= 1)
        chunks.zipWithNext().forEach { (a, b) ->
            assertEquals(a.last(), b.first())
        }
        assertEquals(straight.first(), chunks.first().first())
        assertEquals(straight.last(), chunks.last().last())
    }

    @Test
    fun each_chunk_stays_close_to_the_target_length() {
        val chunks = RouteGeometry.chunk(straight, chunkKm = 40.0)
        // 2 degrees of longitude at 50°N is roughly 143 km, so ~4 chunks of ~36 km.
        assertTrue(chunks.size in 2..8)
    }

    @Test
    fun cumulative_km_grows_monotonically_and_ends_with_total_length() {
        val cumulative = RouteGeometry.cumulativeKm(straight)
        assertEquals(0.0, cumulative.first(), 1e-9)
        cumulative.toList().zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
        assertEquals(RouteGeometry.cumulativeKm(straight).last(), cumulative.last(), 1e-9)
        assertTrue(cumulative.last() > 100.0)
    }

    @Test
    fun simplify_keeps_endpoints_and_drops_dense_noise() {
        val dense = buildList {
            add(LatLon(50.0, 19.0))
            repeat(50) { add(LatLon(50.0, 19.0 + 0.00001 * it)) }
            add(LatLon(50.0, 21.0))
        }
        val simplified = RouteGeometry.simplify(dense)
        assertTrue(simplified.size < dense.size)
        assertEquals(dense.first(), simplified.first())
        assertEquals(dense.last(), simplified.last())
    }

    @Test
    fun chunk_of_degenerate_line_is_empty() {
        assertTrue(RouteGeometry.chunk(listOf(LatLon(50.0, 19.0))).isEmpty())
    }
}
