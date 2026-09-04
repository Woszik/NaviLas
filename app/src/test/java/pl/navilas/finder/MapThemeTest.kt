package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.preferences.NightMapStyle
import pl.navilas.finder.map.MapConfig

class MapThemeTest {
    @Test
    fun map_style_urls_for_light_and_dark() {
        assertEquals(MapConfig.STYLE_URL, MapConfig.styleUrl(darkMode = false))
        assertEquals(MapConfig.STYLE_URL_DARK, MapConfig.styleUrl(darkMode = true))
    }

    @Test
    fun night_ui_defaults_to_light_map() {
        assertFalse(MapConfig.useDarkMapStyle(appNightMode = true, NightMapStyle.LIGHT))
        assertEquals(
            MapConfig.STYLE_URL,
            MapConfig.styleUrl(
                MapConfig.useDarkMapStyle(appNightMode = true, NightMapStyle.LIGHT),
            ),
        )
    }

    @Test
    fun night_ui_can_use_dark_map() {
        assertTrue(MapConfig.useDarkMapStyle(appNightMode = true, NightMapStyle.DARK))
        assertEquals(
            MapConfig.STYLE_URL_DARK,
            MapConfig.styleUrl(
                MapConfig.useDarkMapStyle(appNightMode = true, NightMapStyle.DARK),
            ),
        )
    }

    @Test
    fun day_ui_always_uses_light_map() {
        assertFalse(MapConfig.useDarkMapStyle(appNightMode = false, NightMapStyle.DARK))
        assertFalse(MapConfig.useDarkMapStyle(appNightMode = false, NightMapStyle.LIGHT))
    }
}
