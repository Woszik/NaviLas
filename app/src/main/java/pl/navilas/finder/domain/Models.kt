package pl.navilas.finder.domain

enum class PoiCategory {
    PARKING,
    REST,
    FIRE,
    CAMP,
}

enum class TravelProfile {
    CAR,
    MOTORCYCLE,
    ;

    fun allowedCategories(): Set<PoiCategory> = when (this) {
        CAR -> setOf(PoiCategory.PARKING, PoiCategory.REST, PoiCategory.FIRE, PoiCategory.CAMP)
        MOTORCYCLE -> setOf(PoiCategory.REST, PoiCategory.FIRE, PoiCategory.CAMP)
    }
}

enum class PoiGeometryKind {
    /** Exact BDL point (layers 15, 17). */
    POINT,
    /**
     * BDL polygon/area (layer 0 Zanocuj w Lesie).
     * [Poi.latitude]/[Poi.longitude] are a presentation/helper centroid only —
     * not a real navigation destination.
     */
    AREA,
}

data class LatLon(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Internal domain POI. One BDL feature maps to one [Poi].
 * Multiple amenities (wiata / palenisko) become multiple [categories], never duplicated rows.
 */
data class Poi(
    val id: String,
    val categories: Set<PoiCategory>,
    val name: String,
    /**
     * Marker / helper coordinates in WGS84.
     * For [PoiGeometryKind.AREA] this is a **centroid for presentation and helper distance only**,
     * not the destination point of the camping area.
     */
    val latitude: Double,
    val longitude: Double,
    val description: String?,
    val source: String,
    val geometryKind: PoiGeometryKind = PoiGeometryKind.POINT,
    /** Outer/inner rings as lon-lat vertices; non-empty only for [PoiGeometryKind.AREA]. */
    val areaRings: List<List<LatLon>> = emptyList(),
) {
    val primaryCategory: PoiCategory
        get() = when {
            PoiCategory.CAMP in categories -> PoiCategory.CAMP
            PoiCategory.PARKING in categories -> PoiCategory.PARKING
            PoiCategory.FIRE in categories -> PoiCategory.FIRE
            else -> categories.firstOrNull() ?: PoiCategory.REST
        }

    fun matches(profile: TravelProfile): Boolean =
        categories.any { it in profile.allowedCategories() }
}

data class PoiResult(
    val poi: Poi,
    val distanceKm: Double,
    /** Motorcycle road proximity; null for CAR-only display or before analysis. */
    val roadAssessment: RoadAssessment? = null,
)

sealed class AppMessage {
    data class Info(val text: String) : AppMessage()
    data class Error(val text: String) : AppMessage()
}
