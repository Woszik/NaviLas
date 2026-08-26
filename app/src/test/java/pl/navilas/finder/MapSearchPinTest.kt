package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.osm.NominatimGeocoder
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.SearchOriginMode
import pl.navilas.finder.ui.UiState
import pl.navilas.finder.ui.UserPosition

class MapSearchPinTest {
    @Test
    fun map_mode_uses_pin_not_gps() {
        val state = UiState(
            userPosition = UserPosition(52.0, 21.0, false),
            mapSearchPin = LatLon(50.63, 18.78),
            searchOriginMode = SearchOriginMode.MAP,
        )
        assertTrue(state.usesMapPinForSearch())
        val origin = state.searchOrigin()!!
        assertEquals(50.63, origin.latitude, 1e-9)
        assertEquals(18.78, origin.longitude, 1e-9)
    }

    @Test
    fun gps_mode_ignores_map_pin() {
        val state = UiState(
            userPosition = UserPosition(52.1, 21.1, true),
            mapSearchPin = LatLon(50.0, 18.0),
            searchOriginMode = SearchOriginMode.GPS,
        )
        assertFalse(state.usesMapPinForSearch())
        val origin = state.searchOrigin()!!
        assertEquals(52.1, origin.latitude, 1e-9)
    }

    @Test
    fun locality_mode_uses_geocoded_pin() {
        val state = UiState(
            searchOriginMode = SearchOriginMode.LOCALITY,
            mapSearchPin = LatLon(50.06, 19.94),
            localityQuery = "Kraków",
        )
        assertTrue(state.usesLocalityForSearch())
        val origin = state.searchOrigin()!!
        assertEquals(50.06, origin.latitude, 1e-9)
    }

    @Test
    fun search_origin_null_without_gps_or_pin_in_active_mode() {
        assertNull(UiState().searchOrigin())
        assertNull(
            UiState(
                searchOriginMode = SearchOriginMode.MAP,
                mapSearchPin = null,
            ).searchOrigin(),
        )
    }

    @Test
    fun nominatim_parses_multiple_results() {
        val json = """
            [
              {"lat":"53.77","lon":"20.49","display_name":"Olsztyn, warmińsko-mazurskie, Polska",
               "address":{"city":"Olsztyn","state":"województwo warmińsko-mazurskie"}},
              {"lat":"50.75","lon":"19.27","display_name":"Olsztyn, śląskie, Polska",
               "address":{"town":"Olsztyn","county":"powiat częstochowski","state":"województwo śląskie"}}
            ]
        """.trimIndent()
        val places = NominatimGeocoder.parseResults(json)
        assertEquals(2, places.size)
        assertEquals("Olsztyn, województwo warmińsko-mazurskie", places[0].displayName)
        assertTrue(places[1].displayName.contains("częstochowski"))
    }

    @Test
    fun nominatim_dedupes_nearby_admin_duplicates() {
        val json = """
            [
              {"lat":"50.3470","lon":"18.9232","display_name":"Bytom A",
               "address":{"city":"Bytom","state":"województwo śląskie"}},
              {"lat":"50.3653","lon":"18.8723","display_name":"Bytom B",
               "address":{"city":"Bytom","state":"województwo śląskie"}},
              {"lat":"53.7767","lon":"20.4765","display_name":"Olsztyn city",
               "address":{"city":"Olsztyn","state":"województwo warmińsko-mazurskie"}},
              {"lat":"53.7766","lon":"20.4778","display_name":"Olsztyn county",
               "address":{"county":"powiat olsztyński","state":"województwo warmińsko-mazurskie"}}
            ]
        """.trimIndent()
        val places = NominatimGeocoder.parseResults(json)
        assertEquals(2, places.size)
        assertTrue(places[0].displayName.startsWith("Bytom"))
        assertTrue(places[1].displayName.startsWith("Olsztyn"))
    }

    @Test
    fun nominatim_parses_first_result() {
        val json = """
            [
              {"lat":"50.0619474","lon":"19.9368564","display_name":"Kraków, Polska"}
            ]
        """.trimIndent()
        val place = NominatimGeocoder.parseFirstResult(json)!!
        assertEquals(50.0619474, place.latitude, 1e-7)
        assertEquals(19.9368564, place.longitude, 1e-7)
        assertTrue(place.displayName.contains("Kraków"))
    }
}
