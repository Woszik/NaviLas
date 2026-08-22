package pl.navilas.finder

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.RestSiteRepository
import pl.navilas.finder.data.bdl.RestSiteSearchDiagnostics
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.util.GeoUtils
import java.net.InetAddress

class RestSitePipelineDiagTest {
    @Test
    fun satellite_out_fields_must_not_include_parking() {
        // Layers 25/27 have ma_parking, not parking — requesting parking aborts the whole search.
        assertFalse(
            "OUT_FIELDS_SATELLITE must not contain parking",
            RestSiteRepository.OUT_FIELDS_SATELLITE.split(',').contains("parking"),
        )
        assertTrue(RestSiteRepository.OUT_FIELDS_SATELLITE.contains("tur_rec_pnt_cd"))
    }

    @Test
    fun radius_25_keeps_known_in_range_rest_site() {
        val userLat = RestSiteSearchDiagnostics.SAMPLE_LAT
        val userLon = RestSiteSearchDiagnostics.SAMPLE_LON
        // Exact BDL sample coordinate at the known rest site itself.
        val siteLat = 52.20226498283688
        val siteLon = 21.18140799100673
        val radiusKm = 25.0
        val envelope = GeoUtils.envelopeAround(userLat, userLon, radiusKm)
        assertTrue(envelope.xmin < envelope.xmax)
        assertTrue(envelope.ymin < envelope.ymax)
        assertTrue(siteLon in envelope.xmin..envelope.xmax)
        assertTrue(siteLat in envelope.ymin..envelope.ymax)
        val dist = GeoUtils.distanceKm(userLat, userLon, siteLat, siteLon)
        assertTrue("known site must be within 25 km (dist=$dist)", dist <= radiusKm)
        assertTrue(dist < 0.01)
    }

    @Test
    fun live_bdl_layer15_25km_returns_features_with_geometry() {
        assumeTrue("no network", hasDns("mapserver.bdl.lasy.gov.pl"))
        val report = RestSiteSearchDiagnostics.diagnoseOne(
            RestSiteSearchDiagnostics.SAMPLE_LAT,
            RestSiteSearchDiagnostics.SAMPLE_LON,
            25.0,
        )
        println(RestSiteSearchDiagnostics.formatReport(report))
        assertEquals(200, report.layer15Http)
        assertTrue(report.jsonValid)
        assertEquals(null, report.arcgisError)
        assertTrue("expected layer 15 features > 0", report.bdlFeatures > 0)
        assertEquals(report.bdlFeatures, report.hasGeometryCount)
        assertTrue("expected withinRadius > 0", report.withinRadius > 0)
        report.satelliteNotes.forEach { note ->
            assertFalse("satellite query must not fail: $note", note.contains("error=") && !note.endsWith("error=null"))
        }
    }

    @Test
    fun live_findRestSites_25km_not_empty() = runBlocking {
        assumeTrue("no network", hasDns("mapserver.bdl.lasy.gov.pl"))
        val bundle = RestSiteRepository(config = SearchConfig.DEFAULT).findRestSites(
            latitude = RestSiteSearchDiagnostics.SAMPLE_LAT,
            longitude = RestSiteSearchDiagnostics.SAMPLE_LON,
            radiusKm = 25.0,
        )
        assertTrue(
            "radius 25 km must not yield zero rest sites for known in-range point",
            bundle.sites.isNotEmpty(),
        )
    }

    @Test
    fun live_all_radii_log_counts() {
        assumeTrue("no network", hasDns("mapserver.bdl.lasy.gov.pl"))
        val reports = RestSiteSearchDiagnostics.diagnoseAllPresets()
        reports.forEach { report ->
            println(RestSiteSearchDiagnostics.formatReport(report))
            println("---")
            assertEquals(200, report.layer15Http)
            assertTrue(report.bdlFeatures > 0)
            assertTrue(report.withinRadius > 0)
        }
    }

    private fun hasDns(host: String): Boolean = try {
        InetAddress.getAllByName(host).isNotEmpty()
    } catch (_: Exception) {
        false
    }
}
