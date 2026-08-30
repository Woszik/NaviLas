package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.navilas.finder.ui.AmbientLightThemeController
import pl.navilas.finder.ui.desiredAmbientNightMode

class AmbientLightThemeTest {
    @Test
    fun darkness_switches_day_to_night() {
        assertEquals(
            true,
            desiredAmbientNightMode(
                currentNight = false,
                lux = AmbientLightThemeController.NIGHT_THRESHOLD_LUX,
            ),
        )
    }

    @Test
    fun bright_light_switches_night_to_day() {
        assertEquals(
            false,
            desiredAmbientNightMode(
                currentNight = true,
                lux = AmbientLightThemeController.DAY_THRESHOLD_LUX,
            ),
        )
    }

    @Test
    fun hysteresis_range_keeps_current_theme() {
        assertNull(desiredAmbientNightMode(currentNight = false, lux = 80f))
        assertNull(desiredAmbientNightMode(currentNight = true, lux = 80f))
    }
}
