package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.navilas.finder.data.preferences.StartupMode
import pl.navilas.finder.domain.AppExploreMode
import pl.navilas.finder.ui.AppPages
import pl.navilas.finder.ui.UiState
import pl.navilas.finder.ui.exploreModeTargetPage
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

    @Test
    fun explore_mode_from_search_page_jumps_to_target_screen() {
        assertEquals(
            AppPages.SEARCH,
            exploreModeTargetPage(AppExploreMode.SEARCH, stayOnPage = false, currentPage = AppPages.MAP),
        )
        assertEquals(
            AppPages.MAP,
            exploreModeTargetPage(AppExploreMode.MAP_BROWSE, stayOnPage = false, currentPage = AppPages.SEARCH),
        )
    }

    @Test
    fun explore_mode_from_map_filters_stays_on_current_page() {
        assertEquals(
            AppPages.MAP,
            exploreModeTargetPage(AppExploreMode.SEARCH, stayOnPage = true, currentPage = AppPages.MAP),
        )
        assertEquals(
            AppPages.MAP,
            exploreModeTargetPage(AppExploreMode.MAP_BROWSE, stayOnPage = true, currentPage = AppPages.MAP),
        )
        assertEquals(
            AppPages.SEARCH,
            exploreModeTargetPage(AppExploreMode.MAP_BROWSE, stayOnPage = true, currentPage = AppPages.SEARCH),
        )
    }
}
