package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.NearbyOverlayObjects
import pl.navilas.finder.domain.BdlOverlayGroup
import pl.navilas.finder.domain.BdlOverlayPoint
import pl.navilas.finder.domain.RestSiteTitles

class NearbyOverlayObjectsTest {
    @Test
    fun groups_same_name_within_200m() {
        val originLat = 50.63036
        val originLon = 18.78480
        val oakClose = overlay("oak-1", "POMNIK PRZYRODY DĄB SZYPUŁKOWY", 50.63060, 18.78480)
        val oakAlso = overlay("oak-2", "POMNIK PRZYRODY DĄB SZYPUŁKOWY", 50.63020, 18.78480)
        val chapel = overlay("cap-1", "KAPLICZKA ŚW. HUBERTA", 50.63100, 18.78480)
        val far = overlay("far", "DALEKO", 50.64000, 18.78480)
        val self = overlay("self", "Uroczysko", originLat, originLon)

        val groups = NearbyOverlayObjects.groupedWithin(
            points = listOf(oakClose, oakAlso, chapel, far, self),
            latitude = originLat,
            longitude = originLon,
            excludeId = "self",
            radiusMeters = 200.0,
        )
        assertEquals(2, groups.size)
        assertEquals("POMNIK PRZYRODY DĄB SZYPUŁKOWY", groups[0].name)
        assertEquals(2, groups[0].count)
        assertTrue(groups[0].linePl().startsWith("Pomnik Przyrody Dąb Szypułkowy ×2"))
        assertEquals("KAPLICZKA ŚW. HUBERTA", groups[1].name)
        assertTrue(groups[1].linePl().startsWith("Kapliczka św. Huberta · "))
        assertTrue(groups.none { it.name.contains("DALEKO") })
    }

    @Test
    fun rest_site_title_skips_bdl_prefix() {
        assertEquals(
            "Miejsce postoju pojazdów Uroczysko Potempowe",
            RestSiteTitles.cardTitle("Miejsce postoju pojazdów Uroczysko Potempowe"),
        )
        assertEquals(
            "Miejsce odpoczynku „Pod Dębem”",
            RestSiteTitles.cardTitle("Pod Dębem"),
        )
    }

    private fun overlay(
        id: String,
        name: String,
        lat: Double,
        lon: Double,
    ) = BdlOverlayPoint(
        id = id,
        name = name,
        latitude = lat,
        longitude = lon,
        layerId = 27,
        layerName = "Inne",
        group = BdlOverlayGroup.OTHER,
        typeCode = "IN PT NIEN",
        features = emptySet(),
        notes = null,
        extraFlags = emptyList(),
    )
}
