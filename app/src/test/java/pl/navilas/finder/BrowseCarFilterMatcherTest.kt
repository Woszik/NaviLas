package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.BrowseCarFilterMatcher
import pl.navilas.finder.data.bdl.NaturalSpringClassifier
import pl.navilas.finder.data.bdl.RestSiteRepository
import pl.navilas.finder.domain.BrowseCarFilter
import pl.navilas.finder.domain.BrowseParkingProximityMode
import pl.navilas.finder.domain.BrowseWaterProximityMode
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.NaturalSpringCertainty
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujStatus

class BrowseCarFilterMatcherTest {
    @Test
    fun inactive_returns_null() {
        val sites = listOf(site("a", 52.0, 21.0, setOf(SiteFeature.WIATA)))
        assertNull(BrowseCarFilterMatcher.matchingIds(sites, BrowseCarFilter()))
    }

    @Test
    fun wiata_and_lawostoly_and_on_same_site() {
        val sites = listOf(
            site("both", 52.0, 21.0, setOf(SiteFeature.WIATA, SiteFeature.LAWOSTOLY)),
            // Far enough that nearby amenity link does not apply (~1.1 km).
            site("wiata", 52.01, 21.0, setOf(SiteFeature.WIATA)),
        )
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireWiata = true, requireLawostoly = true),
        )
        assertEquals(setOf("both"), ids)
    }

    @Test
    fun wiata_nearby_within_200m_counts() {
        val sites = listOf(
            site("rest", 52.0, 21.0, emptySet()),
            site("wiata", 52.0005, 21.0, setOf(SiteFeature.WIATA)), // ~55 m
        )
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireWiata = true),
        )
        assertTrue(ids!!.contains("rest"))
        assertTrue(ids.contains("wiata"))
    }

    @Test
    fun woda_pitna_nearby_within_200m_counts() {
        val sites = listOf(
            site("rest", 52.0, 21.0, emptySet()),
            site("woda", 52.0005, 21.0, setOf(SiteFeature.WODA_PITNA)),
        )
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireWodaPitna = true),
        )
        assertTrue(ids!!.contains("rest"))
        assertTrue(ids.contains("woda"))
    }

    @Test
    fun zrodlo_certain_and_uncertain_match_filter() {
        val sites = listOf(
            site("krywald", 52.0, 21.0, emptySet(), spring = NaturalSpringCertainty.CERTAIN),
            site("nameOnly", 52.01, 21.0, emptySet(), spring = NaturalSpringCertainty.UNCERTAIN),
            site("plain", 52.02, 21.0, emptySet()),
        )
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireZrodlo = true),
        )
        assertEquals(setOf("krywald", "nameOnly"), ids)
    }

    @Test
    fun zrodlo_nearby_within_200m_counts() {
        val sites = listOf(
            site("rest", 52.0, 21.0, emptySet()),
            site(
                "spring",
                52.0005,
                21.0,
                emptySet(),
                spring = NaturalSpringCertainty.CERTAIN,
            ),
        )
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireZrodlo = true),
        )
        assertTrue(ids!!.contains("rest"))
        assertTrue(ids.contains("spring"))
    }

    @Test
    fun parking_max_distance_allows_farther_than_200m() {
        // ~450 m north
        val sites = listOf(
            site("rest", 52.0, 21.0, setOf(SiteFeature.WIATA)),
            site(
                "park",
                52.00405,
                21.0,
                setOf(SiteFeature.PARKING),
                layerId = RestSiteRepository.LAYER_PARKING,
            ),
        )
        val near = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(
                requireWiata = true,
                requireParking = true,
                parkingMode = BrowseParkingProximityMode.NEAR_POINT,
            ),
        )
        assertEquals(emptySet<String>(), near)

        val far = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(
                requireWiata = true,
                requireParking = true,
                parkingMode = BrowseParkingProximityMode.MAX_DISTANCE,
                parkingMaxMeters = 600,
            ),
        )
        assertTrue(far!!.contains("rest"))
    }

    @Test
    fun zanocuj_in_zone_only() {
        val sites = listOf(
            site("in", 52.0, 21.0, emptySet(), zanocuj = ZanocujStatus.IN_ZONE),
            site("out", 52.01, 21.0, emptySet(), zanocuj = ZanocujStatus.OUTSIDE_ZONE),
        )
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireZanocujInZone = true),
        )
        assertEquals(setOf("in"), ids)
    }

    @Test
    fun exclude_entry_ban_removes_sites_in_zone() {
        val sites = listOf(
            site("in", 52.0, 21.0, emptySet()),
            site("out", 52.01, 21.0, emptySet()),
        )
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(excludeSitesInEntryBan = true),
            excludeSiteIds = setOf("in"),
        )
        assertEquals(setOf("out"), ids)
    }

    @Test
    fun summaryPl_lists_active_filters() {
        val empty = BrowseCarFilter()
        assertEquals(BrowseCarFilter.SUMMARY_ALL, empty.summaryPl())
        val active = BrowseCarFilter(
            requireWiata = true,
            requireZrodlo = true,
            requireParking = true,
            parkingMode = BrowseParkingProximityMode.MAX_DISTANCE,
            parkingMaxMeters = 800,
        )
        assertEquals("Wiata · Źródło · Parking (max 800 m)", active.summaryPl())
        val water = BrowseCarFilter(
            requireNearWater = true,
            waterMode = BrowseWaterProximityMode.NEAR_POINT,
        )
        assertEquals("Nad wodą (przy punkcie)", water.summaryPl())
        val waterMax = BrowseCarFilter(
            requireNearWater = true,
            waterMode = BrowseWaterProximityMode.MAX_DISTANCE,
            waterMaxMeters = 800,
        )
        assertEquals("Nad wodą (max 800 m)", waterMax.summaryPl())
    }

    @Test
    fun kapielisko_within_250m_matches_near_water() {
        val sites = listOf(
            site("rest", 52.0, 21.0, emptySet()),
            site("swim", 52.002, 21.0, setOf(SiteFeature.KAPIELISKO)), // ~222 m
        )
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireNearWater = true),
        )
        assertTrue(ids!!.contains("rest"))
        assertTrue(ids.contains("swim"))
    }

    @Test
    fun zrodlo_does_not_satisfy_near_water() {
        val sites = listOf(
            site("spring", 52.0, 21.0, emptySet(), spring = NaturalSpringCertainty.CERTAIN),
            site("plain", 52.01, 21.0, emptySet()),
        )
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireNearWater = true),
        )
        assertEquals(emptySet<String>(), ids)
    }

    @Test
    fun water_max_distance_allows_farther_than_250m() {
        val sites = listOf(
            site("rest", 52.0, 21.0, setOf(SiteFeature.WIATA)),
            site("swim", 52.00405, 21.0, setOf(SiteFeature.KAPIELISKO)), // ~450 m
        )
        val near = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(
                requireWiata = true,
                requireNearWater = true,
                waterMode = BrowseWaterProximityMode.NEAR_POINT,
            ),
        )
        assertEquals(emptySet<String>(), near)

        val far = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(
                requireWiata = true,
                requireNearWater = true,
                waterMode = BrowseWaterProximityMode.MAX_DISTANCE,
                waterMaxMeters = 600,
            ),
        )
        assertTrue(far!!.contains("rest"))
    }

    @Test
    fun overlay_boat_point_counts_as_bdl_water() {
        val sites = listOf(site("rest", 52.0, 21.0, emptySet()))
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireNearWater = true),
            overlayWaterPoints = listOf(LatLon(52.001, 21.0)),
        )
        assertEquals(setOf("rest"), ids)
    }

    @Test
    fun osm_hit_without_bdl_water_matches() {
        val sites = listOf(site("rest", 52.0, 21.0, emptySet()))
        val miss = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireNearWater = true),
        )
        assertEquals(emptySet<String>(), miss)
        val hit = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireNearWater = true),
            osmWaterHits = setOf("rest"),
        )
        assertEquals(setOf("rest"), hit)
    }

    @Test
    fun layer_26_site_matches_near_water() {
        val sites = listOf(
            site(
                "boat",
                52.0,
                21.0,
                emptySet(),
                layerId = RestSiteRepository.LAYER_BOAT,
            ),
        )
        val ids = BrowseCarFilterMatcher.matchingIds(
            sites,
            BrowseCarFilter(requireNearWater = true),
        )
        assertEquals(setOf("boat"), ids)
    }

    private fun site(
        id: String,
        lat: Double,
        lon: Double,
        features: Set<SiteFeature>,
        zanocuj: ZanocujStatus = ZanocujStatus.OUTSIDE_ZONE,
        layerId: Int = RestSiteRepository.LAYER_REST,
        spring: NaturalSpringCertainty? = null,
    ) = RestSite(
        id = id,
        name = id,
        latitude = lat,
        longitude = lon,
        description = null,
        sourceLayerId = layerId,
        sourceLayerName = "test",
        features = features,
        relatedObjects = emptyList(),
        zanocujStatus = zanocuj,
        distanceToZanocujBoundaryMeters = null,
        naturalSpring = spring,
    )
}

