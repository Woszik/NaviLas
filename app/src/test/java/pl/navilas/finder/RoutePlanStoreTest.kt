package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.route.RoutePlanStore
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.RoutePlan
import pl.navilas.finder.domain.RouteWaypoint
import pl.navilas.finder.domain.RouteWaypointKind
import java.io.File

class RoutePlanStoreTest {
    private fun tempStore(): Pair<RoutePlanStore, File> {
        val dir = File(
            System.getProperty("java.io.tmpdir"),
            "navilas-route-test-${System.nanoTime()}",
        )
        dir.mkdirs()
        return RoutePlanStore.fromAppFilesDir(dir) to dir
    }

    @Test
    fun round_trips_geometry_waypoints_and_source() {
        val (store, _) = tempStore()
        val plan = RoutePlan(
            waypoints = listOf(
                RouteWaypoint("a", RouteWaypointKind.WAYPOINT, "Postój", LatLon(50.0, 19.0)),
                RouteWaypoint("b", RouteWaypointKind.TARGET, "Meta", LatLon(50.0, 20.0)),
            ),
            line = listOf(LatLon(50.0, 19.0), LatLon(50.0, 19.5), LatLon(50.0, 20.0)),
            sourceName = "OsmAnd.gpx",
        )
        store.save(plan)
        val loaded = store.load()
        assertEquals(plan.line, loaded.line)
        assertEquals(plan.waypoints, loaded.waypoints)
        assertEquals("OsmAnd.gpx", loaded.sourceName)
        assertTrue(loaded.isUsable)
    }

    @Test
    fun missing_file_gives_empty_plan() {
        val (store, _) = tempStore()
        val plan = store.load()
        assertTrue(plan.line.isEmpty())
        assertTrue(plan.waypoints.isEmpty())
    }

    @Test
    fun clearing_removes_the_file() {
        val (store, dir) = tempStore()
        store.save(RoutePlan(line = listOf(LatLon(50.0, 19.0), LatLon(50.0, 20.0))))
        store.clear()
        assertTrue(store.load().line.isEmpty())
        assertTrue(!File(dir, RoutePlanStore.FILE_NAME).exists())
    }

    @Test
    fun target_is_moved_last_when_file_is_hand_edited() {
        val (store, dir) = tempStore()
        File(dir, RoutePlanStore.FILE_NAME).writeText(
            """
            {"line":[50.0,19.0,50.0,20.0],"waypoints":[
              {"id":"t","kind":"TARGET","name":"Cel","lat":50.0,"lon":20.0},
              {"id":"v","kind":"WAYPOINT","name":"Postój","lat":50.0,"lon":19.5}
            ]}
            """.trimIndent(),
        )
        val loaded = store.load()
        assertEquals(2, loaded.waypoints.size)
        assertEquals("v", loaded.waypoints.first().id)
        assertEquals(RouteWaypointKind.TARGET, loaded.waypoints.last().kind)
    }
}
