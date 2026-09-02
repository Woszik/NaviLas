package pl.navilas.finder.data.osm

/**
 * Accepts OSM tags that mean a real river / stream / canal / lake / pond / reservoir.
 * Springs are excluded (separate BDL filter). Ditches and pools are excluded for precision.
 */
object OsmWaterClassifier {
    private val WATERWAY_OK = setOf("river", "stream", "canal")
    private val WATERWAY_NO = setOf(
        "ditch", "drain", "pressurised", "dam", "weir", "lock_gate", "fairway",
    )
    private val WATER_OK = setOf("lake", "pond", "reservoir", "oxbow", "lagoon", "river")
    private val WATER_NO = setOf(
        "swimming_pool", "reflecting_pool", "wastewater", "sewage",
        "basin", "moat", "fountain", "fishpond",
    )

    fun accept(
        waterway: String?,
        natural: String?,
        water: String?,
        landuse: String?,
        leisure: String?,
        intermittent: String?,
        seasonal: String?,
    ): Boolean {
        if (leisure == "swimming_pool") return false
        if (intermittent == "yes" || seasonal == "yes") return false
        if (natural == "wetland" || natural == "spring" || natural == "hot_spring") return false
        val waterTag = water?.lowercase()
        if (waterTag != null && waterTag in WATER_NO) return false
        val way = waterway?.lowercase()
        if (way != null) {
            if (way in WATERWAY_NO) return false
            if (way in WATERWAY_OK) return true
        }
        if (landuse == "reservoir") return true
        if (waterTag != null && waterTag in WATER_OK) return true
        if (natural == "water") return waterTag == null || waterTag in WATER_OK
        return false
    }
}
