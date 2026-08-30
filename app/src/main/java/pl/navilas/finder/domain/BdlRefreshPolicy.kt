package pl.navilas.finder.domain

internal const val BDL_REFRESH_STALE_MS = 30L * 24 * 60 * 60 * 1000
internal const val BDL_REFRESH_SNOOZE_MS = 7L * 24 * 60 * 60 * 1000

data class BdlRefreshOffer(
    val downloadedAt: Long,
    val config: OfflineBdlConfig,
)

internal fun shouldOfferBdlRefresh(
    isReady: Boolean,
    downloadedAt: Long?,
    nowMs: Long,
    snoozeUntilMs: Long,
    staleAfterMs: Long = BDL_REFRESH_STALE_MS,
): Boolean {
    if (!isReady) return false
    val downloaded = downloadedAt ?: return false
    if (downloaded <= 0L) return false
    if (nowMs - downloaded < staleAfterMs) return false
    if (snoozeUntilMs > nowMs) return false
    return true
}
