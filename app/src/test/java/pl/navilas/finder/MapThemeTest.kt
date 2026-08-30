package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.navilas.finder.map.MapConfig

class MapThemeTest {
    @Test
    fun map_style_follows_app_theme() {
        assertEquals(MapConfig.STYLE_URL, MapConfig.styleUrl(darkMode = false))
        assertEquals(MapConfig.STYLE_URL_DARK, MapConfig.styleUrl(darkMode = true))
    }
}
