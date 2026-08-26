package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.util.CorridorGeometry

class CorridorGeometryTest {
    private val west = LatLon(50.0, 19.0)
    private val east = LatLon(50.0, 20.0)
    private val line = listOf(west, east)

    @Test
    fun point_south_of_eastbound_line_is_on_right() {
        val south = LatLon(49.95, 19.5)
        val p = CorridorGeometry.project(south.latitude, south.longitude, line)!!
        assertTrue(p.onRight)
        assertTrue(p.crossTrackKm in 4.0..7.0)
        assertTrue(CorridorGeometry.isInside(south.latitude, south.longitude, line, leftKm = 2.0, rightKm = 10.0))
        assertFalse(CorridorGeometry.isInside(south.latitude, south.longitude, line, leftKm = 10.0, rightKm = 2.0))
    }

    @Test
    fun point_north_of_eastbound_line_is_on_left() {
        val north = LatLon(50.05, 19.5)
        val p = CorridorGeometry.project(north.latitude, north.longitude, line)!!
        assertFalse(p.onRight)
        assertTrue(CorridorGeometry.isInside(north.latitude, north.longitude, line, leftKm = 10.0, rightKm = 2.0))
        assertFalse(CorridorGeometry.isInside(north.latitude, north.longitude, line, leftKm = 2.0, rightKm = 10.0))
    }

    @Test
    fun distance_along_increases_toward_end() {
        val mid = LatLon(50.0, 19.5)
        val nearEnd = LatLon(50.0, 19.9)
        val a = CorridorGeometry.project(mid.latitude, mid.longitude, line)!!
        val b = CorridorGeometry.project(nearEnd.latitude, nearEnd.longitude, line)!!
        assertTrue(b.distanceAlongKm > a.distanceAlongKm)
        assertEquals(0.0, a.crossTrackKm, 0.2)
    }

    @Test
    fun envelope_covers_buffer() {
        val env = CorridorGeometry.envelope(line, leftKm = 5.0, rightKm = 10.0)
        assertTrue(env.ymin < 50.0)
        assertTrue(env.ymax > 50.0)
        assertTrue(env.xmin < 19.0)
        assertTrue(env.xmax > 20.0)
    }
}
