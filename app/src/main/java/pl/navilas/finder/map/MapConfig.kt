package pl.navilas.finder.map

/**
 * Separates MapLibre engine, map style, and OSM-derived tile data.
 * Full usage notes: docs/MAP_SOURCE.md
 */
object MapConfig {
    /** Rendering engine only — does not provide OSM tiles by itself. */
    const val ENGINE_NAME = "MapLibre Native Android"
    const val ENGINE_ARTIFACT = "org.maplibre.gl:android-sdk:11.8.6"

    /** MapLibre styles hosted by OpenFreeMap. */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val STYLE_URL_DARK = "https://tiles.openfreemap.org/styles/dark"
    const val STYLE_NAME = "OpenFreeMap Liberty / Dark"
    const val STYLE_PROVIDER = "OpenFreeMap"

    fun styleUrl(darkMode: Boolean): String =
        if (darkMode) STYLE_URL_DARK else STYLE_URL

    /**
     * Vector tile source referenced by the style (OpenMapTiles schema over OSM data).
     * Not loaded directly by app code — declared here for documentation / future offline work.
     */
    const val VECTOR_TILEJSON_URL = "https://tiles.openfreemap.org/planet"

    /** Raster hillshade tiles referenced by the style. */
    const val NATURAL_EARTH_TILES =
        "https://tiles.openfreemap.org/natural_earth/ne2sr/{z}/{x}/{y}.png"

    const val ATTRIBUTION =
        "OpenFreeMap © OpenMapTiles Data from OpenStreetMap"

    const val USAGE_NOTES_DOC = "docs/MAP_SOURCE.md"
}
