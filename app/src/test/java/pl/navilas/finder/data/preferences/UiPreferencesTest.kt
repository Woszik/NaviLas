package pl.navilas.finder.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesTest {
    @Test
    fun invalid_or_missing_values_fall_back_to_safe_defaults() {
        assertEquals(AppThemeMode.SYSTEM, parseThemeMode(null))
        assertEquals(AppThemeMode.SYSTEM, parseThemeMode("INVALID"))
        assertEquals(StartupMode.REMEMBER_LAST, parseStartupMode(null))
        assertEquals(StartupMode.REMEMBER_LAST, parseStartupMode("INVALID"))
    }

    @Test
    fun persisted_enum_values_are_restored() {
        assertEquals(AppThemeMode.NIGHT, parseThemeMode("NIGHT"))
        assertEquals(AppThemeMode.AMBIENT_LIGHT, parseThemeMode("AMBIENT_LIGHT"))
        assertEquals(StartupMode.MAP_BROWSE, parseStartupMode("MAP_BROWSE"))
    }
}
