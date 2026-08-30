package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.osm.GeocodedPlace
import pl.navilas.finder.data.osm.NominatimGeocoder
import pl.navilas.finder.data.osm.VoivodeshipResolver

class VoivodeshipResolverTest {
    @Test
    fun format_voivodeship_strips_prefix() {
        assertEquals("Łódzkie", VoivodeshipResolver.formatVoivodeship("województwo łódzkie"))
        assertEquals("Mazowieckie", VoivodeshipResolver.formatVoivodeship("województwo mazowieckie"))
    }

    @Test
    fun extract_county_from_display_name() {
        val county = VoivodeshipResolver.extractCountyFromDisplayName(
            "Władysławów, powiat turecki, województwo wielkopolskie",
        )
        assertEquals("powiat turecki", county)
    }

    @Test
    fun merge_enriches_voivodeship_from_nominatim_duplicate() {
        val overpass = listOf(
            GeocodedPlace(
                latitude = 52.10,
                longitude = 18.47,
                displayName = "Władysławów, powiat turecki",
                county = "powiat turecki",
            ),
        )
        val nominatim = listOf(
            GeocodedPlace(
                latitude = 52.103,
                longitude = 18.474,
                displayName = "Władysławów, powiat turecki, województwo wielkopolskie",
                voivodeship = "województwo wielkopolskie",
                county = "powiat turecki",
            ),
        )
        val merged = NominatimGeocoder.mergeLocalityResults(overpass, nominatim)
        assertEquals(1, merged.size)
        assertEquals("województwo wielkopolskie", merged[0].voivodeship)
    }

    @Test
    fun picker_row_label_omits_voivodeship_when_grouped() {
        val place = GeocodedPlace(
            latitude = 52.1,
            longitude = 18.47,
            displayName = "Władysławów, powiat turecki, województwo wielkopolskie",
            voivodeship = "województwo wielkopolskie",
            county = "powiat turecki",
        )
        assertEquals("Władysławów, powiat turecki", place.pickerRowLabel())
    }

    @Test
    fun group_label_uses_formatted_voivodeship_or_inne() {
        val withWoj = GeocodedPlace(0.0, 0.0, "X", voivodeship = "województwo łódzkie")
        val without = GeocodedPlace(0.0, 0.0, "X")
        assertEquals("Łódzkie", VoivodeshipResolver.groupLabel(withWoj))
        assertEquals(VoivodeshipResolver.UNKNOWN_GROUP, VoivodeshipResolver.groupLabel(without))
    }
}
