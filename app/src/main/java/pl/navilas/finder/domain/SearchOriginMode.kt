package pl.navilas.finder.domain

enum class SearchOriginMode {
    GPS,
    MAP,
    LOCALITY,
    /** Search along a polyline drawn on the map (asymmetric corridor). */
    LINE,
    /**
     * Search along a route imported from an external planner (GPX from OsmAnd).
     * Geometry is read-only; only waypoints are edited in NaviLas.
     */
    ROUTE,
}
