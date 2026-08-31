package pl.navilas.finder.domain

/** Official BDL periodic forest-entry ban (Mapa zakazów wstępu), not Czas w Las. */
enum class ForestEntryBanReason(val labelPl: String) {
    OTHER("inne przyczyny"),
    PESTICIDE("zabiegi ochrony roślin"),
    FIRE("zagrożenie pożarowe"),
}

data class ForestEntryBan(
    val id: String,
    val reason: ForestEntryBanReason,
    val forestDistrict: String?,
    val forestry: String?,
    val compartment: String?,
    val validFrom: String?,
    val validUntil: String?,
    val rings: List<List<LatLon>>,
) {
    fun summaryPl(): String = buildList {
        add(reason.labelPl.replaceFirstChar { it.uppercaseChar() })
        forestDistrict?.let { add("Nadleśnictwo $it") }
        validUntil?.let { add("do $it") }
    }.joinToString(" · ")

    fun detailLines(): List<String> = buildList {
        add(reason.labelPl.replaceFirstChar { it.uppercaseChar() })
        forestDistrict?.let { add("Nadleśnictwo $it") }
        forestry?.let { add("Leśnictwo $it") }
        compartment?.let { add("Oddział $it") }
        validFrom?.let { add("Od $it") }
        validUntil?.let { add("Do $it") }
    }
}
