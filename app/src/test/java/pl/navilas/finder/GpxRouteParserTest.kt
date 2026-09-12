package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.osm.GpxRouteParser
import pl.navilas.finder.domain.RouteWaypointKind
import java.io.ByteArrayInputStream

class GpxRouteParserTest {
    private fun parse(xml: String) =
        GpxRouteParser.parse(ByteArrayInputStream(xml.toByteArray()), "OsmAnd.gpx")

    @Test
    fun track_without_route_points_yields_line_and_derived_target() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="OsmAnd">
              <trk>
                <name>Trasa</name>
                <trkseg>
                  <trkpt lat="50.0000" lon="19.0000" />
                  <trkpt lat="50.0000" lon="19.5000" />
                  <trkpt lat="50.0000" lon="20.0000" />
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()
        val result = parse(xml)
        assertTrue(result is GpxRouteParser.Result.Success)
        val route = (result as GpxRouteParser.Result.Success).value.route
        assertEquals(3, route.line.size)
        assertEquals(1, route.waypoints.size)
        assertEquals(RouteWaypointKind.TARGET, route.waypoints.single().kind)
        assertEquals(20.0, route.waypoints.single().longitude, 1e-6)
    }

    @Test
    fun route_points_keep_order_drop_origin_and_last_becomes_target() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="OsmAnd">
              <rte>
                <rtept lat="50.0" lon="19.0"><name>Start</name></rtept>
                <rtept lat="50.0" lon="19.5"><name>Postój</name></rtept>
                <rtept lat="50.0" lon="20.0"><name>Meta</name></rtept>
              </rte>
              <trk>
                <trkseg>
                  <trkpt lat="50.0" lon="19.0" />
                  <trkpt lat="50.0" lon="20.0" />
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()
        val result = parse(xml)
        val route = (result as GpxRouteParser.Result.Success).value.route
        // "Start" coincides with the track start, so it is not a NaviLas waypoint.
        assertEquals(2, route.waypoints.size)
        assertEquals("Postój", route.viaPoints.single().name)
        assertEquals(RouteWaypointKind.TARGET, route.target?.kind)
        assertEquals("Meta", route.target?.name)
    }

    @Test
    fun route_point_away_from_track_start_is_kept() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="OsmAnd">
              <rte>
                <rtept lat="50.0" lon="19.5"><name>Postój</name></rtept>
                <rtept lat="50.0" lon="20.0"><name>Meta</name></rtept>
              </rte>
              <trk><trkseg>
                <trkpt lat="50.0" lon="19.0" />
                <trkpt lat="50.0" lon="20.0" />
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val route = (parse(xml) as GpxRouteParser.Result.Success).value.route
        assertEquals(2, route.waypoints.size)
        assertEquals("Postój", route.waypoints.first().name)
        assertEquals(RouteWaypointKind.WAYPOINT, route.waypoints.first().kind)
    }

    @Test
    fun longest_track_wins_when_file_has_several() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="OsmAnd">
              <trk><trkseg><trkpt lat="50.0" lon="19.0"/><trkpt lat="50.0" lon="19.01"/></trkseg></trk>
              <trk><trkseg>
                <trkpt lat="50.0" lon="19.0"/>
                <trkpt lat="50.0" lon="19.5"/>
                <trkpt lat="50.0" lon="20.0"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val parsed = (parse(xml) as GpxRouteParser.Result.Success).value
        assertEquals(3, parsed.route.line.size)
        assertTrue(parsed.multipleTracks)
        assertEquals(2, parsed.trackCount)
    }

    @Test
    fun rejects_gpx_without_geometry() {
        val xml = """<?xml version="1.0"?><gpx version="1.1"><wpt lat="50.0" lon="19.0"/></gpx>"""
        assertTrue(parse(xml) is GpxRouteParser.Result.Failure)
    }

    @Test
    fun malformed_xml_reports_failure() {
        assertTrue(parse("<gpx><trk>") is GpxRouteParser.Result.Failure)
    }
}
