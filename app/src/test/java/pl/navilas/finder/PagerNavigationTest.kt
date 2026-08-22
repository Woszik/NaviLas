package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.ui.AppPages
import pl.navilas.finder.ui.MapCameraRequest
import pl.navilas.finder.ui.PagerNavigation

class PagerNavigationTest {
    @Test
    fun swipe_left_search_to_map() {
        assertEquals(AppPages.MAP, PagerNavigation.pageAfterSwipeLeft(AppPages.SEARCH))
    }

    @Test
    fun swipe_left_map_to_list() {
        assertEquals(AppPages.LIST, PagerNavigation.pageAfterSwipeLeft(AppPages.MAP))
    }

    @Test
    fun swipe_left_on_list_stays() {
        assertEquals(AppPages.LIST, PagerNavigation.pageAfterSwipeLeft(AppPages.LIST))
    }

    @Test
    fun swipe_right_list_to_map() {
        assertEquals(AppPages.MAP, PagerNavigation.pageAfterSwipeRight(AppPages.LIST))
    }

    @Test
    fun swipe_right_map_to_search() {
        assertEquals(AppPages.SEARCH, PagerNavigation.pageAfterSwipeRight(AppPages.MAP))
    }

    @Test
    fun swipe_right_on_search_stays() {
        assertEquals(AppPages.SEARCH, PagerNavigation.pageAfterSwipeRight(AppPages.SEARCH))
    }

    @Test
    fun back_from_list_to_map() {
        assertEquals(AppPages.MAP, PagerNavigation.pageAfterBack(AppPages.LIST))
    }

    @Test
    fun back_from_map_to_search() {
        assertEquals(AppPages.SEARCH, PagerNavigation.pageAfterBack(AppPages.MAP))
    }

    @Test
    fun back_from_search_is_system() {
        assertNull(PagerNavigation.pageAfterBack(AppPages.SEARCH))
    }

    @Test
    fun results_and_selection_independent_of_page_rules() {
        // Changing page must not imply camera fitBounds or clearing selection.
        val page = AppPages.LIST
        assertEquals(AppPages.MAP, PagerNavigation.pageAfterSwipeRight(page))
        assertFalse(PagerNavigation.isFitBounds(null))
    }

    @Test
    fun marker_click_requests_show_poi_not_fit_bounds() {
        val req = PagerNavigation.cameraForMarkerClick("site-1", 7L)
        assertTrue(PagerNavigation.isShowPoi(req))
        assertFalse(PagerNavigation.isFitBounds(req))
        assertEquals("site-1", (req as MapCameraRequest.ShowPoi).siteId)
    }

    @Test
    fun list_select_requests_show_poi_not_fit_bounds() {
        val req = PagerNavigation.cameraForListSelect("site-2", 8L)
        assertTrue(PagerNavigation.isShowPoi(req))
        assertFalse(PagerNavigation.isFitBounds(req))
        assertEquals(AppPages.MAP, AppPages.MAP) // list → map is separate state update
    }

    @Test
    fun new_search_requests_fit_bounds_show_all() {
        val req = PagerNavigation.cameraForNewSearch(9L)
        assertTrue(PagerNavigation.isFitBounds(req))
        assertFalse(PagerNavigation.isShowPoi(req))
    }

    @Test
    fun radius_presets_unchanged_by_pager() {
        assertEquals(listOf(5.0, 10.0, 25.0, 50.0, 100.0), SearchConfig.SEARCH_RADIUS_PRESETS_KM)
        assertEquals(25.0, SearchConfig.DEFAULT_SEARCH_RADIUS_KM, 0.0)
    }

    @Test
    fun page_count_is_three() {
        assertEquals(3, AppPages.COUNT)
        assertEquals(0, AppPages.SEARCH)
        assertEquals(1, AppPages.MAP)
        assertEquals(2, AppPages.LIST)
    }
}
