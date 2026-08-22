package pl.navilas.finder.ui

/**
 * Explicit one-shot map camera requests — applied by Activity, then consumed.
 * Never driven by generic "any UI state changed" observers.
 */
sealed class MapCameraRequest {
    data class ShowAllResults(val token: Long) : MapCameraRequest()
    data class ShowPoi(val siteId: String, val token: Long) : MapCameraRequest()
}

object AppPages {
    const val SEARCH = 0
    const val MAP = 1
    const val LIST = 2
    const val COUNT = 3
}

/** Pure pager / selection rules for unit tests (no Android framework). */
object PagerNavigation {
    fun pageAfterSwipeLeft(current: Int): Int =
        if (current < AppPages.LIST) current + 1 else current

    fun pageAfterSwipeRight(current: Int): Int =
        if (current > AppPages.SEARCH) current - 1 else current

    fun pageAfterBack(current: Int): Int? = when (current) {
        AppPages.LIST -> AppPages.MAP
        AppPages.MAP -> AppPages.SEARCH
        else -> null // SEARCH → system back
    }

    fun cameraForMarkerClick(siteId: String, token: Long): MapCameraRequest =
        MapCameraRequest.ShowPoi(siteId, token)

    fun cameraForListSelect(siteId: String, token: Long): MapCameraRequest =
        MapCameraRequest.ShowPoi(siteId, token)

    fun cameraForNewSearch(token: Long): MapCameraRequest =
        MapCameraRequest.ShowAllResults(token)

    fun isFitBounds(request: MapCameraRequest?): Boolean =
        request is MapCameraRequest.ShowAllResults

    fun isShowPoi(request: MapCameraRequest?): Boolean =
        request is MapCameraRequest.ShowPoi
}
