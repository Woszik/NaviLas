package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.domain.BROWSE_CLUSTER_MAX_ZOOM
import pl.navilas.finder.domain.BROWSE_SITE_VIEWPORT_MIN_ZOOM
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
    fun inactive_filter_at_country_zoom_builds_clusters() {
        val (points, clusters) = selectBrowseContent(
            sites = sites,
            envelope = poland,
            centerLat = 52.1,
            centerLon = 19.4,
            zoom = 5.5,
            matchingIds = null,
        )
        assertTrue(points.isEmpty())
        assertTrue(clusters.isNotEmpty())
        assertEquals(sites.size, clusters.sumOf { it.count })
    }

    @Test
    fun inactive_filter_empty_viewport_still_shows_nearest_sites() {
        val emptyOcean = GeoUtils.Envelope(xmin = -1.0, ymin = -1.0, xmax = 1.0, ymax = 1.0)
        val (points, clusters) = selectBrowseContent(
            sites = sites,
            envelope = emptyOcean,
            centerLat = 0.0,
            centerLon = 0.0,
            zoom = BROWSE_CLUSTER_MAX_ZOOM,
            matchingIds = null,
        )
        assertTrue(clusters.isEmpty())
        assertEquals(sites.size, points.size)
    }

    @Test
    fun active_filter_with_no_matches_stays_empty() {
        val (points, clusters) = selectBrowseContent(
            sites = sites,
            envelope = poland,
            centerLat = 52.1,
            centerLon = 19.4,
            zoom = BROWSE_SITE_VIEWPORT_MIN_ZOOM,
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
