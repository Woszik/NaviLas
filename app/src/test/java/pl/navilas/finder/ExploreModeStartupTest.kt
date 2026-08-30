package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.navilas.finder.data.preferences.StartupMode
import pl.navilas.finder.domain.AppExploreMode
import pl.navilas.finder.ui.AppPages
import pl.navilas.finder.ui.UiState
import pl.navilas.finder.ui.savedExploreMode
import pl.navilas.finder.ui.startupExploreMode

class ExploreModeStartupTest {
    @Test
    fun first_start_defaults_to_search() {
        assertEquals(AppExploreMode.SEARCH, savedExploreMode(null))
        assertEquals(AppExploreMode.SEARCH, UiState().exploreMode)
        assertEquals(AppPages.SEARCH, UiState().currentPage)
    }

    @Test
    fun saved_mode_is_restored_and_invalid_value_falls_back_to_search() {
        assertEquals(AppExploreMode.MAP_BROWSE, savedExploreMode("MAP_BROWSE"))
        assertEquals(AppExploreMode.SEARCH, savedExploreMode("invalid"))
    }

    @Test
    fun startup_preference_can_override_or_restore_last_mode() {
        assertEquals(
            AppExploreMode.MAP_BROWSE,
            startupExploreMode(StartupMode.REMEMBER_LAST, "MAP_BROWSE"),
        )
        assertEquals(
            AppExploreMode.SEARCH,
            startupExploreMode(StartupMode.SEARCH, "MAP_BROWSE"),
        )
        assertEquals(
            AppExploreMode.MAP_BROWSE,
            startupExploreMode(StartupMode.MAP_BROWSE, "SEARCH"),
        )
    }
}
