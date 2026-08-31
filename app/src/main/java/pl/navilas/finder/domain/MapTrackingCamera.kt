package pl.navilas.finder.domain

/**
 * Camera rules for the map tracking FAB.
 * Pause must not rewrite the follow frame; play from pause reuses it.
 */
object MapTrackingCamera {
    /** First start from [MapTrackingMode.OFF] bumps a very zoomed-out map into a driving scale. */
    const val START_MIN_ZOOM = 13.0

    fun shouldCaptureLiveCamera(mode: MapTrackingMode): Boolean =
        mode == MapTrackingMode.OFF

    fun zoomForStart(currentZoom: Double): Double =
        currentZoom.coerceAtLeast(START_MIN_ZOOM)
}
