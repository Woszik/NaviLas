package pl.navilas.finder.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.navilas.finder.update.UpdateTrack

class UiPreferencesTest {
    @Test
    fun invalid_or_missing_values_fall_back_to_safe_defaults() {
        assertEquals(AppThemeMode.SYSTEM, parseThemeMode(null))
        assertEquals(AppThemeMode.SYSTEM, parseThemeMode("INVALID"))
        assertEquals(StartupMode.REMEMBER_LAST, parseStartupMode(null))
        assertEquals(StartupMode.REMEMBER_LAST, parseStartupMode("INVALID"))
        assertEquals(UpdateChannelPreference.BETA, parseUpdateChannelPreference(null))
        assertEquals(UpdateChannelPreference.BETA, parseUpdateChannelPreference("INVALID"))
        assertEquals(NightMapStyle.LIGHT, parseNightMapStyle(null))
        assertEquals(NightMapStyle.LIGHT, parseNightMapStyle("INVALID"))
    }

    @Test
    fun persisted_enum_values_are_restored() {
        assertEquals(AppThemeMode.NIGHT, parseThemeMode("NIGHT"))
        assertEquals(AppThemeMode.AMBIENT_LIGHT, parseThemeMode("AMBIENT_LIGHT"))
        assertEquals(StartupMode.MAP_BROWSE, parseStartupMode("MAP_BROWSE"))
        assertEquals(UpdateChannelPreference.NIGHTLY, parseUpdateChannelPreference("NIGHTLY"))
        assertEquals(NightMapStyle.DARK, parseNightMapStyle("DARK"))
        assertEquals(NightMapStyle.LIGHT, parseNightMapStyle("LIGHT"))
    }

    @Test
    fun nightly_preference_includes_beta_and_final() {
        assertEquals(
            listOf(UpdateTrack.NIGHTLY, UpdateTrack.BETA, UpdateTrack.FINAL),
            UpdateChannelPreference.NIGHTLY.tracks(),
        )
        assertEquals(
            listOf(UpdateTrack.BETA, UpdateTrack.FINAL),
            UpdateChannelPreference.BETA.tracks(),
        )
        assertEquals(listOf(UpdateTrack.FINAL), UpdateChannelPreference.FINAL.tracks())
    }
}
