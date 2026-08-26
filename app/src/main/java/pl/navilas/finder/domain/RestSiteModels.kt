package pl.navilas.finder.domain

/**
 * Confirmed amenity flags from BDL layer 15 (and echoed on related layers).
 * Codes match real ArcGIS field names — no invented categories.
 */
enum class SiteFeature(val bdlField: String, val labelPl: String) {
    WIATA("wiata", "Wiata"),
    PALENISKO("palenisko", "Palenisko"),
    PARKING("parking", "Parking"),
    WODA_PITNA("woda_pitna", "Woda pitna"),
    LAWOSTOLY("lawostoly", "Ławostoły"),
    KUCHENKA("kuchenka", "Kuchenka"),
    TOALETY("toalety", "Toalety"),
    LAD_ROWER("lad_rower", "Ładowanie rowerów"),
    SERW_ROWER("serw_rower", "Serwis rowerowy"),
    KAPIELISKO("kapielisko", "Kąpielisko"),
    MARINA("marina", "Marina"),
}

enum class ZanocujStatus {
    IN_ZONE,
    NEAR_ZONE,
    OUTSIDE_ZONE,
}

enum class ZanocujFilterMode {
    /** All rest sites in the search radius. */
    ALL,
    /** Only sites with [ZanocujStatus.IN_ZONE]. */
    ONLY_IN_ZONE,
}

/**
 * Primary search result: one BDL place to rest / stop with amenities.
 * Usually layer 15; may also be layer 17/19 when those carry wiata/palenisko/lawostoly
 * and are not duplicated by a nearby layer-15 site.
 * Amenities and nearby BDL objects enrich this record — they are never separate duplicate rows
 * for the same physical spot within [SearchConfig.restLinkRadiusMeters].
 */
data class RestSite(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val description: String?,
    val sourceLayerId: Int,
    val sourceLayerName: String,
    val features: Set<SiteFeature>,
    val relatedObjects: List<RelatedBdlObject>,
    val zanocujStatus: ZanocujStatus,
    /** Distance to nearest Zanocuj polygon boundary (m); set for NEAR_ZONE. */
    val distanceToZanocujBoundaryMeters: Double? = null,
)

data class RelatedBdlObject(
    val id: String,
    val layerId: Int,
    val layerName: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    /** Raw BDL type code if present, e.g. tur_rec_pnt_cd. */
    val typeCode: String?,
)

data class RestSiteResult(
    val site: RestSite,
    val distanceKm: Double,
    val roadAssessment: RoadAssessment? = null,
    val navigationTarget: LatLon,
    val navigationTargetKind: NavigationTargetKind,
)

enum class NavigationTargetKind {
    REST_SITE,
    PARKING,
    OSM_ROAD,
}

/**
 * Configurable thresholds (not hard-coded magic in call sites).
 * [searchRadiusKm] is the user-selected search radius (max [MAX_SEARCH_RADIUS_KM]).
 */
data class SearchConfig(
    val restLinkRadiusMeters: Double = 100.0,
    val zanocujNearZoneMeters: Double = 500.0,
    val searchRadiusKm: Double = DEFAULT_SEARCH_RADIUS_KM,
) {
    init {
        require(searchRadiusKm > 0.0) { "searchRadiusKm must be positive" }
        require(searchRadiusKm >= MIN_SEARCH_RADIUS_KM) {
            "searchRadiusKm must be >= $MIN_SEARCH_RADIUS_KM"
        }
        require(searchRadiusKm <= MAX_SEARCH_RADIUS_KM) {
            "searchRadiusKm must be <= $MAX_SEARCH_RADIUS_KM"
        }
    }

    companion object {
        const val MIN_SEARCH_RADIUS_KM = 1.0
        const val MAX_SEARCH_RADIUS_KM = 100.0
        const val DEFAULT_SEARCH_RADIUS_KM = 25.0

        /** Preset radii offered in the UI (km). Custom values allowed up to [MAX_SEARCH_RADIUS_KM]. */
        val SEARCH_RADIUS_PRESETS_KM: List<Double> = listOf(5.0, 10.0, 25.0, 50.0)

        val DEFAULT = SearchConfig()
    }
}