class NaturalSpringClassifierTest {
    @Test
    fun krywald_inne_atr_is_certain() {
        assertEquals(
            NaturalSpringCertainty.CERTAIN,
            NaturalSpringClassifier.evaluate(
                name = "Miejsce odpoczynku Źródełko Krywałd",
                uwagi = "Brusiek 484i",
                inneAtr = "Rzeka Mała Panew, Źródełko artezyjskie",
            ),
        )
    }

    @Test
    fun name_only_is_uncertain() {
        assertEquals(
            NaturalSpringCertainty.UNCERTAIN,
            NaturalSpringClassifier.evaluate(
                name = "Miejsce odpoczynku Czernichów Źródełko",
                uwagi = "brak uwag",
                inneAtr = "BRAK",
            ),
        )
    }

    @Test
    fun proximity_inne_atr_is_rejected() {
        assertNull(
            NaturalSpringClassifier.evaluate(
                name = "Miejsce wypoczynku \"Korzeczków\"",
                uwagi = null,
                inneAtr = "W niedalekiej odległości znajduje się pomnik przyrody \"Źródło Anny\"",
            ),
        )
    }

    @Test
    fun tanwi_reserve_inne_atr_is_uncertain() {
        assertEquals(
            NaturalSpringCertainty.UNCERTAIN,
            NaturalSpringClassifier.evaluate(
                name = "Wiata w rezerwacie Źródła Tanwi",
                uwagi = "Nie",
                inneAtr = "Rezerwat Przyrody Źródła Tanwi, gdzie znajduje się ścieżka przyrodnicza",
            ),
        )
    }

    @Test
    fun siedem_zrodel_historical_is_uncertain() {
        assertEquals(
            NaturalSpringCertainty.UNCERTAIN,
            NaturalSpringClassifier.evaluate(
                name = "Siedem Źródeł",
                uwagi = null,
                inneAtr = "Siedem Źródeł - miejsce historyczne, droga krzyżowa",
            ),
        )
    }
}
