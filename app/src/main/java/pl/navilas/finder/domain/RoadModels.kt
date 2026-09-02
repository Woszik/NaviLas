package pl.navilas.finder.domain

/**
 * Minimal OSM road model for motorcycle proximity analysis (Checkpoint 2).
 * Field names follow common OSM tag keys where practical.
 */
data class Road(
    /** OSM way id, e.g. `way/27548154`. */
    val id: String,
    /** OSM `highway=*` value. */
    val type: String,
    /** OSM `access`, if present. */
    val access: String?,
    /** OSM `motor_vehicle`, if present. */
    val motorVehicle: String?,
    /** OSM `motorcycle`, if present. */
    val motorcycle: String?,
    /** OSM `vehicle`, if present. */
    val vehicle: String?,
    /** OSM `surface`, if present. */
    val surface: String? = null,
    /** OSM `tracktype` (grade1–5), if present. */
    val tracktype: String? = null,
    val name: String?,
    /** OSM `operator`, if present (ownership — not a legal-access tag). */
    val operator: String? = null,
    /** Nearest point on the way to the evaluated POI (WGS84), if computed. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geometry: List<LatLon> = emptyList(),
)

enum class RoadAccessClass {
    MOTO_ALLOWED,
    MOTO_RESTRICTED,
    MOTO_UNKNOWN,
    NOT_ROAD,
}

/**
 * Configurable distance bands for motorcycle road suitability.
 * Thresholds are provisional (Checkpoint 2).
 */
data class RoadSuitabilityThresholds(
    val excellentMaxMeters: Double = 50.0,
    val goodMaxMeters: Double = 150.0,
    val weakMaxMeters: Double = 300.0,
) {
    companion object {
        val DEFAULT = RoadSuitabilityThresholds()
    }
}

enum class RoadSuitability {
    EXCELLENT,
    GOOD,
    WEAK,
    REJECTED,
}

/**
 * Road proximity assessment for a single **point** BDL POI.
 * AREA geometries (Zanocuj) leave this null — centroid must not drive road ranking.
 */
data class RoadAssessment(
    val nearestRoad: Road?,
    val distanceToRoadMeters: Double?,
    val accessClass: RoadAccessClass?,
    val roadSuitability: RoadSuitability?,
    val skippedReason: String? = null,
)

fun RoadSuitability.toStars(): String = when (this) {
    RoadSuitability.EXCELLENT -> "★★★★★"
    RoadSuitability.GOOD -> "★★★★☆"
    RoadSuitability.WEAK -> "★★★☆☆"
    RoadSuitability.REJECTED -> "★☆☆☆☆"
}

fun RoadAccessClass.toPolishHint(): String = when (this) {
    RoadAccessClass.MOTO_ALLOWED -> "dozwolona dla moto"
    RoadAccessClass.MOTO_RESTRICTED -> "ograniczona dla moto"
    RoadAccessClass.MOTO_UNKNOWN -> "niepewny dostęp"
    RoadAccessClass.NOT_ROAD -> "nie droga dla moto"
}
