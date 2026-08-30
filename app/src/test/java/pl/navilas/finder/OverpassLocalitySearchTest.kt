package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.osm.GeocodedPlace
import pl.navilas.finder.data.osm.NominatimGeocoder
import pl.navilas.finder.data.osm.OverpassLocalitySearch

class OverpassLocalitySearchTest {
    @Test
    fun overpass_parses_settlement_nodes_with_county() {
        val json = """
            {
              "elements": [
                {
                  "type": "node",
                  "lat": 52.10345,
                  "lon": 18.47406,
                  "tags": {
                    "name": "Władysławów",
                    "place": "village",
                    "is_in:county": "powiat turecki",
                    "is_in:province": "województwo wielkopolskie"
                  }
                },
                {
                  "type": "node",
                  "lat": 51.04,
                  "lon": 19.57,
                  "tags": {
                    "name": "Dmenin",
                    "place": "village",
                    "is_in:county": "powiat radomszczański"
                  }
                }
              ]
            }
        """.trimIndent()
        val places = OverpassLocalitySearch.parseResponse(json, "wladyslawow")
        assertEquals(1, places.size)
        assertTrue(places[0].displayName.contains("powiat turecki"))
    }

    @Test
    fun overpass_wikipedia_county_fallback() {
        val county = OverpassLocalitySearch.countyFromWikipedia("pl:Władysławów (powiat turecki)")
        assertEquals("powiat turecki", county)
    }

    @Test
    fun merge_prefers_overpass_and_nominatim_without_duplicates() {
        val overpass = listOf(
            GeocodedPlace(52.10, 18.47, "Władysławów, powiat turecki"),
            GeocodedPlace(51.97, 19.49, "Władysławów, powiat zgierski"),
        )
        val nominatim = listOf(
            GeocodedPlace(52.103, 18.474, "Władysławów, powiat turecki"),
            GeocodedPlace(50.75, 19.27, "Olsztyn, powiat częstochowski"),
        )
        val merged = NominatimGeocoder.mergeLocalityResults(overpass, nominatim)
        assertEquals(3, merged.size)
    }

    @Test
    fun canonical_name_from_nominatim_enables_ascii_overpass_query() {
        val nominatim = listOf(
            GeocodedPlace(52.10, 18.47, "Władysławów, powiat turecki"),
        )
        val canonical = NominatimGeocoder.resolveCanonicalSettlementName(
            query = "wladyslawow",
            nominatim = nominatim,
            normalizedQuery = "wladyslawow",
        )
        assertEquals("Władysławów", canonical)
    }
}
