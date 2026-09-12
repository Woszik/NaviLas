package pl.navilas.finder.domain

/**
 * Waypoint role inside a planned route imported from a GPX file
 * (OsmAnd "Plan a route" / share track) or added by hand on the map.
 */
enum class RouteWaypointKind {
    /** Route finish — the navigation destination. At most one per plan. */
    TARGET,

    /** Intermediate stop used to correct the route in the external planner. */
    WAYPOINT,
}

data class RouteWaypoint(
    val id: String,
    val kind: RouteWaypointKind,
    val name: String,
    val position: LatLon,
) {
    val latitude: Double get() = position.latitude
    val longitude: Double get() = position.longitude
}

/**
 * Planned route: geometry from an external planner (OsmAnd) plus NaviLas waypoints.
 * [line] is read-only in NaviLas — the external app owns routing.
 */
data class RoutePlan(
    val waypoints: List<RouteWaypoint> = emptyList(),
    val line: List<LatLon> = emptyList(),
    /** Human-readable source, e.g. GPX file name. */
    val sourceName: String? = null,
) {
    /** A route is usable for corridor search only with real geometry. */
    val isUsable: Boolean get() = line.size >= 2

    val target: RouteWaypoint? get() = waypoints.lastOrNull { it.kind == RouteWaypointKind.TARGET }

    val viaPoints: List<RouteWaypoint>
        get() = waypoints.filter { it.kind == RouteWaypointKind.WAYPOINT }

    fun withWaypoints(next: List<RouteWaypoint>): RoutePlan = copy(waypoints = next)
}

/** Pending waypoint edit waiting for a map tap to supply the new position. */
sealed class RouteEditAction {
    data class Move(val waypointId: String) : RouteEditAction()
}
