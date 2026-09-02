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
            if (requireZanocujInZone) add("Zanocuj")
            if (excludeSitesInEntryBan) add("Poza zakazem wstępu")
        }
        return parts.joinToString(" · ").ifBlank { SUMMARY_ALL }
    }

    companion object {
        /** First-run place filter: hide sites inside a BDL forest-entry ban. */
        val DEFAULT = BrowseCarFilter(excludeSitesInEntryBan = true)

        const val SUMMARY_ALL = "Wszystkie miejsca"
        /** Radius for wiata / ławostoły / palenisko / „przy punkcie” parking. */
        const val AMENITY_LINK_METERS = 200.0
        const val DEFAULT_PARKING_MAX_METERS = 500
        const val MAX_PARKING_METERS = 9999
    }
}

enum class BrowseParkingProximityMode {
    /** Parking / postój within [BrowseCarFilter.AMENITY_LINK_METERS]. */
    NEAR_POINT,
    /** Walk-from-parking: custom radius up to 4 digits. */
    MAX_DISTANCE,
}
