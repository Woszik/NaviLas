package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.domain.isUsableBrowseViewport
import pl.navilas.finder.domain.selectBrowseContent
import pl.navilas.finder.util.GeoUtils

class BrowseMapContentTest {
    private val poland = GeoUtils.Envelope(xmin = 14.0, ymin = 49.0, xmax = 24.5, ymax = 55.0)
    private val sites = listOf(
        site("warsaw", 52.23, 21.01),
        site("krakow", 50.06, 19.94),
        site("gdansk", 54.35, 18.65),
    )

    @Test
    fun inactive_filter_draws_every_site_in_the_viewport() {
        val (points, clusters) = selectBrowseContent(
            sites = sites,
            envelope = poland,
            centerLat = 52.1,
            centerLon = 19.4,
            zoom = 5.5,
            matchingIds = null,
        )
        assertTrue(clusters.isEmpty())
        assertEquals(setOf("warsaw", "krakow", "gdansk"), points.map { it.id }.toSet())
    }

    @Test
    fun sites_outside_the_visible_envelope_are_omitted() {
        val warsawOnly = GeoUtils.Envelope(xmin = 20.5, ymin = 52.0, xmax = 21.5, ymax = 52.5)
        val (points, clusters) = selectBrowseContent(
            sites = sites,
            envelope = warsawOnly,
            centerLat = 52.23,
            centerLon = 21.01,
            zoom = 11.0,
            matchingIds = null,
        )
        assertTrue(clusters.isEmpty())
        assertEquals(listOf("warsaw"), points.map { it.id })
    }

    @Test
    fun empty_viewport_stays_empty_when_filter_is_inactive() {
        val emptyOcean = GeoUtils.Envelope(xmin = -1.0, ymin = -1.0, xmax = 1.0, ymax = 1.0)
        val (points, clusters) = selectBrowseContent(
            sites = sites,
            envelope = emptyOcean,
            centerLat = 0.0,
            centerLon = 0.0,
            zoom = 8.0,
            matchingIds = null,
        )
        assertTrue(points.isEmpty())
        assertTrue(clusters.isEmpty())
    }

    @Test
    fun active_filter_with_no_matches_stays_empty() {
        val (points, clusters) = selectBrowseContent(
            sites = sites,
            envelope = poland,
            centerLat = 52.1,
            centerLon = 19.4,
            zoom = 8.0,
            matchingIds = emptySet(),
        )
        assertTrue(points.isEmpty())
        assertTrue(clusters.isEmpty())
    }

    @Test
    fun unusable_camera_bounds_are_rejected() {
        assertFalse(isUsableBrowseViewport(0.0, 0.0, 0.0, 0.0))
        assertFalse(isUsableBrowseViewport(Double.NaN, 50.0, 21.0, 52.0))
        assertTrue(isUsableBrowseViewport(20.8, 52.1, 21.3, 52.4))
    }

    private fun site(id: String, lat: Double, lon: Double) = RestSite(
        id = id,
        name = id,
        latitude = lat,
        longitude = lon,
        description = null,
        sourceLayerId = 15,
        sourceLayerName = "test",
        features = emptySet<SiteFeature>(),
        relatedObjects = emptyList(),
        zanocujStatus = ZanocujStatus.OUTSIDE_ZONE,
    )
}
