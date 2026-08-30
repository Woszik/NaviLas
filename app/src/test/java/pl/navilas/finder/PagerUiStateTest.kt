package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.RestSiteResult
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.ui.AppPages
import pl.navilas.finder.ui.MapCameraRequest
import pl.navilas.finder.ui.PagerNavigation
import pl.navilas.finder.ui.UiState

/**
 * State-machine style checks for Checkpoint 3A without Android instrumentation.
 */
class PagerUiStateTest {
    private fun sampleResult(id: String = "a") = RestSiteResult(
        site = RestSite(
            id = id,
            name = "Test",
            latitude = 52.2,
            longitude = 21.1,
            description = null,
            sourceLayerId = 15,
            sourceLayerName = "rest",
            features = setOf(SiteFeature.WIATA),
            relatedObjects = emptyList(),
            zanocujStatus = ZanocujStatus.IN_ZONE,
        ),
        distanceKm = 3.0,
        navigationTarget = LatLon(52.2, 21.1),
        navigationTargetKind = NavigationTargetKind.REST_SITE,
    )

    @Test
    fun page_change_keeps_results_and_selection() {
        val results = listOf(sampleResult("x"), sampleResult("y"))
        var state = UiState(
            results = results,
            selectedSiteIds = listOf("x"),
            currentPage = AppPages.MAP,
            searchConfig = SearchConfig.DEFAULT,
        )
        state = state.copy(currentPage = AppPages.LIST)
        assertEquals(results, state.results)
        assertEquals("x", state.selectedSiteId)
        state = state.copy(currentPage = AppPages.SEARCH)
        assertEquals(results, state.results)
        assertEquals("x", state.selectedSiteId)
    }

    @Test
    fun selection_change_keeps_results_and_page() {
        val results = listOf(sampleResult("x"), sampleResult("y"))
        var state = UiState(results = results, selectedSiteIds = listOf("x"), currentPage = AppPages.MAP)
        state = state.copy(selectedSiteIds = listOf("y"))
        assertEquals(results, state.results)
        assertEquals(AppPages.MAP, state.currentPage)
    }

    @Test
    fun new_search_replaces_results_and_clears_selection() {
        val old = listOf(sampleResult("old"))
        val neu = listOf(sampleResult("new1"), sampleResult("new2"))
        var state = UiState(
            results = old,
            selectedSiteIds = listOf("old"),
            currentPage = AppPages.LIST,
        )
        val token = 42L
        state = state.copy(
            results = neu,
            selectedSiteIds = emptyList(),
            currentPage = AppPages.MAP,
            mapCameraRequest = PagerNavigation.cameraForNewSearch(token),
        )
        assertEquals(neu, state.results)
        assertNull(state.selectedSiteId)
        assertEquals(AppPages.MAP, state.currentPage)
        assertTrue(PagerNavigation.isFitBounds(state.mapCameraRequest))
    }

    @Test
    fun radius_param_change_without_search_keeps_results() {
        val results = listOf(sampleResult())
        var state = UiState(
            results = results,
            selectedSiteIds = listOf("a"),
            searchConfig = SearchConfig.DEFAULT.copy(searchRadiusKm = 25.0),
        )
        state = state.copy(
            searchConfig = state.searchConfig.copy(searchRadiusKm = 50.0),
        )
        assertEquals(50.0, state.searchConfig.searchRadiusKm, 0.0)
        assertEquals(results, state.results)
        assertEquals("a", state.selectedSiteId)
    }

    @Test
    fun marker_sets_selection_and_poi_camera() {
        val req = PagerNavigation.cameraForMarkerClick("m1", 1L)
        val state = UiState(
            results = listOf(sampleResult("m1")),
            selectedSiteIds = listOf("m1"),
            currentPage = AppPages.MAP,
            mapCameraRequest = req,
        )
        assertEquals("m1", state.selectedSiteId)
        assertTrue(PagerNavigation.isShowPoi(state.mapCameraRequest))
        assertFalse(PagerNavigation.isFitBounds(state.mapCameraRequest))
    }

    @Test
    fun list_select_goes_to_map_with_poi_camera() {
        val req = PagerNavigation.cameraForListSelect("L1", 2L)
        val state = UiState(
            results = listOf(sampleResult("L1")),
            selectedSiteIds = listOf("L1"),
            currentPage = AppPages.MAP,
            mapCameraRequest = req,
        )
        assertEquals(AppPages.MAP, state.currentPage)
        assertTrue(state.mapCameraRequest is MapCameraRequest.ShowPoi)
        assertFalse(PagerNavigation.isFitBounds(state.mapCameraRequest))
    }

    @Test
    fun multi_select_keeps_last_as_primary() {
        val state = UiState(selectedSiteIds = listOf("a", "b", "c"))
        assertEquals("c", state.selectedSiteId)
        assertEquals(3, state.selectedSiteIds.size)
    }
}
