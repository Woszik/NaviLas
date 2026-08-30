package pl.navilas.finder.domain

/**
 * Extra BDL objects shown in map-browse overlay — not rest-site search results.
 * Groups hide MapServer layer IDs from the UI.
 */
enum class BdlOverlayGroup(
    val labelPl: String,
    val colorHex: String,
) {
    VIEW("Widok", "#1565C0"),
    OTHER("Inne / edukacja", "#6D4C41"),
    WATER("Woda", "#00838F"),
    PLAY("Zabawa / rekreacja", "#EF6C00"),
    LODGING("Nocleg leśny", "#880E4F"),
    ;

    companion object {
        val CORE_GROUPS: Set<BdlOverlayGroup> = setOf(VIEW, OTHER)
        val FULL_ONLY_GROUPS: Set<BdlOverlayGroup> = setOf(WATER, PLAY, LODGING)
    }
}

data class BdlOverlayPoint(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val layerId: Int,
    val layerName: String,
    val group: BdlOverlayGroup,
    val typeCode: String?,
    val features: Set<SiteFeature>,
    val notes: String?,
    val extraFlags: List<String>,
) {
    fun detailLines(): List<String> = buildList {
        add(group.labelPl)
        add(layerName)
        typeCode?.takeIf { it.isNotBlank() }?.let { add("Kod BDL: $it") }
        extraFlags.forEach { add(it) }
        features.forEach { add(it.labelPl) }
        notes?.takeIf { it.isNotBlank() }?.let { add(it) }
    }

    fun toRestSite(): RestSite = RestSite(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        description = detailLines().joinToString("\n"),
        sourceLayerId = layerId,
        sourceLayerName = layerName,
        features = features,
        relatedObjects = emptyList(),
        zanocujStatus = ZanocujStatus.OUTSIDE_ZONE,
        naturalSpring = if (extraFlags.any { it.startsWith("Źródło") }) {
            NaturalSpringCertainty.CERTAIN
        } else {
            null
        },
    )
}

data class BdlOverlayFilter(
    val enabled: Boolean = false,
    val groups: Set<BdlOverlayGroup> = BdlOverlayGroup.CORE_GROUPS,
) {
    val isActive: Boolean get() = enabled && groups.isNotEmpty()

    fun summaryPl(fullAvailable: Boolean): String {
        if (!enabled) return "Ukryte"
        val shown = groups.filter { fullAvailable || it in BdlOverlayGroup.CORE_GROUPS }
        if (shown.isEmpty()) return "Włączone — wybierz grupę"
        return shown.joinToString(" · ") { it.labelPl }
    }

    fun effectiveGroups(fullAvailable: Boolean): Set<BdlOverlayGroup> {
        if (!enabled) return emptySet()
        val allowed = if (fullAvailable) {
            BdlOverlayGroup.entries.toSet()
        } else {
            BdlOverlayGroup.CORE_GROUPS
        }
        return groups.intersect(allowed)
    }
}
