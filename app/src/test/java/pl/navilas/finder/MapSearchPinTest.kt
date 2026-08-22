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
