package pl.navilas.finder.domain

/** How the user explores rest sites — shared offline BDL, different presentation. */
enum class AppExploreMode {
    /** Classic radius / map pin / locality / corridor search. */
    SEARCH,
    /** All offline points on the map; filters only show/hide. */
    MAP_BROWSE,
}
