package pl.navilas.finder.domain

/**
 * Which BDL layers to keep on device.
 * - [NAVILAS_CORE]: layers used by NaviLas search (0, 15, 17, 19, 25, 27).
 * - [FULL_BDL]: all non-empty layers in the Czas w Las MapServer (~43k features).
 */
enum class BdlDataScope {
    NAVILAS_CORE,
    FULL_BDL,
}

/** Geometry fidelity for Zanocuj area polygons (layer 0). */
enum class ZanocujPolygonQuality {
    /** Full WGS84 rings — best boundary accuracy (~34 MB for layer 0). */
    PRECISE,
    /** Server-side generalization — smaller download, edges may be approximate. */
    SIMPLIFIED,
}

enum class OfflineBdlStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    ERROR,
}

data class OfflineBdlConfig(
    val scope: BdlDataScope,
    val zanocujQuality: ZanocujPolygonQuality,
) {
    fun matches(other: OfflineBdlConfig?): Boolean =
        other != null && scope == other.scope && zanocujQuality == other.zanocujQuality
}

data class OfflineBdlState(
    val status: OfflineBdlStatus = OfflineBdlStatus.NOT_DOWNLOADED,
    /** Currently stored on disk (null when not downloaded). */
    val storedConfig: OfflineBdlConfig? = null,
    /** User's pending selection on the search screen (may differ before download). */
    val pendingConfig: OfflineBdlConfig = OfflineBdlConfig(
        scope = BdlDataScope.NAVILAS_CORE,
        zanocujQuality = ZanocujPolygonQuality.SIMPLIFIED,
    ),
    val progress: Float = 0f,
    val progressLabel: String? = null,
    val storageBytes: Long = 0L,
    val downloadedAt: Long? = null,
    val errorMessage: String? = null,
) {
    val isReady: Boolean = status == OfflineBdlStatus.READY && storedConfig != null

    fun estimatedSizeLabel(): String = pendingConfig.estimatedSizeLabel()
}

fun OfflineBdlConfig.estimatedSizeLabel(): String = when {
    scope == BdlDataScope.NAVILAS_CORE && zanocujQuality == ZanocujPolygonQuality.PRECISE ->
        "~55–60 MB"
    scope == BdlDataScope.NAVILAS_CORE && zanocujQuality == ZanocujPolygonQuality.SIMPLIFIED ->
        "~15–25 MB"
    scope == BdlDataScope.FULL_BDL && zanocujQuality == ZanocujPolygonQuality.PRECISE ->
        "~80–150 MB"
    else ->
        "~50–90 MB"
}
