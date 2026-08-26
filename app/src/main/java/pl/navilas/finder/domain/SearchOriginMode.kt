package pl.navilas.finder.domain

enum class SearchOriginMode {
    GPS,
    MAP,
    LOCALITY,
    /** Search along a polyline drawn on the map (asymmetric corridor). */
    LINE,
}
