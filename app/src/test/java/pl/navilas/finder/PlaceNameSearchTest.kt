package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.BdlOfflineStore
import pl.navilas.finder.data.bdl.BdlPlaceNameCatalog
import pl.navilas.finder.data.bdl.PlaceNameMatchKind
import pl.navilas.finder.data.bdl.PlaceNameSearch
import pl.navilas.finder.data.bdl.RestSiteRepository
import pl.navilas.finder.domain.BdlDataScope
import pl.navilas.finder.domain.OfflineBdlConfig
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujPolygonQuality
import pl.navilas.finder.domain.ZanocujStatus
import java.io.File

class PlaceNameSearchTest {
    @Test
    fun too_short_query_is_empty() {
        val sites = listOf(site("a", "Uroczysko Potempowe", 52.0, 21.0))
        assertTrue(PlaceNameSearch.search("po", sites).isEmpty())
    }

    @Test
    fun diacritics_fold_lodz() {
        val sites = listOf(site("a", "Altana Łódź", 51.75, 19.45))
        val hits = PlaceNameSearch.search("lodz", sites)
        assertEquals(1, hits.size)
        assertEquals("a", hits[0].siteId)
    }

    @Test
    fun prefix_of_distinctive_name() {
        val sites = listOf(
            site("p", "Miejsce postoju pojazdów Uroczysko Potempowe", 52.1, 21.1, layer = 19),
        )
        val hits = PlaceNameSearch.search("potemp", sites)
        assertEquals(listOf("p"), hits.map { it.siteId })
        assertEquals(
            PlaceNameMatchKind.PREFIX,
            PlaceNameSearch.matchKind(
                sites[0].name,
                PlaceNameSearch.normalize("potemp"),
                PlaceNameSearch.tokenize(PlaceNameSearch.normalize("potemp")),
            ),
        )
    }

    @Test
    fun one_typo_on_token() {
        val sites = listOf(
            site("p", "Miejsce postoju pojazdów Uroczysko Potempowe", 52.1, 21.1, layer = 19),
        )
        val hits = PlaceNameSearch.search("potempwe", sites)
        assertEquals(listOf("p"), hits.map { it.siteId })
        assertEquals(
            PlaceNameMatchKind.FUZZY,
            PlaceNameSearch.matchKind(
                sites[0].name,
                PlaceNameSearch.normalize("potempwe"),
                PlaceNameSearch.tokenize(PlaceNameSearch.normalize("potempwe")),
            ),
        )
    }

    @Test
    fun two_typos_on_long_token() {
        val sites = listOf(site("k", "Źródełko Krywałd", 50.5, 18.9))
        val hits = PlaceNameSearch.search("kriwaldx", sites)
        assertEquals(listOf("k"), hits.map { it.siteId })
    }

    @Test
    fun strip_generic_prefix_keeps_own_name() {
        assertEquals(
            "uroczysko potempowe",
            PlaceNameSearch.distinctivePart(
                PlaceNameSearch.normalize("Miejsce postoju pojazdów Uroczysko Potempowe"),
            ),
        )
        assertEquals(
            "",
            PlaceNameSearch.distinctivePart(PlaceNameSearch.normalize("Parking leśny")),
        )
    }

    @Test
    fun generic_parking_ranks_after_named() {
        val named = site("named", "Parking leśny Nad Zalewem", 52.0, 22.0, layer = 17)
        val generic = site("gen", "Parking leśny", 52.0, 21.0, layer = 17)
        val hits = PlaceNameSearch.search(
            "parking",
            listOf(generic, named),
            originLat = 52.0,
            originLon = 21.0,
        )
        assertEquals(listOf("named", "gen"), hits.map { it.siteId })
    }

    @Test
    fun nearer_generic_wins_among_generics() {
        val near = site("near", "Parking leśny", 52.0, 21.0, layer = 17)
        val far = site("far", "Parking leśny", 53.0, 21.0, layer = 17)
        val hits = PlaceNameSearch.search(
            "parking",
            listOf(far, near),
            originLat = 52.0,
            originLon = 21.0,
        )
        assertEquals("near", hits.first().siteId)
        assertTrue(hits.first().distanceKm != null && hits.first().distanceKm!! < 2.0)
    }

    @Test
    fun edit_distance_helper() {
        assertTrue(PlaceNameSearch.editDistanceAtMost("potempowe", "potempwe", 1))
        assertTrue(PlaceNameSearch.editDistanceAtMost("krywald", "krywaldx", 2))
        assertTrue(!PlaceNameSearch.editDistanceAtMost("las", "lasxx", 1))
    }

    @Test
    fun catalog_loads_named_layers() {
        val dir = createTempDir(prefix = "navilas-place-name")
        val store = BdlOfflineStore(File(dir, "bdl_offline"))
        store.openLayerWriter(15).use { writer ->
            writer.appendPage(listOf(feature(1, "Altana pod dębem", 52.2, 21.1)))
        }
        store.openLayerWriter(17).use { writer ->
            writer.appendPage(listOf(feature(2, "Parking leśny", 52.3, 21.2)))
        }
        store.writeManifest(
            OfflineBdlConfig(BdlDataScope.NAVILAS_CORE, ZanocujPolygonQuality.SIMPLIFIED),
            listOf(15, 17),
            downloadedAt = 1L,
        )
        val sites = BdlPlaceNameCatalog.load(store)
        assertEquals(2, sites.size)
        val hits = PlaceNameSearch.search("debem", sites)
        assertEquals(1, hits.size)
        assertEquals("Altana pod dębem", hits[0].name)
        assertEquals(RestSiteRepository.LAYER_REST, hits[0].sourceLayerId)
    }

    private fun feature(objectId: Int, name: String, lat: Double, lon: Double): JSONObject =
        JSONObject(
            """
            {
              "attributes": {"objectid": $objectId, "nzw_ob": "$name"},
              "geometry": {"x": $lon, "y": $lat}
            }
            """.trimIndent(),
        )

    private fun site(
        id: String,
        name: String,
        lat: Double,
        lon: Double,
        layer: Int = RestSiteRepository.LAYER_REST,
    ) = RestSite(
        id = id,
        name = name,
        latitude = lat,
        longitude = lon,
        description = null,
        sourceLayerId = layer,
        sourceLayerName = "test",
        features = emptySet<SiteFeature>(),
        relatedObjects = emptyList(),
        zanocujStatus = ZanocujStatus.OUTSIDE_ZONE,
    )
}
