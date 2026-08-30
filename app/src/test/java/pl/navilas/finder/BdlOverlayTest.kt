package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.BdlOverlayCatalog
import pl.navilas.finder.data.bdl.BdlOverlayLoader
import pl.navilas.finder.domain.BdlOverlayFilter
import pl.navilas.finder.domain.BdlOverlayGroup
import pl.navilas.finder.util.GeoUtils

class BdlOverlayTest {
    @Test
    fun catalog_excludes_rest_site_layers() {
        assertNull(BdlOverlayCatalog.groupForLayer(15))
        assertNull(BdlOverlayCatalog.groupForLayer(17))
        assertNull(BdlOverlayCatalog.groupForLayer(19))
        assertEquals(BdlOverlayGroup.VIEW, BdlOverlayCatalog.groupForLayer(25))
        assertEquals(BdlOverlayGroup.OTHER, BdlOverlayCatalog.groupForLayer(27))
        assertEquals(BdlOverlayGroup.WATER, BdlOverlayCatalog.groupForLayer(26))
    }

    @Test
    fun core_load_skips_full_only_layers() {
        val core = BdlOverlayCatalog.layersToLoad(fullAvailable = false).map { it.layerId }
        assertEquals(listOf(25, 27), core)
        val full = BdlOverlayCatalog.layersToLoad(fullAvailable = true).map { it.layerId }
        assertTrue(full.containsAll(listOf(25, 27, 26, 21, 5)))
    }

    @Test
    fun map_feature_reads_name_and_flags() {
        val spec = BdlOverlayCatalog.spec(27)!!
        val feature = JSONObject(
            """
            {
              "attributes": {
                "foreign_key": "abc-1",
                "nzw_ob": "Kapliczka w lesie",
                "tur_rec_pnt_cd": "IN PT NIEN",
                "zrodlo": "N",
                "uwagi": "przy szlaku"
              },
              "geometry": { "x": 19.1, "y": 51.2 }
            }
            """.trimIndent(),
        )
        val point = BdlOverlayLoader.mapFeature(feature, spec)!!
        assertEquals("Kapliczka w lesie", point.name)
        assertEquals(BdlOverlayGroup.OTHER, point.group)
        assertEquals(27, point.layerId)
        assertEquals("IN PT NIEN", point.typeCode)
        assertTrue(point.id.contains("abc-1"))
        assertTrue(point.detailLines().any { it.contains("przy szlaku") })
    }

    @Test
    fun envelope_filters_by_group_and_bbox() {
        val spec = BdlOverlayCatalog.spec(25)!!
        val inside = BdlOverlayLoader.mapFeature(
            JSONObject(
                """{"attributes":{"foreign_key":"in","nzw_ob":"Widok"},"geometry":{"x":19.0,"y":51.0}}""",
            ),
            spec,
        )!!
        val outside = BdlOverlayLoader.mapFeature(
            JSONObject(
                """{"attributes":{"foreign_key":"out","nzw_ob":"Daleko"},"geometry":{"x":21.0,"y":53.0}}""",
            ),
            spec,
        )!!
        val subset = BdlOverlayLoader.inEnvelope(
            points = listOf(inside, outside),
            envelope = GeoUtils.Envelope(xmin = 18.5, ymin = 50.5, xmax = 19.5, ymax = 51.5),
            groups = setOf(BdlOverlayGroup.VIEW),
            centerLat = 51.0,
            centerLon = 19.0,
            limit = 10,
        )
        assertEquals(listOf("Widok"), subset.map { it.name })
        val none = BdlOverlayLoader.inEnvelope(
            points = listOf(inside),
            envelope = GeoUtils.Envelope(xmin = 18.5, ymin = 50.5, xmax = 19.5, ymax = 51.5),
            groups = setOf(BdlOverlayGroup.WATER),
            centerLat = 51.0,
            centerLon = 19.0,
            limit = 10,
        )
        assertTrue(none.isEmpty())
    }

    @Test
    fun filter_core_hides_full_only_groups() {
        val filter = BdlOverlayFilter(
            enabled = true,
            groups = setOf(BdlOverlayGroup.VIEW, BdlOverlayGroup.WATER),
        )
        assertEquals(setOf(BdlOverlayGroup.VIEW), filter.effectiveGroups(fullAvailable = false))
        assertEquals(
            setOf(BdlOverlayGroup.VIEW, BdlOverlayGroup.WATER),
            filter.effectiveGroups(fullAvailable = true),
        )
        assertEquals("Ukryte", BdlOverlayFilter().summaryPl(false))
    }

    @Test
    fun filter_keeps_view_and_other_independent() {
        val viewOnly = BdlOverlayFilter(enabled = true, groups = setOf(BdlOverlayGroup.VIEW))
        assertEquals(setOf(BdlOverlayGroup.VIEW), viewOnly.effectiveGroups(fullAvailable = false))
        assertEquals("Widok", viewOnly.summaryPl(false))

        val otherOnly = BdlOverlayFilter(enabled = true, groups = setOf(BdlOverlayGroup.OTHER))
        assertEquals(setOf(BdlOverlayGroup.OTHER), otherOnly.effectiveGroups(fullAvailable = false))
        assertEquals("Inne / edukacja", otherOnly.summaryPl(false))
    }

    @Test
    fun enabled_without_groups_draws_nothing() {
        val empty = BdlOverlayFilter(enabled = true, groups = emptySet())
        assertTrue(empty.effectiveGroups(fullAvailable = true).isEmpty())
        assertEquals("Włączone — wybierz grupę", empty.summaryPl(true))
    }
}
