package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.domain.SiteWebSearch

class SiteWebSearchTest {
    @Test
    fun strips_bdl_prefixes_and_quotes() {
        assertEquals(
            "Uroczysko Potempowe",
            SiteWebSearch.coreName("Miejsce postoju pojazdów Uroczysko Potempowe"),
        )
        assertEquals(
            "Źródełko Krywałd",
            SiteWebSearch.coreName("Miejsce odpoczynku Źródełko Krywałd"),
        )
        assertEquals("Trzy Dęby", SiteWebSearch.coreName("Miejsce odpoczynku \"Trzy Dęby\""))
        assertEquals("Karwno", SiteWebSearch.coreName("Miejsce wypoczynku Karwno"))
        assertEquals("Grodzisko", SiteWebSearch.coreName("Parking Grodzisko"))
        assertEquals(
            "Karżcino",
            SiteWebSearch.coreName("Miejsce postoju pojazdów- Karżcino"),
        )
    }

    @Test
    fun rich_cores_search_without_prefix() {
        assertTrue(SiteWebSearch.isRichCore("Uroczysko Potempowe"))
        assertTrue(SiteWebSearch.isRichCore("Źródełko Krywałd"))
        assertTrue(SiteWebSearch.isRichCore("Kolorowe jeziorka"))
        assertTrue(
            SiteWebSearch.isRichCore(
                "Wiata przy ścieżce edukacyjnej Przez bieszczadzki las",
            ),
        )
        assertEquals(
            "Uroczysko Potempowe",
            SiteWebSearch.buildQuery("Miejsce postoju pojazdów Uroczysko Potempowe"),
        )
        assertEquals(
            "Źródełko Krywałd",
            SiteWebSearch.buildQuery("Miejsce odpoczynku Źródełko Krywałd", "Koszęcin"),
        )
    }

    @Test
    fun thin_cores_keep_full_name_and_may_add_admin() {
        assertFalse(SiteWebSearch.isRichCore("Karwno"))
        assertFalse(SiteWebSearch.isRichCore("Trzy Dęby"))
        assertFalse(SiteWebSearch.isRichCore("Grodzisko"))
        assertEquals(
            "Miejsce wypoczynku Karwno",
            SiteWebSearch.buildQuery("Miejsce wypoczynku Karwno"),
        )
        assertEquals(
            "Miejsce wypoczynku Karwno Nadleśnictwo Łupawa",
            SiteWebSearch.buildQuery("Miejsce wypoczynku Karwno", "Łupawa"),
        )
        assertEquals(
            "Miejsce odpoczynku \"Trzy Dęby\" Nadleśnictwo X",
            SiteWebSearch.buildQuery("Miejsce odpoczynku \"Trzy Dęby\"", "X"),
        )
        assertEquals(
            "przy Nadleśnictwie Gołąbki",
            SiteWebSearch.buildQuery(
                "Miejsce postoju przy Nadleśnictwie Gołąbki",
                "Gołąbki",
            ),
        )
    }

    @Test
    fun google_url_encodes_query() {
        val url = SiteWebSearch.googleSearchUrl("Źródełko Krywałd")
        assertTrue(url.startsWith("https://www.google.com/search?hl=pl&q="))
        assertTrue(url.contains("Krywa"))
    }
}
