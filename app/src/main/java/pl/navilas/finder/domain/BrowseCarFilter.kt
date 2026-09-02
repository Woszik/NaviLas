package pl.navilas.finder.domain

/**
 * Browse + car amenity filter bar. Empty selection = no filtering (all sites).
 * Checked options are combined with AND.
 */
data class BrowseCarFilter(
    val requireLawostoly: Boolean = false,
    val requireWiata: Boolean = false,
    val requirePalenisko: Boolean = false,
    val requireWodaPitna: Boolean = false,
    /** Natural spring (certain or uncertain) within amenity link radius. */
    val requireZrodlo: Boolean = false,
    val requireParking: Boolean = false,
    val parkingMode: BrowseParkingProximityMode = BrowseParkingProximityMode.NEAR_POINT,
    /** Used when [parkingMode] is [BrowseParkingProximityMode.MAX_DISTANCE] (1–9999 m). */
    val parkingMaxMeters: Int = DEFAULT_PARKING_MAX_METERS,
    /** River / lake / pond within [waterRadiusMeters] (OSM + BDL 26 / kąpielisko / marina). */
    val requireNearWater: Boolean = false,
    val waterMode: BrowseWaterProximityMode = BrowseWaterProximityMode.NEAR_POINT,
    val waterMaxMeters: Int = DEFAULT_WATER_MAX_METERS,
    val requireZanocujInZone: Boolean = false,
    /** Hide rest sites that fall inside a BDL forest-entry ban polygon. */
    val excludeSitesInEntryBan: Boolean = false,
) {
    val isActive: Boolean
        get() = requireLawostoly ||
            requireWiata ||
            requirePalenisko ||
            requireWodaPitna ||
            requireZrodlo ||
            requireParking ||
            requireNearWater ||
            requireZanocujInZone ||
            excludeSitesInEntryBan

    fun parkingRadiusMeters(): Double {
        if (!requireParking) return 0.0
        return when (parkingMode) {
            BrowseParkingProximityMode.NEAR_POINT -> AMENITY_LINK_METERS
            BrowseParkingProximityMode.MAX_DISTANCE ->
                parkingMaxMeters.coerceIn(1, MAX_PARKING_METERS).toDouble()
        }
    }

    fun waterRadiusMeters(): Double {
        if (!requireNearWater) return 0.0
        return when (waterMode) {
            BrowseWaterProximityMode.NEAR_POINT -> WATER_NEAR_POINT_METERS
            BrowseWaterProximityMode.MAX_DISTANCE ->
                waterMaxMeters.coerceIn(1, MAX_WATER_METERS).toDouble()
        }
    }

    /** Short labels for collapsed filter summary and status line. */
    fun summaryPl(): String {
        val parts = buildList {
            if (requireLawostoly) add("Ławostoły")
            if (requireWiata) add("Wiata")
            if (requirePalenisko) add("Palenisko")
            if (requireWodaPitna) add("Woda pitna")
            if (requireZrodlo) add("Źródło")
            if (requireParking) {
                add(
                    when (parkingMode) {
                        BrowseParkingProximityMode.NEAR_POINT -> "Parking (przy punkcie)"
                        BrowseParkingProximityMode.MAX_DISTANCE ->
                            "Parking (max ${parkingMaxMeters.coerceIn(1, MAX_PARKING_METERS)} m)"
                    },
                )
            }
            if (requireNearWater) {
                add(
                    when (waterMode) {
                        BrowseWaterProximityMode.NEAR_POINT -> "Nad wodą (przy punkcie)"
                        BrowseWaterProximityMode.MAX_DISTANCE ->
                            "Nad wodą (max ${waterMaxMeters.coerceIn(1, MAX_WATER_METERS)} m)"
                    },
                )
            }
            if (requireZanocujInZone) add("Zanocuj")
            if (excludeSitesInEntryBan) add("Poza zakazem wstępu")
        }
        return parts.joinToString(" · ").ifBlank { SUMMARY_ALL }
    }

    companion object {
        const val SUMMARY_ALL = "Wszystkie miejsca"
        /** Radius for wiata / ławostoły / palenisko / „przy punkcie” parking. */
        const val AMENITY_LINK_METERS = 200.0
        const val DEFAULT_PARKING_MAX_METERS = 500
        const val MAX_PARKING_METERS = 9999
        /** „Przy punkcie” for near-water (rivers / lakes), not the 200 m amenity link. */
        const val WATER_NEAR_POINT_METERS = 250.0
        const val DEFAULT_WATER_MAX_METERS = 500
        const val MAX_WATER_METERS = 9999
    }
}

enum class BrowseParkingProximityMode {
    /** Parking / postój within [BrowseCarFilter.AMENITY_LINK_METERS]. */
    NEAR_POINT,
    /** Walk-from-parking: custom radius up to 4 digits. */
    MAX_DISTANCE,
}

enum class BrowseWaterProximityMode {
    /** Waterbody / waterway within [BrowseCarFilter.WATER_NEAR_POINT_METERS]. */
    NEAR_POINT,
    /** Custom radius up to 4 digits. */
    MAX_DISTANCE,
}
